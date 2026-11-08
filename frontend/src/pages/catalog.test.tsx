import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp, stubApi } from '../test/renderApp'

type Replies = Parameters<typeof stubApi>[0]

const YEARS = [
  { year: 2025, current: false },
  { year: 2026, current: true },
]
const baseReplies = (): Replies => ({
  'GET /api/me': { body: { id: 1, name: 'Ana', email: 'a@x.com', onboardingSeen: true } },
  'GET /api/settings': { body: { currentYear: 2026, autoInstallments: true, closingsEnabled: true, closingDays: [15, 31] } },
  'GET /api/years': { body: YEARS },
})

const category = (id: number, name: string, active = true, description = `Descrição de ${name}`) => ({
  id, year: 2026, name, description, active,
})

describe('categories screen (REQ-011, REQ-012)', () => {
  it('lists the categories of the current year, marking inactive ones', async () => {
    stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado'), category(2, 'Lazer', false)] },
    })
    renderApp('/categories', { signedIn: true })

    expect(await screen.findByText('Mercado')).toBeInTheDocument()
    expect(screen.getByText('Descrição de Mercado')).toBeInTheDocument()
    expect(screen.getByText('Lazer')).toBeInTheDocument()
    const inactive = screen.getByRole('region', { name: 'Inativas' })
    expect(within(inactive).getByText('Lazer')).toBeInTheDocument()
    expect(within(inactive).queryByText('Mercado')).not.toBeInTheDocument()
    expect(within(inactive).getByRole('button', { name: 'Reativar Lazer' })).toBeInTheDocument()
  })

  it('creates a category and shows it in the list', async () => {
    const replies: Replies = {
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'POST /api/years/2026/categories': { status: 201, body: category(2, 'Pets') },
    }
    const calls = stubApi(replies)
    renderApp('/categories', { signedIn: true })

    await userEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    const dialog = await screen.findByRole('dialog', { name: 'Nova categoria' })
    await userEvent.type(within(dialog).getByLabelText('Nome'), '  Pets ')
    replies['GET /api/years/2026/categories'] = { body: [category(1, 'Mercado'), category(2, 'Pets')] }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Pets')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(calls.find((c) => c.key === 'POST /api/years/2026/categories')?.body).toEqual({ name: 'Pets', description: '' })
  })

  it('blocks an empty name without calling the API and shows a repeated-name error from the API', async () => {
    const calls = stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'POST /api/years/2026/categories': { status: 409, body: { code: 'CATEGORY_NAME_TAKEN', fields: {} } },
    })
    renderApp('/categories', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    const dialog = await screen.findByRole('dialog')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(within(dialog).getByText('Informe um nome.')).toBeInTheDocument()
    expect(calls.some((c) => c.key.startsWith('POST'))).toBe(false)

    await userEvent.type(within(dialog).getByLabelText('Nome'), 'mercado')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Já existe uma categoria com esse nome neste ano.')
  })

  it('edits a category keeping its active flag', async () => {
    const calls = stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado', false)] },
      'PUT /api/categories/1': { body: category(1, 'Feira', false) },
    })
    renderApp('/categories', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Editar Mercado' }))
    const dialog = await screen.findByRole('dialog', { name: 'Editar categoria' })
    const name = within(dialog).getByLabelText('Nome')
    await userEvent.clear(name)
    await userEvent.type(name, 'Feira')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(calls.some((c) => c.key === 'PUT /api/categories/1')).toBe(true))
    expect(calls.find((c) => c.key === 'PUT /api/categories/1')?.body).toEqual({
      name: 'Feira', description: 'Descrição de Mercado', active: false,
    })
  })

  it('deactivates a category with one click', async () => {
    const calls = stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'PUT /api/categories/1': { body: category(1, 'Mercado', false) },
    })
    renderApp('/categories', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Desativar Mercado' }))

    await waitFor(() => expect(calls.some((c) => c.key === 'PUT /api/categories/1')).toBe(true))
    expect(calls.find((c) => c.key === 'PUT /api/categories/1')?.body).toMatchObject({ name: 'Mercado', active: false })
  })

  it('deletes an unused category after confirming', async () => {
    const replies: Replies = {
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'DELETE /api/categories/1': { status: 204 },
    }
    const calls = stubApi(replies)
    renderApp('/categories', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Excluir Mercado' }))
    const dialog = await screen.findByRole('dialog', { name: 'Excluir categoria' })
    replies['GET /api/years/2026/categories'] = { body: [] }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))

    await waitFor(() => expect(screen.queryByText('Mercado')).not.toBeInTheDocument())
    expect(calls.some((c) => c.key === 'DELETE /api/categories/1')).toBe(true)
    expect(screen.getByText('Nenhuma categoria neste ano.')).toBeInTheDocument()
  })

  it('asks what to do when the category has expenses, and keeps it', async () => {
    stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'DELETE /api/categories/1': { status: 409, body: { code: 'CATEGORY_IN_USE', fields: {}, count: 3 } },
    })
    const router = renderApp('/categories', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Excluir Mercado' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))

    const dialog = await screen.findByRole('dialog', { name: 'Categoria em uso' })
    expect(dialog).toHaveTextContent('tem 3 lançamentos e não pode ser excluída')
    expect(dialog).toHaveTextContent('Quer alterar esses lançamentos antes?')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Mercado')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Excluir Mercado' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Categoria em uso' })).getByRole('button', { name: 'Alterar lançamentos' }))
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/month\//))
  })

  it('uses singular for one expense', async () => {
    stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'DELETE /api/categories/1': { status: 409, body: { code: 'CATEGORY_IN_USE', fields: {}, count: 1 } },
    })
    renderApp('/categories', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Excluir Mercado' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))
    expect(await screen.findByRole('dialog', { name: 'Categoria em uso' })).toHaveTextContent('tem 1 lançamento e não')
  })

  it('switches to another year (older years stay editable)', async () => {
    const calls = stubApi({
      ...baseReplies(),
      'GET /api/years/2026/categories': { body: [category(1, 'Mercado')] },
      'GET /api/years/2025/categories': { body: [{ ...category(9, 'Antiga'), year: 2025 }] },
    })
    renderApp('/categories', { signedIn: true })
    await screen.findByText('Mercado')
    await userEvent.selectOptions(screen.getByLabelText('Ano'), '2025')

    expect(await screen.findByText('Antiga')).toBeInTheDocument()
    expect(calls.some((c) => c.key === 'GET /api/years/2025/categories')).toBe(true)
    expect(screen.getByRole('button', { name: 'Nova categoria' })).toBeEnabled()
  })
})

