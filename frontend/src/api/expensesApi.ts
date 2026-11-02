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
  amountCents: number
  type: ExpenseType
  paymentMethod: PaymentMethod
  paid: boolean
  date: string
  installmentLabel?: string
  createdCount?: number
}

export const createExpense = (month: string, expense: NewExpense) =>
  apiRequest<Expense>(`/months/${month}/expenses`, { method: 'POST', body: expense })
