import { useQuery, useQueryClient } from '@tanstack/react-query'
import { listItems, type CatalogItem } from '../api/catalogApi'
import { getMonth } from '../api/monthApi'
import { getSettings } from '../api/yearsApi'
import { defaultMonth } from './month'

/** Key prefix of every month query; saving an expense or income refreshes them all (installments reach later months). */
export const MONTH_KEY = 'month'

/** The month screen data, exactly as the API computed it (REQ-041). */
export function useMonth(month: string, enabled = true) {
  return useQuery({ queryKey: [MONTH_KEY, month], queryFn: () => getMonth(month), enabled })
}

/**
 * The month the app opens by default (REQ-006), from the saved current year. Until the settings arrive
 * (or if they fail) it is today's month; {@code isLoading} lets a redirect wait for them.
 */
export function useDefaultMonth() {
  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings })
  return { month: defaultMonth(settings.data?.currentYear), isLoading: settings.isLoading }
}

/** Refetches the open months after a change, so lists, totals and summaries reflect it at once. */
export function useRefreshMonths() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [MONTH_KEY] })
}

export type YearCatalogs = {
  categories: CatalogItem[]
  cards: CatalogItem[]
  people: CatalogItem[]
  /** Name of a category, card or person by id (inactive ones too: old entries keep showing them). */
  name: (kind: 'categories' | 'cards' | 'people', id: number | undefined | null) => string | undefined
}

/** Categories, cards and people of a year, shared (same cache keys) with the catalog screens and the forms. */
/** Categories, cards and people: one list each, the same in every month (D-22). */
export function useCatalogs(): YearCatalogs {
  const categories = useQuery({ queryKey: ['catalog', 'categories'], queryFn: () => listItems('categories') })
  const cards = useQuery({ queryKey: ['catalog', 'cards'], queryFn: () => listItems('cards') })
  const people = useQuery({ queryKey: ['catalog', 'people'], queryFn: () => listItems('people') })
  const lists = { categories: categories.data ?? [], cards: cards.data ?? [], people: people.data ?? [] }
  return {
    ...lists,
    name: (kind, id) => (id == null ? undefined : lists[kind].find((item) => item.id === id)?.name),
  }
}

/** Active items, plus the one an entry already uses even if inactive (the API accepts it, REQ-011/014). */
export function selectable(items: CatalogItem[], currentId?: number | null): CatalogItem[] {
  return items.filter((item) => item.active || item.id === currentId)
}
