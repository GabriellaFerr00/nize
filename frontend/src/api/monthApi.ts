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
/** Sum of the expenses from fromDay to toDay (REQ-030). */
export type Position = { fromDay: number; toDay: number; totalExpensesCents: number }

/** `cardId` null is the "no card" group (REQ-037). */
/** Only cards: there is no "Sem cartão" group (REQ-037, 2026-11-09). */
/** How the month's spending of a category stands against its limit (REQ-051), as the API computed it. */
export type LimitStatus = 'UNDER' | 'NEAR' | 'OVER'

export type CardEntry = { cardId: number; cardName: string | null; amountCents: number }

export type MonthSummaries = {
  /** `percent` is the share of the total expenses with 2 decimals (e.g. 23.86), computed by the API. */
  /** limitCents and limitStatus only for a category with a monthly limit (REQ-051). */
  byCategory: {
    categoryId: number
    categoryName: string | null
    amountCents: number
    percent: number
    limitCents?: number
    limitStatus?: LimitStatus
  }[]
  byPayment: { pixCents: number; creditCents: number }
  byCard: CardEntry[]
  /** Per person: cards and Pix (`pixCents`) add up to `amountCents` (REQ-038). */
  byPerson: { personId: number; personName: string | null; amountCents: number; byCard: CardEntry[]; pixCents: number }[]
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

/** A category's limit in one month (REQ-051, D-22): `monthCents` null = the month uses `defaultCents`. */
export type MonthLimit = { categoryId: number; categoryName: string; defaultCents: number | null; monthCents: number | null }

export const getMonthLimits = (month: string) => apiRequest<MonthLimit[]>(`/months/${month}/category-limits`)

export const setMonthLimit = (month: string, categoryId: number, limitCents: number) =>
  apiRequest(`/months/${month}/category-limits/${categoryId}`, { method: 'PUT', body: { limitCents } })

/** Back to the category's default in that month. */
export const clearMonthLimit = (month: string, categoryId: number) =>
  apiRequest(`/months/${month}/category-limits/${categoryId}`, { method: 'DELETE' })
