import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { getSettings, listYears } from '../api/yearsApi'

/**
 * The year a catalog screen works on: `?year=` when present and valid, otherwise the user's current year
 * (REQ-006). Older years stay fully editable (REQ-008).
 */
export function useSelectedYear() {
  const [params, setParams] = useSearchParams()
  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings })
  const years = useQuery({ queryKey: ['years'], queryFn: listYears })

  const requested = Number(params.get('year'))
  const available = years.data?.map((y) => y.year) ?? []
  const year = available.includes(requested) ? requested : (settings.data?.currentYear ?? null)

  const setYear = (next: number) => setParams({ year: String(next) }, { replace: true })
  return { year, years: years.data ?? [], setYear, isLoading: settings.isLoading || years.isLoading }
}
