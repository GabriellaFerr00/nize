import { createContext, useContext } from 'react'

export type AuthState = {
  isAuthenticated: boolean
  signIn: (token: string, expiresInSeconds: number) => void
  signOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
