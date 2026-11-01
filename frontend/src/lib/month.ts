/** Current month as `YYYY-MM` in the browser's local time. Navigation only: no money rules live here. */
export function currentMonth(date: Date = new Date()): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${date.getFullYear()}-${month}`
}
