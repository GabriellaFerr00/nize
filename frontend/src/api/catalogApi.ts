import { apiImageDataUrl, apiRequest, apiUpload } from './client'

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
  /** Palette key of a category or person (REQ-052). */
  color?: string
  /** A card has the user's own image (REQ-052). */
  hasLogo?: boolean
}

export type CatalogInput = {
  name: string
  description?: string
  dueDay?: number
  monthlyLimitCents?: number
  color?: string
}

export const listItems = (kind: CatalogKind, year: number) =>
  apiRequest<CatalogItem[]>(`/years/${year}/${kind}`)

export const createItem = (kind: CatalogKind, year: number, input: CatalogInput) =>
  apiRequest<CatalogItem>(`/years/${year}/${kind}`, { method: 'POST', body: input })

export const updateItem = (kind: CatalogKind, id: number, input: CatalogInput & { active: boolean }) =>
  apiRequest<CatalogItem>(`/${kind}/${id}`, { method: 'PUT', body: input })

/** 204, or 409 `CATEGORY_IN_USE` / `CARD_IN_USE` with the count of expenses that still use it (REQ-012, REQ-013). */
export const deleteItem = (kind: CatalogKind, id: number) => apiRequest(`/${kind}/${id}`, { method: 'DELETE' })

/** Card logo (REQ-052): PNG, JPEG or WebP up to 200 KB; the API checks the real format. */
export const MAX_LOGO_BYTES = 200 * 1024
export const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp']
export const uploadCardLogo = (id: number, file: Blob) => apiUpload(`/cards/${id}/logo`, file)
export const deleteCardLogo = (id: number) => apiRequest(`/cards/${id}/logo`, { method: 'DELETE' })
export const cardLogoDataUrl = (id: number) => apiImageDataUrl(`/cards/${id}/logo`)
