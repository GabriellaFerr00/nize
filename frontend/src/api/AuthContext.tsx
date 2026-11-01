import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AuthContext } from './authState'
import { clearSession, getStoredToken, storeSession, UNAUTHORIZED_EVENT } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<string | null>(() => getStoredToken())

  const signIn = useCallback((newToken: string, expiresInSeconds: number) => {
    queryClient.clear() // never show the previous user's cached data
    storeSession(newToken, expiresInSeconds)
    setToken(newToken)
  }, [queryClient])

  const signOut = useCallback(() => {
    clearSession()
    queryClient.clear()
    setToken(null)
  }, [queryClient])

  useEffect(() => {
    const onUnauthorized = () => {
      queryClient.clear()
      setToken(null)
    }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [queryClient])

  const value = useMemo(
    () => ({ isAuthenticated: token !== null, signIn, signOut }),
    [token, signIn, signOut],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
