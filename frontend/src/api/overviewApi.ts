import { apiRequest } from './client'

/** One month of the annual overview; every value is computed by the API, in whole cents (REQ-039). */
export type OverviewMonth = {
  month: string
  totalExpensesCents: number
  totalIncomeCents: number
  /** Income minus expenses; may be negative. */
  balanceCents: number
}

/** The 12 months of a year, January first. */
export type Overview = { year: number; months: OverviewMonth[] }

export const getOverview = (year: number) => apiRequest<Overview>(`/years/${year}/overview`)
