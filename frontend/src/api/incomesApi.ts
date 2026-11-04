import { apiRequest } from './client'

/** What the API accepts to create an income; with a person it is a reimbursement (REQ-024..026). */
export type NewIncome = {
  name: string
  amountCents: number
  date: string
  personId?: number
  installmentNumber?: number
  installmentCount?: number
}

/** The month and the installment data cannot change (REQ-023). */
export type IncomeEdit = { name: string; amountCents: number; date: string; personId?: number }

export type Income = {
  id: number
  month: string
  name: string
  date: string
  amountCents: number
  personId?: number
  reimbursement: boolean
  installmentNumber?: number
  installmentCount?: number
  /** "N/M", formatted by the API (REQ-021). */
  installmentLabel?: string
}

export const createIncome = (month: string, income: NewIncome) =>
  apiRequest<Income>(`/months/${month}/incomes`, { method: 'POST', body: income })

export const updateIncome = (id: number, income: IncomeEdit) =>
  apiRequest<Income>(`/incomes/${id}`, { method: 'PUT', body: income })

export const deleteIncome = (id: number) => apiRequest(`/incomes/${id}`, { method: 'DELETE' })
