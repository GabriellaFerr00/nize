import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { MonthView } from '../api/monthApi'
import { LIMIT_NOTICE_MS } from '../components/LimitToast'
import { renderApp, stubApi } from '../test/renderApp'

type Replies = Parameters<typeof stubApi>[0]
type Calls = ReturnType<typeof stubApi>

const NOV = 'GET /api/months/2026-11'

/** A month as the API computes it (numbers of the PDF examples); the front must show them as they are. */
const november = (): MonthView => ({
  month: '2026-11',
  totals: {
    totalExpensesCents: 594_288,
    totalFixedCents: 99_399,
    totalIncomeCents: 662_358,
    balanceCents: 68_070,
    thirdPartyExpensesCents: 20_000,
    reimbursementsCents: 19_000,
    ownBalanceCents: 69_070,
  },
  closingsEnabled: true,
  positions: [
    { fromDay: 1, toDay: 15, totalExpensesCents: 120_000 },
    { fromDay: 16, toDay: 30, totalExpensesCents: 30_000 },
  ],
  summaries: {
    byCategory: [
      { categoryId: 9, categoryName: 'Despesas eventuais', amountCents: 141_814, percent: 23.86 },
      { categoryId: 1, categoryName: 'Mercado', amountCents: 452_474, percent: 76.14 },
    ],
    byPayment: { pixCents: 99_399, creditCents: 494_889 },
    byCard: [
      { cardId: 5, cardName: 'Nubank', amountCents: 447_389 },
      { cardId: 6, cardName: 'Inter', amountCents: 47_500 },
    ],
    byPerson: [
      {
        personId: 7,
        personName: 'Mainha',
        amountCents: 20_000,
        byCard: [
          { cardId: 5, cardName: 'Nubank', amountCents: 15_000 },
          { cardId: 6, cardName: 'Inter', amountCents: 5_000 },
        ],
      },
    ],
  },
  expenses: [
    { id: 11, month: '2026-11', name: 'Aluguel', categoryId: 1, amountCents: 99_399, type: 'FIXED', paymentMethod: 'PIX', paid: false, date: '2026-11-05' },
    {
      id: 12, month: '2026-11', name: 'Sofá', categoryId: 9, amountCents: 141_814, type: 'CARD', paymentMethod: 'CREDIT', cardId: 5,
      paid: false, date: '2026-11-10', installmentNumber: 7, installmentCount: 12, installmentLabel: '7/12',
    },
    { id: 13, month: '2026-11', name: 'Roupa da mãe', categoryId: 1, amountCents: 20_000, type: 'CARD', paymentMethod: 'CREDIT', personId: 7, paid: false, date: '2026-11-12' },
  ],
  incomes: [
    { id: 21, month: '2026-11', name: 'Salário', date: '2026-11-05', amountCents: 643_358, reimbursement: false },
    { id: 22, month: '2026-11', name: 'Devolução', date: '2026-11-20', amountCents: 19_000, personId: 7, reimbursement: true, installmentNumber: 1, installmentCount: 3, installmentLabel: '1/3' },
  ],
})

const empty = (month: string): MonthView => ({
  month,
  totals: {
    totalExpensesCents: 0, totalFixedCents: 0, totalIncomeCents: 0, balanceCents: 0,
    thirdPartyExpensesCents: 0, reimbursementsCents: 0, ownBalanceCents: 0,
  },
  closingsEnabled: true,
  positions: [],
  summaries: { byCategory: [], byPayment: { pixCents: 0, creditCents: 0 }, byCard: [], byPerson: [] },
  expenses: [],
  incomes: [],
})

const baseReplies = (view: MonthView = november()): Replies => ({
  'GET /api/me': { body: { id: 1, name: 'Ana', email: 'a@x.com', onboardingSeen: true } },
  'GET /api/settings': { body: { currentYear: 2026, autoInstallments: true, closingsEnabled: true, closingDays: [15, 31] } },
  'GET /api/years': { body: [{ year: 2025, current: false }, { year: 2026, current: true }] },
  'GET /api/categories': {
    body: [
      { id: 1, year: 2026, name: 'Mercado', active: true },
      { id: 9, year: 2026, name: 'Despesas eventuais', active: true },
      { id: 4, year: 2026, name: 'Antiga', active: false },
    ],
  },
  'GET /api/cards': { body: [{ id: 5, year: 2026, name: 'Nubank', dueDay: 10, active: true }] },
  'GET /api/people': {
    body: [
      { id: 7, year: 2026, name: 'Mainha', active: true },
      { id: 8, year: 2026, name: 'Primo', active: false },
    ],
  },
  [NOV]: { body: view },
})

