/** Current month as `YYYY-MM` in the browser's local time. Navigation only: no money rules live here. */
export function currentMonth(date: Date = new Date()): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${date.getFullYear()}-${month}`
}

/**
 * Month opened by default (REQ-006): today's calendar month inside the user's current year
 * (Nov 2026 with current year 2027 → `2027-11`). Without a current year, today's month.
 */
export function defaultMonth(currentYear?: number | null, today: Date = new Date()): string {
  const now = currentMonth(today)
  return currentYear ? `${String(currentYear).padStart(4, '0')}${now.slice(4)}` : now
}

/** Date a new entry starts with: today when `month` is the current month, otherwise its first day. */
export function defaultDate(month: string, today: Date = new Date()): string {
  return currentMonth(today) === month ? `${month}-${String(today.getDate()).padStart(2, '0')}` : `${month}-01`
}

/** True for a well-formed `YYYY-MM` (month 01–12). */
export function isMonth(value: string): boolean {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

/** The month `delta` months away from `yyyyMm` (navigation between months and years, REQ-040). */
export function shiftMonth(yyyyMm: string, delta: number): string {
  const index = Number(yyyyMm.slice(0, 4)) * 12 + Number(yyyyMm.slice(5)) - 1 + delta
  return `${String(Math.floor(index / 12)).padStart(4, '0')}-${String((index % 12) + 1).padStart(2, '0')}`
}
