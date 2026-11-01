import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { getStoredToken } from '../api/session'
import { renderApp, stubApi } from '../test/renderApp'

const LOGIN_OK = { status: 200, body: { token: 'jwt-123', expiresInSeconds: 3600 } }

describe('route guard and session', () => {
  it('sends a signed-out visitor to /sign-in', async () => {
    const router = renderApp('/categories')
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/sign-in')
  })

  it('keeps a signed-in user out of /sign-in', async () => {
    const router = renderApp('/sign-in', { signedIn: true })
    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/month\//))
  })

  it('treats an expired stored session as signed out', async () => {
    localStorage.setItem('nize.auth', JSON.stringify({ token: 'old', expiresAt: Date.now() - 1000 }))
    const router = renderApp('/categories')
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/sign-in')
    expect(localStorage.getItem('nize.auth')).toBeNull()
  })

  it('signs out: clears the token and returns to /sign-in', async () => {
    const router = renderApp('/categories', { signedIn: true })
    await userEvent.click(screen.getAllByRole('button', { name: 'Sair' })[0])
    await waitFor(() => expect(router.state.location.pathname).toBe('/sign-in'))
    expect(getStoredToken()).toBeNull()
  })
})

describe('sign in', () => {
  it('logs in, stores the token and enters the app', async () => {
    const calls = stubApi({ 'POST /api/auth/login': LOGIN_OK })
    const router = renderApp('/sign-in')

    await userEvent.type(await screen.findByLabelText('E-mail'), ' ana@example.com ')
    await userEvent.type(screen.getByLabelText('Senha'), 'S3cret-pass!')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/month\//))
    expect(calls[0].body).toEqual({ email: 'ana@example.com', password: 'S3cret-pass!' })
    expect(getStoredToken()).toBe('jwt-123')
  })

  it('goes back to the page the visitor wanted', async () => {
    stubApi({ 'POST /api/auth/login': LOGIN_OK })
    const router = renderApp('/people')
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'S3cret-pass!')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/people'))
  })

  it('shows the same translated message for wrong credentials and stores nothing', async () => {
    stubApi({ 'POST /api/auth/login': { status: 401, body: { code: 'INVALID_CREDENTIALS', fields: {} } } })
    renderApp('/sign-in')
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha incorretos.')
    expect(getStoredToken()).toBeNull()
  })

  it('validates empty fields without calling the API', async () => {
    const calls = stubApi({})
    renderApp('/sign-in')
    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }))
    expect(screen.getByText('Informe um e-mail válido.')).toBeInTheDocument()
    expect(screen.getByText('Informe sua senha.')).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })
})

describe('sign up', () => {
  it('creates the account, signs in and enters the app', async () => {
    const calls = stubApi({
      'POST /api/auth/register': { status: 201, body: { id: 1, name: 'Ana', email: 'ana@example.com' } },
      'POST /api/auth/login': LOGIN_OK,
    })
    const router = renderApp('/sign-up')
    await userEvent.type(await screen.findByLabelText('Nome'), 'Ana');
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'S3cret-pass!')
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }))

    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/month\//))
    expect(calls.map((c) => c.key).filter((key) => key.includes('/auth/'))).toEqual([
      'POST /api/auth/register',
      'POST /api/auth/login',
    ])
    expect(getStoredToken()).toBe('jwt-123')
  })

  it('rejects a short password on the client', async () => {
    const calls = stubApi({})
    renderApp('/sign-up')
    await userEvent.type(await screen.findByLabelText('Nome'), 'Ana')
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(screen.getByText('A senha precisa ter pelo menos 8 caracteres.')).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it('shows a clear message when the email is already registered', async () => {
    stubApi({ 'POST /api/auth/register': { status: 409, body: { code: 'EMAIL_ALREADY_REGISTERED', fields: {} } } })
    renderApp('/sign-up')
    await userEvent.type(await screen.findByLabelText('Nome'), 'Ana')
    await userEvent.type(screen.getByLabelText('E-mail'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'S3cret-pass!')
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Este e-mail já está cadastrado.')
    expect(getStoredToken()).toBeNull()
  })
})

describe('password recovery', () => {
  it('shows the same confirmation whatever the email', async () => {
    const calls = stubApi({ 'POST /api/auth/forgot-password': { status: 202 } })
    renderApp('/forgot-password')
    await userEvent.type(await screen.findByLabelText('E-mail'), 'anyone@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Se este e-mail tiver uma conta')
    expect(calls[0].body).toEqual({ email: 'anyone@example.com' })
  })

  it('resets the password using the token from the link, then asks to sign in', async () => {
    const calls = stubApi({ 'POST /api/auth/reset-password': { status: 204 } })
    const router = renderApp('/reset-password?token=abc123')
    await userEvent.type(await screen.findByLabelText('Nova senha'), 'New-password-2')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/sign-in'))
    expect(calls[0].body).toEqual({ token: 'abc123', newPassword: 'New-password-2' })
    expect(await screen.findByRole('status')).toHaveTextContent('Senha alterada')
  })

  it('shows the translated error for an invalid or expired token', async () => {
    stubApi({ 'POST /api/auth/reset-password': { status: 400, body: { code: 'INVALID_RESET_TOKEN', fields: {} } } })
    renderApp('/reset-password?token=expired')
    await userEvent.type(await screen.findByLabelText('Nova senha'), 'New-password-2')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('inválido ou expirou')
  })

  it('without a token offers to request a new link', async () => {
    renderApp('/reset-password')
    expect(await screen.findByRole('link', { name: 'Pedir um novo link' })).toBeInTheDocument()
  })
})

describe('language', () => {
  it('translates the sign-in screen and its errors to English', async () => {
    stubApi({ 'POST /api/auth/login': { status: 401, body: { code: 'INVALID_CREDENTIALS', fields: {} } } })
    renderApp('/sign-in')
    await userEvent.selectOptions(await screen.findByLabelText('Idioma'), 'en')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.')
  })
})
