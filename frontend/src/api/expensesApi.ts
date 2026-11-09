import { apiRequest } from './client'

export type ExpenseType = 'FIXED' | 'CARD'
export type PaymentMethod = 'PIX' | 'CREDIT'

/** What the API accepts to create an expense; money in whole cents (NFR-001). */
export type NewExpense = {
  name: string
  categoryId: number
  amountCents: number
  type: ExpenseType
  paymentMethod?: PaymentMethod
  cardId?: number
  personId?: number
  paid?: boolean
  date: string
  installmentNumber?: number
  installmentCount?: number
  generateInstallments?: boolean
  amountIsTotal?: boolean
}

export type Expense = {
  id: number
  month: string
  name: string
  categoryId: number
  amountCents: number
  type: ExpenseType
  paymentMethod: PaymentMethod
  cardId?: number
  personId?: number
  paid: boolean
  date: string
  installmentNumber?: number
  installmentCount?: number
  /** "N/M", formatted by the API (REQ-021). */
  installmentLabel?: string
  createdCount?: number
}

/** Full replacement of the editable fields (REQ-023); the month and the installment data cannot change. */
export type ExpenseEdit = {
  name: string
  categoryId: number
  amountCents: number
  type: ExpenseType
  paymentMethod: PaymentMethod
  cardId?: number
  personId?: number
  paid: boolean
  date: string
}

export const createExpense = (month: string, expense: NewExpense) =>
  apiRequest<Expense>(`/months/${month}/expenses`, { method: 'POST', body: expense })

export const updateExpense = (id: number, expense: ExpenseEdit) =>
  apiRequest<Expense>(`/expenses/${id}`, { method: 'PUT', body: expense })

/** Only the control mark changes; totals and positions stay the same (REQ-033). */
export const setExpensePaid = (id: number, paid: boolean) =>
  apiRequest<Expense>(`/expenses/${id}/paid`, { method: 'PATCH', body: { paid } })

/** What deleting an installment removes (REQ-050): only it, or it and the next ones of the same purchase. */
export type DeleteScope = 'this' | 'following'

export const deleteExpense = (id: number, scope: DeleteScope = 'this') =>
  apiRequest(scope === 'following' ? `/expenses/${id}?scope=following` : `/expenses/${id}`, { method: 'DELETE' })
