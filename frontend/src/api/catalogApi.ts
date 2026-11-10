import { apiRequest } from './client'

/** The three per-year catalogs share one shape on the wire. */
export type CatalogKind = 'categories' | 'cards' | 'people'

export type CatalogItem = {
  id: number
  year: number
  name: string
  active: boolean
  description?: string
  dueDay?: number
  /** Optional monthly limit of a category (REQ-051). */
  monthlyLimitCents?: number
}

export type CatalogInput = { name: string; description?: string; dueDay?: number; monthlyLimitCents?: number }

export const listItems = (kind: CatalogKind, year: number) =>
  apiRequest<CatalogItem[]>(`/years/${year}/${kind}`)

export const createItem = (kind: CatalogKind, year: number, input: CatalogInput) =>
  apiRequest<CatalogItem>(`/years/${year}/${kind}`, { method: 'POST', body: input })

export const updateItem = (kind: CatalogKind, id: number, input: CatalogInput & { active: boolean }) =>
  apiRequest<CatalogItem>(`/${kind}/${id}`, { method: 'PUT', body: input })

/** 204, or 409 `CATEGORY_IN_USE` / `CARD_IN_USE` with the count of expenses that still use it (REQ-012, REQ-013). */
export const deleteItem = (kind: CatalogKind, id: number) => apiRequest(`/${kind}/${id}`, { method: 'DELETE' })
