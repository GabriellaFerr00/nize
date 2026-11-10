import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp, stubApi } from '../test/renderApp'

type Replies = Parameters<typeof stubApi>[0]

const POST = 'POST /api/months/2026-11/expenses'
const CREATED = { status: 201, body: { id: 1, month: '2026-11', name: 'x', amountCents: 100, type: 'FIXED', paymentMethod: 'PIX', paid: false, date: '2026-11-15' } }

const replies = (autoInstallments = true): Replies => ({
  'GET /api/me': { body: { id: 1, name: 'Ana', email: 'a@x.com', onboardingSeen: true } },
  'GET /api/settings': { body: { currentYear: 2026, autoInstallments } },
  'GET /api/years': { body: [{ year: 2026, current: true }] },
  'GET /api/categories': {
    body: [
      { id: 1, year: 2026, name: 'Mercado', active: true },
      { id: 2, year: 2026, name: 'Lazer', active: false },
      { id: 3, year: 2026, name: 'Eletrônicos', active: true },
    ],
  },
  'GET /api/cards': {
    body: [
      { id: 5, year: 2026, name: 'Nubank', dueDay: 10, active: true },
      { id: 6, year: 2026, name: 'Velho', dueDay: 3, active: false },
    ],
  },
  'GET /api/people': { body: [{ id: 7, year: 2026, name: 'Mainha', active: true }] },
  [POST]: CREATED,
})

async function openForm(map: Replies = replies()) {
  const calls = stubApi(map)
  renderApp('/month/2026-11', { signedIn: true })
  await userEvent.click(await screen.findByRole('button', { name: 'Novo lançamento' }))
  const dialog = await screen.findByRole('dialog', { name: 'Novo lançamento' })
  // wait until the catalogs are loaded
  await within(dialog).findByRole('option', { name: 'Mercado' })
  return { calls, dialog }
}

async function fillBasics(dialog: HTMLElement, name = 'Aluguel', amount = '1.234,56') {
  await userEvent.type(within(dialog).getByLabelText('Nome'), name)
  await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), amount)
  await userEvent.selectOptions(within(dialog).getByLabelText('Categoria'), 'Mercado')
  fireEvent.change(within(dialog).getByLabelText('Data'), { target: { value: '2026-11-15' } })
}

const posted = (calls: { key: string; body: unknown }[]) => calls.filter((c) => c.key === POST).map((c) => c.body)