describe('cards screen (REQ-013)', () => {
  const card = (id: number, name: string, dueDay: number, active = true) => ({ id, year: 2026, name, dueDay, active })

  it('lists cards with their due day and explains the empty state', async () => {
    const replies: Replies = { ...baseReplies(), 'GET /api/years/2026/cards': { body: [] } }
    stubApi(replies)
    renderApp('/cards', { signedIn: true })
    expect(await screen.findByText(/Nenhum cartão neste ano/)).toBeInTheDocument()
  })

  it('shows the due day of each card', async () => {
    stubApi({ ...baseReplies(), 'GET /api/years/2026/cards': { body: [card(1, 'Nubank', 10)] } })
    renderApp('/cards', { signedIn: true })
    expect(await screen.findByText('Nubank')).toBeInTheDocument()
    expect(screen.getByText('Vence todo dia 10')).toBeInTheDocument()
  })

  it('rejects due days outside 1–31 on the client and accepts a valid one', async () => {
    const replies: Replies = {
      ...baseReplies(),
      'GET /api/years/2026/cards': { body: [] },
      'POST /api/years/2026/cards': { status: 201, body: card(1, 'Nubank', 10) },
    }
    const calls = stubApi(replies)
    renderApp('/cards', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Novo cartão' }))
    const dialog = await screen.findByRole('dialog', { name: 'Novo cartão' })
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Nubank')

    for (const invalid of ['0', '32', '']) {
      const day = within(dialog).getByLabelText('Dia de vencimento (1 a 31)')
      await userEvent.clear(day)
      if (invalid) await userEvent.type(day, invalid)
      await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))
      expect(within(dialog).getByText('Informe um dia de 1 a 31.')).toBeInTheDocument()
    }
    expect(calls.some((c) => c.key.startsWith('POST'))).toBe(false)

    const day = within(dialog).getByLabelText('Dia de vencimento (1 a 31)')
    await userEvent.clear(day)
    await userEvent.type(day, '10')
    replies['GET /api/years/2026/cards'] = { body: [card(1, 'Nubank', 10)] }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Vence todo dia 10')).toBeInTheDocument()
    expect(calls.find((c) => c.key === 'POST /api/years/2026/cards')?.body).toEqual({ name: 'Nubank', dueDay: 10 })
  })
})

