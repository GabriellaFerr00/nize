/** Browser session storage (D-12): the JWT and its expiry live in localStorage under one key. */
const KEY = 'nize.auth'
export const UNAUTHORIZED_EVENT = 'nize:unauthorized'

type StoredSession = { token: string; expiresAt: number }

export function getStoredToken(): string | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const stored = JSON.parse(raw) as Partial<StoredSession>
    if (typeof stored.token !== 'string' || typeof stored.expiresAt !== 'number') return null
    if (stored.expiresAt <= Date.now()) {
      clearSession()
      return null
    }
    return stored.token
  } catch {
    return null
  }
}

export function storeSession(token: string, expiresInSeconds: number) {
  try {
    const session: StoredSession = { token, expiresAt: Date.now() + expiresInSeconds * 1000 }
    localStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    // storage unavailable: the session lasts only until the page is reloaded
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // nothing to clear
  }
}

/** Called when the API rejects a token we sent: drop it and tell the app. */
export function notifyUnauthorized() {
  clearSession()
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}
