import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp, stubApi } from '../test/renderApp'

function renderAt(path: string) {
  stubApi({ 'GET /api/me': { body: { id: 1, name: 'Ana', email: 'a@x.com', onboardingSeen: true } } })
  return renderApp(path, { signedIn: true })
}

describe('app shell', () => {
  it('opens in Portuguese by default (REQ-046)', () => {
    renderAt('/categories')
    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(within(screen.getByTestId('side-nav')).getByText('Mês')).toBeInTheDocument()
  })

  it('switches the whole interface to English and remembers the choice', async () => {
    renderAt('/categories')
    await userEvent.selectOptions(screen.getAllByLabelText('Idioma')[0], 'en')

    expect(screen.getByRole('heading', { name: 'Categories' })).toBeInTheDocument()
    expect(within(screen.getByTestId('side-nav')).getByText('Month')).toBeInTheDocument()
    expect(localStorage.getItem('nize.language')).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it('switches the theme right away and remembers it (REQ-045)', async () => {
    renderAt('/categories')
    const toggle = screen.getAllByRole('button', { name: 'Tema escuro' })[0]
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-pressed', 'true')
    // the phone header toggle follows the same choice
    expect(screen.getAllByRole('button', { name: 'Tema escuro' })[1]).toHaveAttribute('aria-pressed', 'true')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('nize.theme')).toBe('dark')

    await userEvent.click(toggle)
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('nize.theme')).toBe('light')
    delete document.documentElement.dataset.theme
  })

  it('has a skip link to the content and the logo with the app name', () => {
    renderAt('/categories')
    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute('href', '#content')
    expect(document.getElementById('content')?.tagName).toBe('MAIN')
    expect(screen.getAllByRole('img', { name: 'Nize' })[0]).toHaveAttribute('src', '/logoPadrao/nize-logo-n-bloco-verde-1000.png')
  })

  it('redirects / to the current month', async () => {
    const router = renderAt('/')
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/month\/\d{4}-\d{2}$/))
    expect(await screen.findByRole('button', { name: 'Novo lançamento' })).toBeInTheDocument()
  })

  it('opens / and the menu in the current year from the settings (REQ-006)', async () => {
    const thisMonth = new Date().getMonth() + 1
    const expected = `2031-${String(thisMonth).padStart(2, '0')}`
    stubApi({
      'GET /api/me': { body: { id: 1, name: 'Ana', email: 'a@x.com', onboardingSeen: true } },
      'GET /api/settings': { body: { currentYear: 2031, autoInstallments: true, closingsEnabled: true, closingDays: [15, 31] } },
    })
    const router = renderApp('/', { signedIn: true })

    await waitFor(() => expect(router.state.location.pathname).toBe(`/month/${expected}`))
    const links = within(screen.getByTestId('side-nav')).getAllByRole('link')
    expect(links[0]).toHaveAttribute('href', `/month/${expected}`)
    expect(links[1]).toHaveAttribute('href', '/overview/2031')
  })

  it('links every menu entry in both menus', () => {
    renderAt('/settings')
    for (const testId of ['side-nav', 'bottom-nav']) {
      const links = within(screen.getByTestId(testId)).getAllByRole('link')
      expect(links.map((link) => link.getAttribute('href'))).toEqual([
        expect.stringMatching(/^\/month\/\d{4}-\d{2}$/),
        expect.stringMatching(/^\/overview\/\d{4}$/),
        '/categories',
        '/cards',
        '/people',
        '/settings',
      ])
    }
  })

  it('shows the side menu from md up and the bottom bar below it (REQ-043)', () => {
    renderAt('/settings')
    const side = screen.getByTestId('side-nav').closest('aside')!
    expect(side).toHaveClass('hidden', 'md:flex')
    expect(screen.getByTestId('bottom-nav')).toHaveClass('md:hidden')
  })

  it('shows a not-found page for unknown routes', () => {
    renderAt('/nope')
    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument()
  })
})
