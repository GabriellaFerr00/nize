import { screen, within } from '@testing-library/react'
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

  it('redirects / to the current month', () => {
    const router = renderAt('/')
    expect(router.state.location.pathname).toMatch(/^\/month\/\d{4}-\d{2}$/)
    expect(screen.getByRole('button', { name: 'Novo lançamento' })).toBeInTheDocument()
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
