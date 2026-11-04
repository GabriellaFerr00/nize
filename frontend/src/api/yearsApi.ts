import { apiRequest } from './client'

export type YearInfo = { year: number; current: boolean }
export type Settings = {
  currentYear: number
  autoInstallments: boolean
  /** REQ-048: positions on these days (1–31, ascending; past the end of the month = last day). */
  closingsEnabled: boolean
  closingDays: number[]
}

export const listYears = () => apiRequest<YearInfo[]>('/years')
export const createYear = (year: number) => apiRequest<{ year: number }>(`/years/${year}`, { method: 'POST' })

export const getSettings = () => apiRequest<Settings>('/settings')
export const updateSettings = (settings: Settings) =>
  apiRequest<Settings>('/settings', { method: 'PUT', body: settings })
