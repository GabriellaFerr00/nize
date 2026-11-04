/**
 * Display helpers for the month screen. They only format what the API sends back (dates, the percentage it
 * computed, month names); no money rule lives in the front (D-1).
 */

const locale = (language: string) => (language === 'pt' ? 'pt-BR' : 'en-US')

/** "novembro de 2026" / "November 2026". */
export function formatMonth(yyyyMm: string, language: string): string {
  return new Intl.DateTimeFormat(locale(language), { month: 'long', year: 'numeric' }).format(
    new Date(Number(yyyyMm.slice(0, 4)), Number(yyyyMm.slice(5)) - 1, 1),
  )
}

/** Month names in the interface language, January first. */
export function monthNames(language: string): string[] {
  const format = new Intl.DateTimeFormat(locale(language), { month: 'long' })
  return Array.from({ length: 12 }, (_, i) => format.format(new Date(2000, i, 1)))
}

/** "15/11" / "11/15" from an ISO date (`YYYY-MM-DD`), without time zone shifts. */
export function formatDay(isoDate: string, language: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat(locale(language), { day: '2-digit', month: '2-digit' }).format(
    new Date(year, month - 1, day),
  )
}

/** The percentage exactly as the API rounded it (2 decimals), e.g. "23,86%". */
export function formatPercent(percent: number, language: string): string {
  return new Intl.NumberFormat(locale(language), { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(percent) + '%'
}
