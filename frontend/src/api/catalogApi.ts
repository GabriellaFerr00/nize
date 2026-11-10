import { apiRequest } from './client'

/** The three catalogs (no year, D-22) share one shape on the wire. */
export type CatalogKind = 'categories' | 'cards' | 'people'

export type CatalogItem = {
  id: number
  name: string
  active: boolean
  description?: string
  dueDay?: number
  /** Optional monthly limit of a category (REQ-051). */
  monthlyLimitCents?: number
  /** Palette key of a category or person (REQ-052). */
  color?: string
  /** Key of a Brazilian card brand (lib/cardBrands), null when none (REQ-052). */
  brand?: string | null
}

export type CatalogInput = {
  name: string
  description?: string
  dueDay?: number
  monthlyLimitCents?: number
  color?: string
  brand?: string
}

/** One list per user, the same in every month and year (D-22). */
export const listItems = (kind: CatalogKind) => apiRequest<CatalogItem[]>(`/${kind}`)

export const createItem = (kind: CatalogKind, input: CatalogInput) =>
  apiRequest<CatalogItem>(`/${kind}`, { method: 'POST', body: input })

export const updateItem = (kind: CatalogKind, id: number, input: CatalogInput & { active: boolean }) =>
  apiRequest<CatalogItem>(`/${kind}/${id}`, { method: 'PUT', body: input })

/** 204, or 409 `CATEGORY_IN_USE` / `CARD_IN_USE` with the count of expenses that still use it (REQ-012, REQ-013). */
export const deleteItem = (kind: CatalogKind, id: number) => apiRequest(`/${kind}/${id}`, { method: 'DELETE' })
