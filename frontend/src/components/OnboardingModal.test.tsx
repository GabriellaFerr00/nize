import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp, stubApi } from '../test/renderApp'

const me = (onboardingSeen: boolean, name = 'Ana') => ({
  body: { id: 1, name, email: 'ana@example.com', onboardingSeen },
})

describe('onboarding (REQ-005)', () => {
  it('shows on the first entry and can be dismissed, telling the API', async () => {
    const calls = stubApi({
      'GET /api/me': me(false),
      'POST /api/me/onboarding-seen': { status: 204 },
    })
    renderApp('/settings', { signedIn: true })

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Bem-vinda ao Nize, Ana!')
    await userEvent.click(screen.getByRole('button', { name: 'Entendi' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(calls.map((c) => c.key)).toContain('POST /api/me/onboarding-seen')
    expect(calls.find((c) => c.key === 'POST /api/me/onboarding-seen')?.headers.Authorization).toBe(
      'Bearer test-token',
    )
  })

  it('can be dismissed with Escape', async () => {
    stubApi({ 'GET /api/me': me(false), 'POST /api/me/onboarding-seen': { status: 204 } })
    renderApp('/settings', { signedIn: true })
    await screen.findByRole('dialog')
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('does not show when the user has already seen it', async () => {
    stubApi({ 'GET /api/me': me(true) })
    renderApp('/settings', { signedIn: true })
    expect(await screen.findByRole('heading', { name: 'Configurações' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('does not come back when moving between screens', async () => {
    stubApi({ 'GET /api/me': me(false), 'POST /api/me/onboarding-seen': { status: 204 } })
    renderApp('/settings', { signedIn: true })
    await userEvent.click(await screen.findByRole('button', { name: 'Entendi' }))
    await userEvent.click(screen.getAllByRole('link', { name: 'Cartões' })[0])
    expect(await screen.findByRole('heading', { name: 'Cartões' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('translates to English', async () => {
    stubApi({ 'GET /api/me': me(false) })
    localStorage.setItem('nize.language', 'en')
    const { setLanguage } = await import('../i18n')
    setLanguage('en')
    renderApp('/settings', { signedIn: true })
    expect(await screen.findByRole('dialog')).toHaveTextContent('Welcome to Nize, Ana!')
    expect(screen.getByRole('button', { name: 'Got it' })).toBeInTheDocument()
  })

  it("does not leak the previous user's cached profile after signing out and in as someone else", async () => {
    const replies: Parameters<typeof stubApi>[0] = {
      'GET /api/me': me(true, 'Ana'),
      'POST /api/auth/login': { body: { token: 'jwt-b', expiresInSeconds: 3600 } },
    }
    stubApi(replies)
    renderApp('/settings', { signedIn: true })
    await screen.findByRole('heading', { name: 'Configurações' })

    // another person (who has not seen the onboarding) signs in on the same browser
    replies['GET /api/me'] = me(false, 'Bia')
    await userEvent.click(screen.getAllByRole('button', { name: 'Sair' })[0])
    await userEvent.type(await screen.findByLabelText('E-mail'), 'bia@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'S3cret-pass!')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('dialog')).toHaveTextContent('Bem-vinda ao Nize, Bia!')
  })
})