describe('people screen (REQ-014)', () => {
  it('creates and deactivates a person', async () => {
    const replies: Replies = {
      ...baseReplies(),
      'GET /api/years/2026/people': { body: [] },
      'POST /api/years/2026/people': { status: 201, body: { id: 1, year: 2026, name: 'Mainha', active: true } },
      'PUT /api/people/1': { body: { id: 1, year: 2026, name: 'Mainha', active: false } },
    }
    const calls = stubApi(replies)
    renderApp('/people', { signedIn: true })

    expect(await screen.findByText(/Nenhuma pessoa neste ano/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Nova pessoa' }))
    const dialog = await screen.findByRole('dialog', { name: 'Nova pessoa' })
    await userEvent.type(within(dialog).getByLabelText('Nome'), 'Mainha')
    expect(within(dialog).queryByLabelText(/Dia de vencimento/)).not.toBeInTheDocument()
    replies['GET /api/years/2026/people'] = { body: [{ id: 1, year: 2026, name: 'Mainha', active: true }] }
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Mainha')).toBeInTheDocument()
    expect(calls.find((c) => c.key === 'POST /api/years/2026/people')?.body).toEqual({ name: 'Mainha' })
    expect(screen.queryByRole('button', { name: /^Excluir/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Desativar Mainha' }))
    await waitFor(() => expect(calls.some((c) => c.key === 'PUT /api/people/1')).toBe(true))
    expect(calls.find((c) => c.key === 'PUT /api/people/1')?.body).toEqual({ name: 'Mainha', active: false })
  })
})

describe('settings screen (REQ-006, REQ-007, REQ-009)', () => {
  it('shows the saved settings and saves changes', async () => {
    const calls = stubApi({ ...baseReplies(), 'PUT /api/settings': { body: { currentYear: 2025, autoInstallments: false, closingsEnabled: true, closingDays: [15, 31] } } })
    renderApp('/settings', { signedIn: true })

    const year = await screen.findByLabelText(/^Ano vigente/)
    await waitFor(() => expect(year).toHaveValue('2026'))
    const auto = screen.getByRole('checkbox', { name: /Parcelas automáticas/ })
    expect(auto).toBeChecked()

    await userEvent.selectOptions(year, '2025')
    await userEvent.click(auto)
    await userEvent.click(screen.getAllByRole('button', { name: 'Salvar' })[0])

    expect(await screen.findByText('Configurações salvas.')).toBeInTheDocument()
    expect(calls.find((c) => c.key === 'PUT /api/settings')?.body).toEqual({
      currentYear: 2025, autoInstallments: false, closingsEnabled: true, closingDays: [15, 31],
    })
  })

  it('shows the closing days and saves added, removed and turned-off closings (REQ-048)', async () => {
    const calls = stubApi({ ...baseReplies(), 'PUT /api/settings': { body: { currentYear: 2026, autoInstallments: true, closingsEnabled: false, closingDays: [5, 31] } } })
    renderApp('/settings', { signedIn: true })

    const closings = await screen.findByRole('checkbox', { name: /Fechamentos do mês/ })
    await waitFor(() => expect(closings).toBeChecked())
    const list = screen.getByRole('list', { name: 'Dias de fechamento' })
    expect(within(list).getByText('Dia 15')).toBeInTheDocument()
    expect(within(list).getByText('Último dia')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Novo dia de fechamento'), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar dia' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remover Dia 15' }))
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('Dia 5')).toBeInTheDocument()
    expect(within(items[1]).getByText('Último dia')).toBeInTheDocument()
    await userEvent.click(closings)
    await userEvent.click(screen.getAllByRole('button', { name: 'Salvar' })[0])

    expect(await screen.findByText('Configurações salvas.')).toBeInTheDocument()
    expect(calls.find((c) => c.key === 'PUT /api/settings')?.body).toEqual({
      currentYear: 2026, autoInstallments: true, closingsEnabled: false, closingDays: [5, 31],
    })
  })

  it('refuses a closing day outside 1 to 31 or already in the list', async () => {
    stubApi(baseReplies())
    renderApp('/settings', { signedIn: true })

    const day = await screen.findByLabelText('Novo dia de fechamento')
    await waitFor(() => expect(screen.getByText('Dia 15')).toBeInTheDocument())
    for (const value of ['0', '32', '15', '2.5']) {
      await userEvent.clear(day)
      await userEvent.type(day, value)
      await userEvent.click(screen.getByRole('button', { name: 'Adicionar dia' }))
      expect(screen.getByText('Informe um dia de 1 a 31 que ainda não esteja na lista.')).toBeInTheDocument()
    }
    expect(within(screen.getByRole('list', { name: 'Dias de fechamento' })).getAllByRole('listitem')).toHaveLength(2)
  })

  it('creates a new year, suggesting the one after the latest', async () => {
    const calls = stubApi({ ...baseReplies(), 'POST /api/years/2027': { status: 201, body: { year: 2027 } } })
    renderApp('/settings', { signedIn: true })

    const field = await screen.findByLabelText('Ano')
    await waitFor(() => expect(field).toHaveValue(2027))
    await userEvent.click(screen.getByRole('button', { name: 'Criar ano' }))

    expect(await screen.findByText('Ano 2027 criado.')).toBeInTheDocument()
    expect(calls.some((c) => c.key === 'POST /api/years/2027')).toBe(true)
  })

  it('shows a clear message when the year already exists', async () => {
    stubApi({ ...baseReplies(), 'POST /api/years/2026': { status: 409, body: { code: 'YEAR_ALREADY_EXISTS', fields: {} } } })
    renderApp('/settings', { signedIn: true })

    const field = await screen.findByLabelText('Ano')
    await waitFor(() => expect(field).toHaveValue(2027))
    await userEvent.clear(field)
    await userEvent.type(field, '2026')
    await userEvent.click(screen.getByRole('button', { name: 'Criar ano' }))

    expect(await screen.findByText('Esse ano já existe na sua conta.')).toBeInTheDocument()
  })
})

describe('language', () => {
  it('translates the catalog screens to English', async () => {
    stubApi({ ...baseReplies(), 'GET /api/years/2026/cards': { body: [{ id: 1, year: 2026, name: 'Nubank', dueDay: 10, active: true }] } })
    localStorage.setItem('nize.language', 'en')
    const { setLanguage } = await import('../i18n')
    setLanguage('en')
    renderApp('/cards', { signedIn: true })

    expect(await screen.findByText('Due every day 10')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New card' })).toBeInTheDocument()
  })
})
