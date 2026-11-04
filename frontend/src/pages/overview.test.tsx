import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { setLanguage } from '../i18n'
import { renderApp, stubApi } from '../test/renderApp'

type Replies = Parameters<typeof stubApi>[0]

/** jsdom has no layout: the chart measures itself through ResizeObserver. */
class FixedSizeObserver {
  private readonly callback: ResizeObserverCallback
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
  }
  observe(target: Element) {
    const entry = { target, contentRect: { width: 640, height: 280 } } as unknown as ResizeObserverEntry
    this.callback([entry], this as unknown as ResizeObserver)
  }
  unobserve() {}
  disconnect() {}
}

const months = (year: number, changes: Record<number, [number, number, number]> = {}) =>
  Array.from({ length: 12 }, (_, i) => {
    const [income, expenses, balance] = changes[i + 1] ?? [0, 0, 0]
    return {
      month: `${year}-${String(i + 1).padStart(2, '0')}`,
      totalIncomeCents: income,
      totalExpensesCents: expenses,
      balanceCents: balance,
    }
  })

const baseReplies = (): Replies => ({
  'GET /api/me': { body: { id: 1, name: 'Ana', email: 'a@x.com', onboardingSeen: true } },
  'GET /api/settings': { body: { currentYear: 2026, autoInstallments: true, closingsEnabled: true, closingDays: [15, 31] } },
  'GET /api/years': { body: [{ year: 2025, current: false }, { year: 2026, current: true }] },
  'GET /api/years/2026/overview': {
    body: { year: 2026, months: months(2026, { 3: [662_358, 594_288, 68_070], 4: [0, 99_399, -99_399] }) },
  },
  'GET /api/years/2025/overview': { body: { year: 2025, months: months(2025, { 1: [100_000, 1_000, 99_000] }) } },
})

const table = () => screen.findByRole('table', { name: 'Saldo de cada mês' })

describe('annual overview (REQ-039, REQ-040)', () => {
  // The chart is lazy-loaded; loading the chart library once up front keeps each test within its time limit.
  beforeAll(async () => {
    await import('../components/OverviewChart')
  }, 60_000)

  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', FixedSizeObserver)
  })

  it('shows the 12 months exactly as the API sends them', async () => {
    stubApi(baseReplies())
    renderApp('/overview/2026', { signedIn: true })

    const rows = within(await table()).getAllByRole('row')
    expect(rows).toHaveLength(13) // header + 12 months
    const march = within(rows[3])
    expect(march.getByRole('link', { name: 'Abrir março' })).toBeInTheDocument()
    expect(march.getByText('6.623,58')).toBeInTheDocument()
    expect(march.getByText('5.942,88')).toBeInTheDocument()
    expect(march.getByText('680,70')).toHaveClass('text-positive')
    expect(within(rows[4]).getByText('-993,99')).toHaveClass('text-negative')
    expect(screen.getByRole('figure', { name: 'Gastos e entradas por mês' })).toBeInTheDocument()
  })

  it('never recalculates: the balance shown is the one the API sent', async () => {
    const replies = baseReplies()
    replies['GET /api/years/2026/overview'] = { body: { year: 2026, months: months(2026, { 1: [100, 50, 12_345] }) } }
    stubApi(replies)
    renderApp('/overview/2026', { signedIn: true })

    const january = within(within(await table()).getAllByRole('row')[1])
    expect(january.getByText('123,45')).toBeInTheDocument()
  })

  it('opens the month when its row is clicked', async () => {
    stubApi(baseReplies())
    const router = renderApp('/overview/2026', { signedIn: true })

    await userEvent.click(within(await table()).getByRole('link', { name: 'Abrir março' }))
    expect(router.state.location.pathname).toBe('/month/2026-03')
  })

  it('opens the month when its bar is clicked', async () => {
    stubApi(baseReplies())
    const router = renderApp('/overview/2026', { signedIn: true })

    await table()
    const figure = screen.getByRole('figure', { name: 'Gastos e entradas por mês' })
    await waitFor(() => expect(figure.querySelectorAll('.recharts-bar-rectangle').length).toBeGreaterThan(0))
    const bars = figure.querySelectorAll('.recharts-bar-rectangle')
    await userEvent.click(bars[0])
    expect(router.state.location.pathname).toMatch(/^\/month\/2026-\d{2}$/)
  })

  it('changes the year', async () => {
    const calls = stubApi(baseReplies())
    const router = renderApp('/overview/2026', { signedIn: true })

    await table()
    await userEvent.selectOptions(screen.getByLabelText('Ano'), '2025')

    await waitFor(() => expect(router.state.location.pathname).toBe('/overview/2025'))
    const january = within(within(await table()).getAllByRole('row')[1])
    expect(await january.findByText('990,00')).toBeInTheDocument()
    expect(calls.some((c) => c.key === 'GET /api/years/2025/overview')).toBe(true)
  })

  it('says so when the year does not exist in the account', async () => {
    const replies = baseReplies()
    replies['GET /api/years/2031/overview'] = { status: 404, body: { code: 'NOT_FOUND', fields: {} } }
    stubApi(replies)
    renderApp('/overview/2031', { signedIn: true })

    expect(await screen.findByText('Esse ano não existe na sua conta. Escolha outro ano.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('does not call the API for a malformed year', async () => {
    const calls = stubApi(baseReplies())
    renderApp('/overview/abc', { signedIn: true })

    expect(await screen.findByText('Esse ano não existe na sua conta. Escolha outro ano.')).toBeInTheDocument()
    expect(calls.some((c) => c.key.includes('/overview'))).toBe(false)
  })

  it('shows other API errors translated', async () => {
    const replies = baseReplies()
    replies['GET /api/years/2026/overview'] = { status: 500, body: { code: 'INTERNAL_ERROR', fields: {} } }
    stubApi(replies)
    renderApp('/overview/2026', { signedIn: true })

    expect(await screen.findByText('Algo deu errado. Tente de novo.')).toBeInTheDocument()
  })

  it('follows the interface language', async () => {
    setLanguage('en')
    stubApi(baseReplies())
    renderApp('/overview/2026', { signedIn: true })

    const rows = within(await screen.findByRole('table', { name: 'Balance of each month' })).getAllByRole('row')
    expect(within(rows[3]).getByRole('link', { name: 'Open March' })).toBeInTheDocument()
    expect(within(rows[3]).getByText('6,623.58')).toBeInTheDocument()
  })
})