async function openMonth(replies: Replies = baseReplies(), path = '/month/2026-11', ready = 'Fixos') {
  const calls = stubApi(replies)
  const router = renderApp(path, { signedIn: true })
  await screen.findByRole('region', { name: ready })
  return { calls, router }
}

const section = (name: string) => screen.getByRole('region', { name })
const sent = (calls: Calls, key: string) => calls.filter((c) => c.key === key).map((c) => c.body)
const monthLoads = (calls: Calls, key = NOV) => calls.filter((c) => c.key === key).length

describe('month screen header and navigation (TASK-039: REQ-041, REQ-040)', () => {
  it('shows the totals and the closing positions exactly as the API sends them', async () => {
    await openMonth()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/novembro de 2026/i)
    const header = screen.getByText('Total de gastos').closest('dl') as HTMLElement
    expect(within(header).getByText('Total de gastos').nextSibling).toHaveTextContent('R$ 5.942,88')
    expect(within(header).getByText('Total de fixos').nextSibling).toHaveTextContent('R$ 993,99')
    expect(within(header).getByText('Entradas').nextSibling).toHaveTextContent('R$ 6.623,58')
    expect(within(header).getByText('Saldo do mês').nextSibling).toHaveTextContent('R$ 680,70')

    const positions = section('Fechamentos')
    expect(within(positions).getByText('Do dia 1 ao 15').closest('li')).toHaveTextContent('Gastos R$ 1.200,00')
    expect(within(positions).getByText('Do dia 16 ao 30').closest('li')).toHaveTextContent('Gastos R$ 300,00')
  })

  it('shows no positions when the closings are turned off (REQ-048)', async () => {
    await openMonth(baseReplies({ ...november(), closingsEnabled: false, positions: [] }))
    expect(screen.queryByRole('region', { name: 'Fechamentos' })).not.toBeInTheDocument()
    expect(screen.getByText('Total de gastos').nextSibling).toHaveTextContent('R$ 5.942,88')
  })

  it('a negative balance is shown with its sign', async () => {
    await openMonth(baseReplies({ ...november(), totals: { ...november().totals, balanceCents: -30_000 } }))
    expect(screen.getByText('Total de gastos').closest('dl')).toHaveTextContent('-R$ 300,00')
  })

  it('moves to the previous and next month and the numbers follow the API', async () => {
    const replies = baseReplies()
    replies['GET /api/months/2026-10'] = { body: { ...empty('2026-10'), totals: { ...empty('2026-10').totals, totalExpensesCents: 12_345 } } }
    replies['GET /api/months/2026-12'] = { body: empty('2026-12') }
    const { calls, router } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/month/2026-10'))
    expect(await screen.findByRole('heading', { level: 1, name: /outubro de 2026/i })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Total de gastos').nextSibling).toHaveTextContent('R$ 123,45'))

    await userEvent.click(screen.getByRole('button', { name: 'Próximo mês' }))
    await userEvent.click(screen.getByRole('button', { name: 'Próximo mês' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/month/2026-12'))
    await waitFor(() => expect(screen.getByText('Total de gastos').nextSibling).toHaveTextContent('R$ 0,00'))
    expect(monthLoads(calls, 'GET /api/months/2026-12')).toBe(1)
  })

  it('picks a month and a year from the selectors; the year list comes from the API', async () => {
    const replies = baseReplies()
    replies['GET /api/months/2026-03'] = { body: empty('2026-03') }
    replies['GET /api/months/2025-03'] = { body: empty('2025-03') }
    replies['GET /api/categories'] = { body: [] }
    replies['GET /api/cards'] = { body: [] }
    replies['GET /api/people'] = { body: [] }
    const { router } = await openMonth(replies)

    const year = screen.getByRole('combobox', { name: 'Escolher ano' })
    expect(within(year).getAllByRole('option').map((o) => o.textContent)).toEqual(['2025', '2026'])
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Escolher mês' }), 'março')
    await waitFor(() => expect(router.state.location.pathname).toBe('/month/2026-03'))
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Escolher ano' }), '2025')
    await waitFor(() => expect(router.state.location.pathname).toBe('/month/2025-03'))
    expect(await screen.findByRole('heading', { level: 1, name: /março de 2025/i })).toBeInTheDocument()
  })

  it('rolls over from December to January of the next year', async () => {
    const replies = baseReplies()
    replies['GET /api/months/2026-12'] = { body: empty('2026-12') }
    replies['GET /api/months/2027-01'] = { body: empty('2027-01') }
    const { router } = await openMonth(replies, '/month/2026-12')
    await userEvent.click(screen.getByRole('button', { name: 'Próximo mês' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/month/2027-01'))
    // a year the user does not have yet is still offered while it is shown (opening it creates it)
    expect(within(screen.getByRole('combobox', { name: 'Escolher ano' })).getByRole('option', { name: '2027' })).toBeInTheDocument()
  })

  it('reports a malformed month without calling the month API', async () => {
    const calls = stubApi(baseReplies())
    renderApp('/month/2026-13', { signedIn: true })
    expect(await screen.findByRole('alert')).toHaveTextContent('Mês inválido.')
    expect(calls.some((c) => c.key.startsWith('GET /api/months/'))).toBe(false)
  })

  it('reports an API error while opening the month', async () => {
    const replies = baseReplies()
    replies[NOV] = { status: 500, body: { code: 'INTERNAL_ERROR', fields: {} } }
    stubApi(replies)
    renderApp('/month/2026-11', { signedIn: true })
    expect(await screen.findByText('Algo deu errado. Tente de novo.')).toBeInTheDocument()
  })
})

describe('category limits (TASK-068: REQ-051)', () => {
  const withLimits = (mercado: MonthView['summaries']['byCategory'][number]['limitStatus'], eventuais?: 'NEAR' | 'OVER' | 'UNDER') => {
    const view = november()
    view.summaries.byCategory = [
      { categoryId: 9, categoryName: 'Despesas eventuais', amountCents: 141_814, percent: 23.86, limitCents: 140_000, limitStatus: eventuais ?? 'UNDER' },
      { categoryId: 1, categoryName: 'Mercado', amountCents: 452_474, percent: 76.14, limitCents: 500_000, limitStatus: mercado },
    ]
    return view
  }

  /** Marks the rent as paid: the month is fetched again and the API answers with `after`. */
  async function saveSomethingAndRefetch(replies: Replies, after: MonthView) {
    replies['PATCH /api/expenses/11/paid'] = { body: { ...november().expenses[0], paid: true } }
    after.expenses[0].paid = true
    replies[NOV] = { body: after }
    await userEvent.click(screen.getByRole('checkbox', { name: 'Aluguel pago' }))
  }

  it('opening a month never pops a notice, and nothing stays fixed on the screen', async () => {
    await openMonth(baseReplies(withLimits('NEAR', 'OVER')))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    // the summary keeps "used of limit"
    const byCategory = section('Por categoria')
    expect(within(byCategory).getByText('Mercado').closest('li')).toHaveTextContent('R$ 4.524,74 de R$ 5.000,00 · está perto do limite')
    expect(within(byCategory).getByText('Despesas eventuais').closest('li')).toHaveTextContent('passou do limite')
  })

  it('notifies when an entry takes a category near or over its limit, and the notice can be closed', async () => {
    const replies = baseReplies(withLimits('UNDER', 'NEAR'))
    await openMonth(replies)
    await saveSomethingAndRefetch(replies, withLimits('NEAR', 'OVER'))

    const notices = await screen.findAllByRole('status')
    expect(notices).toHaveLength(2)
    expect(notices[0]).toHaveTextContent('Despesas eventuais passou do limite (R$ 1.418,14 de R$ 1.400,00)')
    expect(notices[1]).toHaveTextContent('Mercado está perto do limite (R$ 4.524,74 de R$ 5.000,00)')

    await userEvent.click(within(notices[0]).getByRole('button', { name: 'Fechar aviso' }))
    expect(screen.getAllByRole('status')).toHaveLength(1)
  })

  it('does not notify again while the status stays the same', async () => {
    const replies = baseReplies(withLimits('NEAR'))
    await openMonth(replies)
    await saveSomethingAndRefetch(replies, withLimits('NEAR'))
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Aluguel pago' })).toBeChecked())
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('the notice goes away on its own', async () => {
    const replies = baseReplies(withLimits('UNDER'))
    await openMonth(replies)
    await saveSomethingAndRefetch(replies, withLimits('OVER'))
    expect(await screen.findByRole('status')).toHaveTextContent('Mercado passou do limite')
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument(), { timeout: LIMIT_NOTICE_MS + 2000 })
  }, LIMIT_NOTICE_MS + 5000)
})

describe('month limits (TASK-074: REQ-051, D-22)', () => {
  const limits = [
    { categoryId: 1, categoryName: 'Mercado', defaultCents: 100_000, monthCents: null },
    { categoryId: 9, categoryName: 'Lazer', defaultCents: 50_000, monthCents: 120_000 },
    { categoryId: 4, categoryName: 'Pets', defaultCents: null, monthCents: null },
  ]

  it('shows each default and changes, keeps or removes only this month', async () => {
    const replies = baseReplies()
    replies['GET /api/months/2026-11/category-limits'] = { body: limits }
    replies['PUT /api/months/2026-11/category-limits/1'] = { status: 204 }
    replies['DELETE /api/months/2026-11/category-limits/9'] = { status: 204 }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Limites do mês' }))
    const dialog = await screen.findByRole('dialog', { name: 'Limites de novembro de 2026' })
    expect(within(dialog).getByText('Mercado').closest('li')).toHaveTextContent('Padrão: R$ 1.000,00')
    expect(within(dialog).getByText('Pets').closest('li')).toHaveTextContent('Sem limite padrão')
    expect(within(dialog).getByLabelText('Limite de Lazer neste mês (R$)')).toHaveValue('1200,00')

    await userEvent.type(within(dialog).getByLabelText('Limite de Mercado neste mês (R$)'), '800')
    await userEvent.clear(within(dialog).getByLabelText('Limite de Lazer neste mês (R$)'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(calls.find((c) => c.key === 'PUT /api/months/2026-11/category-limits/1')?.body).toEqual({ limitCents: 80_000 })
    expect(calls.some((c) => c.key === 'DELETE /api/months/2026-11/category-limits/9')).toBe(true)
    // nothing changed for Pets: nothing sent
    expect(calls.some((c) => c.key.includes('/category-limits/4'))).toBe(false)
  })

  it('refuses a limit that is not a positive amount', async () => {
    const replies = baseReplies()
    replies['GET /api/months/2026-11/category-limits'] = { body: limits }
    const { calls } = await openMonth(replies)
    await userEvent.click(screen.getByRole('button', { name: 'Limites do mês' }))
    const dialog = await screen.findByRole('dialog', { name: 'Limites de novembro de 2026' })
    await userEvent.type(within(dialog).getByLabelText('Limite de Pets neste mês (R$)'), '0')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Informe um valor maior que zero ou deixe em branco.')).toBeInTheDocument()
    expect(calls.some((c) => c.key.startsWith('PUT') || c.key.startsWith('DELETE'))).toBe(false)
  })
})

describe('hide values (TASK-065: REQ-049)', () => {
  it('the eye hides every amount, is remembered, and shows them again', async () => {
    await openMonth()
    const header = screen.getByText('Total de gastos').closest('dl') as HTMLElement
    const balance = () => within(header).getByText('Saldo do mês').nextSibling
    const eye = screen.getAllByRole('button', { name: 'Ocultar valores' })[0]
    expect(eye).toHaveAttribute('aria-pressed', 'false')
    expect(balance()).toHaveTextContent('R$ 680,70')

    await userEvent.click(eye)

    expect(eye).toHaveAttribute('aria-pressed', 'true')
    // every control on screen follows the same choice
    expect(screen.getAllByRole('button', { name: 'Ocultar valores' })[1]).toHaveAttribute('aria-pressed', 'true')
    expect(localStorage.getItem('nize.hideValues')).toBe('true')
    const main = document.body.textContent ?? ''
    for (const amount of ['680,70', '5.942,88', '993,99', '6.623,58', '1.200,00']) expect(main).not.toContain(amount)
    expect(balance()).toHaveTextContent('R$ ••••')
    expect(balance()).toHaveTextContent('valor oculto')

    await userEvent.click(eye)
    expect(balance()).toHaveTextContent('R$ 680,70')
    expect(localStorage.getItem('nize.hideValues')).toBe('false')
  })

  it('opens with the values hidden when that was the last choice', async () => {
    localStorage.setItem('nize.hideValues', 'true')
    await openMonth()
    expect(screen.getByText('Total de gastos').nextSibling).toHaveTextContent('R$ ••••')
    expect(document.body.textContent).not.toContain('5.942,88')
  })
})

describe('month lists (TASK-040: REQ-015, REQ-016, REQ-021, REQ-023, REQ-024)', () => {
  it('splits fixed, card and income entries, with names of category, card and person and the N/M indicator', async () => {
    await openMonth()
    const fixed = section('Fixos')
    expect(within(fixed).getByText('Aluguel')).toBeInTheDocument()
    expect(within(fixed).getByText(/^Mercado · Pix/)).toBeInTheDocument()
    // the day leads the row; the full date is there for screen readers
    expect(within(fixed).getByText('05/11').closest('time')).toHaveAttribute('dateTime', '2026-11-05')
    // the date says what it is: day/month with "lançado" under it; screen readers hear "Lançado em …"
    const row = within(fixed).getByText('05/11').closest('li') as HTMLElement
    expect(row).toHaveTextContent('05/11lançado')
    expect(within(row).getByText(/^Lançado em /)).toHaveClass('sr-only')
    expect(within(fixed).getByText('R$ 993,99', { selector: 'h2 + span' })).toBeInTheDocument()

    const card = section('Cartão')
    expect(within(card).queryByText('Aluguel')).not.toBeInTheDocument()
    expect(within(card).getByText('7/12')).toBeInTheDocument()
    expect(within(card).getByText(/^Despesas eventuais · Nubank/)).toBeInTheDocument()
    // an old credit row without a card asks for one (no "Sem cartão", REQ-016)
    expect(within(card).getByText(/Mercado · Cartão a definir · gasto de Mainha/)).toBeInTheDocument()

    const incomes = section('Entradas')
    expect(within(incomes).getByText('Salário')).toBeInTheDocument()
    expect(within(incomes).getByText('1/3')).toBeInTheDocument()
    expect(within(incomes).getByText(/Reembolso de Mainha/)).toBeInTheDocument()
  })

  it('shows an empty message for each list of an empty month', async () => {
    await openMonth(baseReplies(empty('2026-11')))
    expect(screen.getByText('Nenhum gasto fixo neste mês.')).toBeInTheDocument()
    expect(screen.getByText('Nenhum gasto de cartão neste mês.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma entrada neste mês.')).toBeInTheDocument()
  })

  it('marking a fixed expense as paid only sends the mark; the totals shown stay the API ones', async () => {
    const replies = baseReplies()
    replies['PATCH /api/expenses/11/paid'] = { body: { ...november().expenses[0], paid: true } }
    const { calls } = await openMonth(replies)
    const view = november()
    view.expenses[0].paid = true
    replies[NOV] = { body: view } // the API answers the refetch with the same totals (REQ-033)

    const box = screen.getByRole('checkbox', { name: 'Aluguel pago' })
    expect(box).not.toBeChecked()
    await userEvent.click(box)
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Aluguel pago' })).toBeChecked())
    expect(sent(calls, 'PATCH /api/expenses/11/paid')).toEqual([{ paid: true }])
    expect(monthLoads(calls)).toBe(2)
    expect(screen.getByText('Total de gastos').nextSibling).toHaveTextContent('R$ 5.942,88')
    expect(section('Fechamentos')).toHaveTextContent('Gastos R$ 1.200,00')
  })

  it('reports a failure to mark paid', async () => {
    const replies = baseReplies()
    replies['PATCH /api/expenses/11/paid'] = { status: 404, body: { code: 'NOT_FOUND', fields: {} } }
    await openMonth(replies)
    await userEvent.click(screen.getByRole('checkbox', { name: 'Aluguel pago' }))
    expect(await screen.findByText('Não encontramos esse item. Atualize a página.')).toBeInTheDocument()
  })

  it('edits a fixed expense in the expense window and the list shows the change at once', async () => {
    const replies = baseReplies()
    replies['PUT /api/expenses/11'] = { body: { ...november().expenses[0], name: 'Aluguel novo' } }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Editar Aluguel' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar lançamento' })
    await within(dialog).findByRole('option', { name: 'Mercado' })
    expect(within(dialog).getByLabelText('Nome')).toHaveValue('Aluguel')
    expect(within(dialog).getByLabelText('Valor (R$)')).toHaveValue('993,99')
    expect(within(dialog).getByLabelText('Categoria')).toHaveValue('1')
    expect(within(dialog).getByLabelText('Data')).toHaveValue('2026-11-05')
    expect(within(dialog).queryByRole('button', { name: 'Salvar e lançar outro' })).not.toBeInTheDocument()

    const view = november()
    view.expenses[0] = { ...view.expenses[0], name: 'Aluguel novo', amountCents: 100_000 }
    replies[NOV] = { body: view }
    await userEvent.clear(within(dialog).getByLabelText('Nome'))
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Aluguel novo')
    await userEvent.clear(within(dialog).getByLabelText('Valor (R$)'))
    await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), '1.000')
    await userEvent.click(within(dialog).getByLabelText('Já foi pago'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(sent(calls, 'PUT /api/expenses/11')).toEqual([
      { name: 'Aluguel novo', categoryId: 1, amountCents: 100_000, type: 'FIXED', paymentMethod: 'PIX', paid: true, date: '2026-11-05' },
    ])
    expect(await within(section('Fixos')).findByText('Aluguel novo')).toBeInTheDocument()
  })

  it('edits an installment: type locked, no new installments, the paid mark kept', async () => {
    const replies = baseReplies()
    replies['PUT /api/expenses/12'] = { body: november().expenses[1] }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Editar Sofá' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar lançamento' })
    await within(dialog).findByRole('option', { name: 'Nubank' })
    expect(within(dialog).getByLabelText('Tipo')).toBeDisabled()
    expect(within(dialog).queryByLabelText('Como lançar')).not.toBeInTheDocument()
    expect(within(dialog).getByText(/Parcela 7\/12/)).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Cartão')).toHaveValue('5')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(sent(calls, 'PUT /api/expenses/12')).toHaveLength(1))
    expect(sent(calls, 'PUT /api/expenses/12')[0]).toEqual({
      name: 'Sofá', categoryId: 9, amountCents: 141_814, type: 'CARD', paymentMethod: 'CREDIT', cardId: 5, paid: false, date: '2026-11-10',
    })
  })

  it('keeps offering an inactive category or person an entry already uses', async () => {
    const view = november()
    view.expenses[0].categoryId = 4
    view.incomes[1].personId = 8
    await openMonth(baseReplies(view))
    await userEvent.click(screen.getByRole('button', { name: 'Editar Aluguel' }))
    let dialog = await screen.findByRole('dialog', { name: 'Editar lançamento' })
    expect(await within(dialog).findByRole('option', { name: 'Antiga' })).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))

    await userEvent.click(screen.getByRole('button', { name: 'Editar Devolução' }))
    dialog = await screen.findByRole('dialog', { name: 'Editar entrada' })
    expect(within(dialog).getByRole('option', { name: 'Primo' })).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Reembolso de (pessoa)')).toHaveValue('8')
  })

  it('an installment asks: only this one (default) or this one and the next ones (TASK-066, REQ-050)', async () => {
    const replies = baseReplies()
    replies['DELETE /api/expenses/12'] = { status: 204 }
    replies['DELETE /api/expenses/12?scope=following'] = { status: 204 }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Sofá' }))
    let dialog = await screen.findByRole('dialog', { name: 'Excluir' })
    expect(within(dialog).getByRole('radio', { name: 'Só esta parcela (7/12)' })).toBeChecked()
    expect(within(dialog).getByRole('radio', { name: 'Esta e as próximas (7/12 até 12/12)' })).not.toBeChecked()
    // just confirming deletes only this one
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))
    await waitFor(() => expect(sent(calls, 'DELETE /api/expenses/12')).toHaveLength(1))

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Sofá' }))
    dialog = await screen.findByRole('dialog', { name: 'Excluir' })
    // the choice starts over at "only this one" every time
    expect(within(dialog).getByRole('radio', { name: 'Só esta parcela (7/12)' })).toBeChecked()
    await userEvent.click(within(dialog).getByRole('radio', { name: 'Esta e as próximas (7/12 até 12/12)' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))
    await waitFor(() => expect(sent(calls, 'DELETE /api/expenses/12?scope=following')).toHaveLength(1))
  })

  it('an income in installments offers the same choice; a single entry does not', async () => {
    const replies = baseReplies()
    replies['DELETE /api/incomes/22?scope=following'] = { status: 204 }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Aluguel' }))
    let dialog = await screen.findByRole('dialog', { name: 'Excluir' })
    expect(within(dialog).queryByRole('radio')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Devolução' }))
    dialog = await screen.findByRole('dialog', { name: 'Excluir' })
    await userEvent.click(within(dialog).getByRole('radio', { name: 'Esta e as próximas (1/3 até 3/3)' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))
    await waitFor(() => expect(sent(calls, 'DELETE /api/incomes/22?scope=following')).toHaveLength(1))
  })

  it('deletes an expense after confirming and the list updates at once', async () => {
    const replies = baseReplies()
    replies['DELETE /api/expenses/13'] = { status: 204 }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Roupa da mãe' }))
    const dialog = await screen.findByRole('dialog', { name: 'Excluir' })
    expect(dialog).toHaveTextContent('Excluir "Roupa da mãe"? Isso não pode ser desfeito.')
    const view = november()
    view.expenses = view.expenses.filter((e) => e.id !== 13)
    replies[NOV] = { body: view }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(sent(calls, 'DELETE /api/expenses/13')).toHaveLength(1)
    await waitFor(() => expect(within(section('Cartão')).queryByText('Roupa da mãe')).not.toBeInTheDocument())
  })

  it('cancelling a delete sends nothing; a failed delete keeps the window with the message', async () => {
    const replies = baseReplies()
    replies['DELETE /api/incomes/21'] = { status: 404, body: { code: 'NOT_FOUND', fields: {} } }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Salário' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Salário' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Não encontramos esse item.')
    expect(sent(calls, 'DELETE /api/incomes/21')).toHaveLength(1)
  })

  it('edits and deletes an income', async () => {
    const replies = baseReplies()
    replies['PUT /api/incomes/22'] = { body: november().incomes[1] }
    replies['DELETE /api/incomes/22'] = { status: 204 }
    const { calls } = await openMonth(replies)

    await userEvent.click(screen.getByRole('button', { name: 'Editar Devolução' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar entrada' })
    expect(within(dialog).getByLabelText('Valor (R$)')).toHaveValue('190,00')
    expect(within(dialog).getByText(/Parcela 1\/3/)).toBeInTheDocument()
    expect(within(dialog).queryByLabelText('É uma entrada parcelada')).not.toBeInTheDocument()
    await userEvent.selectOptions(within(dialog).getByLabelText('Reembolso de (pessoa)'), '')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(sent(calls, 'PUT /api/incomes/22')).toEqual([{ name: 'Devolução', amountCents: 19_000, date: '2026-11-20' }])

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Devolução' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))
    await waitFor(() => expect(sent(calls, 'DELETE /api/incomes/22')).toHaveLength(1))
  })
})

describe('summaries panel and own balance (TASK-041: REQ-034..038)', () => {
  const line = (block: HTMLElement, label: string) => within(block).getByText(label).closest('li') as HTMLElement

  it('shows each summary with the numbers of the API, percentages included', async () => {
    await openMonth()
    const byCategory = section('Por categoria')
    expect(line(byCategory, 'Despesas eventuais')).toHaveTextContent('23,86%R$ 1.418,14')
    expect(line(byCategory, 'Mercado')).toHaveTextContent('76,14%R$ 4.524,74')

    const byPayment = section('Por forma de pagamento')
    expect(line(byPayment, 'Pix')).toHaveTextContent('R$ 993,99')
    expect(line(byPayment, 'Crédito')).toHaveTextContent('R$ 4.948,89')

    const byCard = section('Por cartão')
    expect(line(byCard, 'Nubank')).toHaveTextContent('R$ 4.473,89')
    expect(line(byCard, 'Inter')).toHaveTextContent('R$ 475,00')
    expect(byCard).not.toHaveTextContent('Sem cartão')

    const byPerson = section('Terceiros por pessoa e cartão')
    expect(line(byPerson, 'Mainha')).toHaveTextContent('R$ 200,00')
    expect(line(byPerson, 'Nubank')).toHaveTextContent('R$ 150,00')
    expect(line(byPerson, 'Inter')).toHaveTextContent('R$ 50,00')
  })

  it('shows the own balance and its parts as the API computed them', async () => {
    await openMonth()
    const own = section('Saldo próprio')
    expect(within(own).getByText('Saldo próprio').nextSibling).toHaveTextContent('R$ 690,70')
    expect(line(own, 'Saldo do mês')).toHaveTextContent('R$ 680,70')
    expect(line(own, 'Gastos de terceiros')).toHaveTextContent('R$ 200,00')
    expect(line(own, 'Reembolsos recebidos')).toHaveTextContent('R$ 190,00')
  })

  it('shows empty messages for a month without expenses', async () => {
    await openMonth(baseReplies(empty('2026-11')))
    expect(within(section('Por categoria')).getByText('Nenhum gasto neste mês.')).toBeInTheDocument()
    expect(within(section('Por cartão')).getByText('Nenhum gasto no crédito neste mês.')).toBeInTheDocument()
    expect(within(section('Terceiros por pessoa e cartão')).getByText('Nenhum gasto de terceiros neste mês.')).toBeInTheDocument()
  })

  it('can be opened and closed on a phone', async () => {
    await openMonth()
    const toggle = screen.getByRole('button', { name: 'Mostrar' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(document.getElementById('month-summaries')).toHaveClass('hidden', 'md:flex')
    await userEvent.click(toggle)
    expect(screen.getByRole('button', { name: 'Ocultar' })).toHaveAttribute('aria-expanded', 'true')
    expect(document.getElementById('month-summaries')).not.toHaveClass('hidden')
  })
})

describe('quick entry of income (TASK-042: REQ-042, REQ-024, REQ-026)', () => {
  const POST = 'POST /api/months/2026-11/incomes'
  const created = { status: 201, body: { id: 30, month: '2026-11', name: 'x', date: '2026-11-01', amountCents: 1, reimbursement: false } }

  async function openIncome() {
    const replies = baseReplies()
    replies[POST] = created
    const { calls } = await openMonth(replies)
    await userEvent.click(screen.getByRole('button', { name: 'Nova entrada' }))
    const dialog = await screen.findByRole('dialog', { name: 'Nova entrada' })
    await within(dialog).findByRole('option', { name: 'Mainha' })
    return { calls, dialog, replies }
  }

  it('records a salary and the month is refetched', async () => {
    const { calls, dialog } = await openIncome()
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Salário')
    await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), '6.433,58')
    fireEvent.change(within(dialog).getByLabelText('Data'), { target: { value: '2026-11-05' } })
    expect(within(dialog).queryByRole('option', { name: 'Primo' })).not.toBeInTheDocument() // inactive person
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(sent(calls, POST)).toEqual([{ name: 'Salário', amountCents: 643_358, date: '2026-11-05' }])
    await waitFor(() => expect(monthLoads(calls)).toBe(2))
  })

  it('records a reimbursement in installments and "save and add another" keeps the window open', async () => {
    const { calls, dialog } = await openIncome()
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Devolução')
    await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), '190')
    fireEvent.change(within(dialog).getByLabelText('Data'), { target: { value: '2026-11-20' } })
    await userEvent.selectOptions(within(dialog).getByLabelText('Reembolso de (pessoa)'), 'Mainha')
    await userEvent.click(within(dialog).getByLabelText('É uma entrada parcelada'))
    await userEvent.type(within(dialog).getByLabelText('Parcela atual'), '1')
    await userEvent.type(within(dialog).getByLabelText('Total de parcelas'), '3')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar e lançar outra' }))

    expect(await within(dialog).findByRole('status')).toHaveTextContent('Entrada salva.')
    expect(within(dialog).getByLabelText('Nome')).toHaveValue('')
    expect(within(dialog).getByLabelText('Reembolso de (pessoa)')).toHaveValue('7')
    expect(sent(calls, POST)).toEqual([
      { name: 'Devolução', amountCents: 19_000, date: '2026-11-20', personId: 7, installmentNumber: 1, installmentCount: 3 },
    ])
  })

  it('checks the form before calling the API and shows API errors', async () => {
    const { calls, dialog, replies } = await openIncome()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Informe um nome.')).toBeInTheDocument()
    expect(within(dialog).getByText('Informe um valor maior que zero, como 49,90.')).toBeInTheDocument()

    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Bico')
    await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), '50')
    fireEvent.change(within(dialog).getByLabelText('Data'), { target: { value: '2026-12-01' } })
    await userEvent.click(within(dialog).getByLabelText('É uma entrada parcelada'))
    await userEvent.type(within(dialog).getByLabelText('Total de parcelas'), '1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Escolha uma data dentro do mês 2026-11.')).toBeInTheDocument()
    expect(within(dialog).getByText('Informe o total de parcelas, de 2 a 120.')).toBeInTheDocument()

    await userEvent.clear(within(dialog).getByLabelText('Total de parcelas'))
    await userEvent.type(within(dialog).getByLabelText('Total de parcelas'), '3')
    await userEvent.type(within(dialog).getByLabelText('Parcela atual'), '4')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('A parcela atual deve estar entre 1 e o total.')).toBeInTheDocument()
    expect(sent(calls, POST)).toHaveLength(0)

    replies[POST] = { status: 400, body: { code: 'INACTIVE_REFERENCE', fields: {} } }
    await userEvent.clear(within(dialog).getByLabelText('Parcela atual'))
    await userEvent.type(within(dialog).getByLabelText('Parcela atual'), '2')
    fireEvent.change(within(dialog).getByLabelText('Data'), { target: { value: '2026-11-02' } })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('está desativada')
  })

  it('an expense goes in from the month screen without leaving it', async () => {
    const replies = baseReplies()
    replies['POST /api/months/2026-11/expenses'] = { status: 201, body: november().expenses[0] }
    const { calls, router } = await openMonth(replies)
    await userEvent.click(screen.getByRole('button', { name: 'Novo lançamento' }))
    const dialog = await screen.findByRole('dialog', { name: 'Novo lançamento' })
    await within(dialog).findByRole('option', { name: 'Mercado' })
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Feira')
    await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), '80')
    await userEvent.selectOptions(within(dialog).getByLabelText('Categoria'), 'Mercado')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe('/month/2026-11')
    await waitFor(() => expect(monthLoads(calls)).toBe(2))
  })

  it('is translated to English', async () => {
    const { setLanguage } = await import('../i18n')
    setLanguage('en')
    try {
      await openMonth(baseReplies(), '/month/2026-11', 'Fixed')
      expect(screen.getByText('Total expenses').nextSibling).toHaveTextContent('R$ 5,942.88')
      expect(within(section('By category')).getByText('23.86%')).toBeInTheDocument()
      expect(within(section('Closings')).getByText('Day 16 to 30').closest('li')).toHaveTextContent('Spent R$ 300.00')
      await userEvent.click(screen.getByRole('button', { name: 'New income' }))
      expect(await screen.findByRole('dialog', { name: 'New income' })).toBeInTheDocument()
    } finally {
      setLanguage('pt')
    }
  })
})
