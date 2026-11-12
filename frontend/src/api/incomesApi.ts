import { apiRequest } from './client'
import type { DeleteScope } from './expensesApi'

/** What the API accepts to create an income; with a person it is a reimbursement (REQ-024..026). */
export type NewIncome = {
  name: string
  amountCents: number
  date: string
  personId?: number
  installmentNumber?: number
  installmentCount?: number
  /** Copied to the next months by the month load (REQ-053); never with installments. */
  fixed?: boolean
}

/** The month and the installment data cannot change (REQ-023). */
export type IncomeEdit = { name: string; amountCents: number; date: string; personId?: number; fixed: boolean }

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
  /** A fixed income repeats in the next months (REQ-053). */
  fixed: boolean
}

export const createIncome = (month: string, income: NewIncome) =>
  apiRequest<Income>(`/months/${month}/incomes`, { method: 'POST', body: income })

export const updateIncome = (id: number, income: IncomeEdit) =>
  apiRequest<Income>(`/incomes/${id}`, { method: 'PUT', body: income })

export const deleteIncome = (id: number, scope: DeleteScope = 'this') =>
  apiRequest(scope === 'following' ? `/incomes/${id}?scope=following` : `/incomes/${id}`, { method: 'DELETE' })
