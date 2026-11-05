import { apiRequest } from './client'
import type { Expense } from './expensesApi'
import type { Income } from './incomesApi'

/** Totals of the month, all computed by the back-end (D-1); balances may be negative. */
export type MonthTotals = {
  totalExpensesCents: number
  totalFixedCents: number
  totalIncomeCents: number
  balanceCents: number
  thirdPartyExpensesCents: number
  reimbursementsCents: number
  ownBalanceCents: number
}

/**
 * What is left (positive) or missing (negative) in one interval between closings, from `fromDay` to `toDay`; each
 * interval counts only its own days and the last one ends on the month's last day (REQ-030).
 */
export type Position = { fromDay: number; toDay: number; balanceCents: number }

/** `cardId` null is the "no card" group (REQ-037). */
export type CardEntry = { cardId: number | null; cardName: string | null; amountCents: number }

export type MonthSummaries = {
  /** `percent` is the share of the total expenses with 2 decimals (e.g. 23.86), computed by the API. */
  byCategory: { categoryId: number; categoryName: string | null; amountCents: number; percent: number }[]
  byPayment: { pixCents: number; creditCents: number }
  byCard: CardEntry[]
  byPerson: { personId: number; personName: string | null; amountCents: number; byCard: CardEntry[] }[]
}

/** Everything the month screen shows (REQ-041), as `GET /api/months/{month}` returns it. */
export type MonthView = {
  month: string
  totals: MonthTotals
  closingsEnabled: boolean
  positions: Position[]
  summaries: MonthSummaries
  expenses: Expense[]
  incomes: Income[]
}

/** Opens the month: the first open runs the month load on the server (REQ-022). */
export const getMonth = (month: string) => apiRequest<MonthView>(`/months/${month}`)
