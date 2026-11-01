import { apiRequest } from './client'

export type LoginResponse = { token: string; expiresInSeconds: number }

export const register = (body: { name: string; email: string; password: string }) =>
  apiRequest('/auth/register', { method: 'POST', body, auth: false })

export const login = (body: { email: string; password: string }) =>
  apiRequest<LoginResponse>('/auth/login', { method: 'POST', body, auth: false })

export const forgotPassword = (email: string) =>
  apiRequest('/auth/forgot-password', { method: 'POST', body: { email }, auth: false })

export const resetPassword = (token: string, newPassword: string) =>
  apiRequest('/auth/reset-password', { method: 'POST', body: { token, newPassword }, auth: false })
