import { apiRequest } from './client'

export type Me = { id: number; name: string; email: string; onboardingSeen: boolean }

export const fetchMe = () => apiRequest<Me>('/me')

export const markOnboardingSeen = () => apiRequest('/me/onboarding-seen', { method: 'POST' })
