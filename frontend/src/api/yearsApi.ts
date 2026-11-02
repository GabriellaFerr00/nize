import { apiRequest } from './client'

export type YearInfo = { year: number; current: boolean }
export type Settings = { currentYear: number; autoInstallments: boolean }

export const listYears = () => apiRequest<YearInfo[]>('/years')
export const createYear = (year: number) => apiRequest<{ year: number }>(`/years/${year}`, { method: 'POST' })

export const getSettings = () => apiRequest<Settings>('/settings')
export const updateSettings = (settings: Settings) =>
  apiRequest<Settings>('/settings', { method: 'PUT', body: settings })