describe('new expense window (REQ-016..019, REQ-042)', () => {
  it('records a fixed Pix expense, turning the typed amount into cents', async () => {
    const { calls, dialog } = await openForm()
    await fillBasics(dialog)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(posted(calls)).toEqual([
      { name: 'Aluguel', categoryId: 1, amountCents: 123_456, type: 'FIXED', paymentMethod: 'PIX', paid: false, date: '2026-11-15' },
    ])
  })

  it('offers only active categories, cards and people', async () => {
    const { dialog } = await openForm()
    expect(within(dialog).queryByRole('option', { name: 'Lazer' })).not.toBeInTheDocument()
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    expect(within(dialog).getByRole('option', { name: 'Nubank' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('option', { name: 'Velho' })).not.toBeInTheDocument()
    expect(within(dialog).getByRole('option', { name: 'Mainha' })).toBeInTheDocument()
  })

  it('lets a fixed expense go on credit (a subscription), with or without a card', async () => {
    const { calls, dialog } = await openForm()
    expect(within(dialog).queryByLabelText('Cartão')).not.toBeInTheDocument()
    await userEvent.selectOptions(within(dialog).getByLabelText('Forma de pagamento'), 'CREDIT')
    await userEvent.selectOptions(within(dialog).getByLabelText('Cartão'), 'Nubank')
    await fillBasics(dialog, 'Streaming', '39,90')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(posted(calls)).toHaveLength(1))
    expect(posted(calls)[0]).toMatchObject({ type: 'FIXED', paymentMethod: 'CREDIT', cardId: 5, amountCents: 3_990 })
  })

  it('records a card expense of a third party with the card used', async () => {
    const { calls, dialog } = await openForm()
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    expect(within(dialog).queryByLabelText('Forma de pagamento')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Já foi pago')).not.toBeInTheDocument()
    await userEvent.selectOptions(within(dialog).getByLabelText('Cartão'), 'Nubank')
    await userEvent.selectOptions(within(dialog).getByLabelText('Pessoa (gasto de terceiro)'), 'Mainha')
    await fillBasics(dialog, 'Roupa da mãe', '120')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(posted(calls)).toHaveLength(1))
    expect(posted(calls)[0]).toEqual({
      name: 'Roupa da mãe', categoryId: 1, amountCents: 12_000, type: 'CARD', paymentMethod: 'CREDIT',
      cardId: 5, personId: 7, date: '2026-11-15',
    })
  })

  it('"save and add another" keeps the window open and lets three expenses go in a row', async () => {
    const { calls, dialog } = await openForm()
    await fillBasics(dialog, 'Um', '10')

    for (const [name, amount] of [['Um', '10'], ['Dois', '20'], ['Três', '30']]) {
      if (name !== 'Um') {
        await userEvent.type(within(dialog).getByLabelText('Nome'), name)
        await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), amount)
      }
      await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar e lançar outro' }))
      expect(await within(dialog).findByRole('status')).toHaveTextContent('Lançamento salvo.')
      await waitFor(() => expect(within(dialog).getByLabelText('Nome')).toHaveValue(''))
      expect(within(dialog).getByLabelText('Valor (R$)')).toHaveValue('')
    }

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Categoria')).toHaveValue('1') // kept for the next one
    expect(within(dialog).getByLabelText('Data')).toHaveValue('2026-11-15')
    expect(posted(calls).map((b) => (b as { amountCents: number }).amountCents)).toEqual([1_000, 2_000, 3_000])
  })

  it('generates installments automatically, with the amount as total or per installment', async () => {
    const { calls, dialog } = await openForm()
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    await userEvent.selectOptions(within(dialog).getByLabelText('Como lançar'), 'auto')
    await userEvent.type(within(dialog).getByLabelText('Número de parcelas'), '3')
    await fillBasics(dialog, 'Notebook', '100')
    await userEvent.selectOptions(within(dialog).getByLabelText('Cartão'), 'Nubank')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar e lançar outro' }))
    await waitFor(() => expect(posted(calls)).toHaveLength(1))

    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Fone')
    await userEvent.type(within(dialog).getByLabelText('Valor (R$)'), '50')
    await userEvent.type(within(dialog).getByLabelText('Número de parcelas'), '4')
    await userEvent.selectOptions(within(dialog).getByLabelText('O valor informado é'), 'each')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(posted(calls)).toHaveLength(2))
    expect(posted(calls)[0]).toMatchObject({ amountCents: 10_000, generateInstallments: true, installmentCount: 3, amountIsTotal: true })
    expect(posted(calls)[1]).toMatchObject({ amountCents: 5_000, generateInstallments: true, installmentCount: 4, amountIsTotal: false })
  })

  it('credit needs a card: nothing is sent without one (REQ-016, 2026-11-09)', async () => {
    const { calls, dialog } = await openForm()
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    await fillBasics(dialog, 'Tênis', '200')
    expect(within(within(dialog).getByLabelText('Cartão')).queryByRole('option', { name: 'Sem cartão' })).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(within(dialog).getByText('Escolha um cartão ou crie um novo.')).toBeInTheDocument()
    expect(posted(calls)).toHaveLength(0)
  })

  it('creates a card inside the entry and selects it', async () => {
    const map = replies()
    map['POST /api/cards'] = { status: 201, body: { id: 9, year: 2026, name: 'Inter', dueDay: 20, active: true } }
    const { calls, dialog } = await openForm(map)
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    await fillBasics(dialog, 'Tênis', '200')
    await userEvent.selectOptions(within(dialog).getByLabelText('Cartão'), '+ Novo cartão')

    const fields = within(dialog).getByRole('group', { name: 'Novo cartão' })
    await userEvent.click(within(fields).getByRole('button', { name: 'Criar cartão' }))
    expect(within(fields).getByText('Informe um nome.')).toBeInTheDocument()
    expect(within(fields).getByText('Informe um dia de 1 a 31.')).toBeInTheDocument()

    await userEvent.click(within(fields).getByRole('radio', { name: 'Inter' }))
    expect(within(fields).getByLabelText('Nome')).toHaveValue('Inter')
    await userEvent.type(within(fields).getByLabelText('Dia de vencimento (1 a 31)'), '20')
    map['GET /api/cards'] = {
      body: [
        { id: 5, year: 2026, name: 'Nubank', dueDay: 10, active: true },
        { id: 9, year: 2026, name: 'Inter', dueDay: 20, active: true },
      ],
    }
    await userEvent.click(within(fields).getByRole('button', { name: 'Criar cartão' }))

    await waitFor(() => expect(within(dialog).getByLabelText('Cartão')).toHaveValue('9'))
    expect(within(dialog).queryByRole('group', { name: 'Novo cartão' })).not.toBeInTheDocument()
    expect(calls.find((c) => c.key === 'POST /api/cards')?.body).toEqual({ name: 'Inter', dueDay: 20, brand: 'inter' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(posted(calls)).toHaveLength(1))
    expect(posted(calls)[0]).toMatchObject({ type: 'CARD', paymentMethod: 'CREDIT', cardId: 9 })
  })

  it('does not offer automatic installments when the permission is off', async () => {
    const { dialog } = await openForm(replies(false))
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    const mode = within(dialog).getByLabelText('Como lançar')
    expect(within(mode).queryByRole('option', { name: 'Parcelas automáticas' })).not.toBeInTheDocument()
    expect(within(mode).getByRole('option', { name: 'Uma parcela já existente' })).toBeInTheDocument()
  })

  it('records one existing installment (7 of 12)', async () => {
    const { calls, dialog } = await openForm()
    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    await userEvent.selectOptions(within(dialog).getByLabelText('Como lançar'), 'manual')
    await userEvent.type(within(dialog).getByLabelText('Parcela atual'), '7')
    await userEvent.type(within(dialog).getByLabelText('Total de parcelas'), '12')
    await fillBasics(dialog, 'Sofá', '300')
    await userEvent.selectOptions(within(dialog).getByLabelText('Cartão'), 'Nubank')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(posted(calls)).toHaveLength(1))
    const body = posted(calls)[0] as Record<string, unknown>
    expect(body).toMatchObject({ amountCents: 30_000, installmentNumber: 7, installmentCount: 12 })
    expect(body).not.toHaveProperty('generateInstallments')
  })

  it('checks the form before calling the API', async () => {
    const { calls, dialog } = await openForm()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Informe um nome.')).toBeInTheDocument()
    expect(within(dialog).getByText('Informe um valor maior que zero, como 49,90.')).toBeInTheDocument()
    expect(within(dialog).getByText('Escolha uma categoria.')).toBeInTheDocument()

    await fillBasics(dialog, 'Algo', 'abc')
    fireEvent.change(within(dialog).getByLabelText('Data'), { target: { value: '2026-12-01' } })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Informe um valor maior que zero, como 49,90.')).toBeInTheDocument()
    expect(within(dialog).getByText('Escolha uma data dentro do mês 2026-11.')).toBeInTheDocument()

    await userEvent.selectOptions(within(dialog).getByLabelText('Tipo'), 'CARD')
    await userEvent.selectOptions(within(dialog).getByLabelText('Como lançar'), 'auto')
    await userEvent.type(within(dialog).getByLabelText('Número de parcelas'), '1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Informe de 2 a 120 parcelas.')).toBeInTheDocument()
    expect(posted(calls)).toHaveLength(0)
  })

  it('shows the API error in the user\'s language', async () => {
    const { dialog } = await openForm({ ...replies(), [POST]: { status: 400, body: { code: 'INVALID_EXPENSE', fields: {} } } })
    await fillBasics(dialog)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Essa combinação não é permitida')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('is translated to English', async () => {
    localStorage.setItem('nize.language', 'en')
    const { setLanguage } = await import('../i18n')
    setLanguage('en')
    stubApi(replies())
    renderApp('/month/2026-11', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'New expense' }))
    const dialog = await screen.findByRole('dialog', { name: 'New expense' })
    expect(within(dialog).getByLabelText('Amount')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Save and add another' })).toBeInTheDocument()
  })
})
