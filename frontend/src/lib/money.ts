/**
 * Money helpers. Money is always a whole number of cents (NFR-001). These only convert what the user types
 * and format what the API sends back; no money rule (totals, balances, percentages) lives in the front.
 */

const MAX_DIGITS = 13 // keeps cents below Number.MAX_SAFE_INTEGER

/**
 * Turns a typed amount into cents, or null when it is empty, zero, negative or malformed.
 * Accepts "1.234,56" (pt), "10.50" (a single dot with 1–2 digits is a decimal point) and an optional "R$".
 * Works on the digits as text, so there is no floating point.
 */
export function parseCents(typed: string): number | null {
  const text = typed.replace(/R\$/g, '').replace(/\s/g, '')
  let integer: string
  let fraction = ''
  if (/^\d{1,3}(\.\d{3})*(,\d{1,2})?$/.test(text) || /^\d+(,\d{1,2})?$/.test(text)) {
    ;[integer, fraction = ''] = text.split(',')
    integer = integer.replace(/\./g, '')
  } else if (/^\d+\.\d{1,2}$/.test(text)) {
    ;[integer, fraction] = text.split('.')
  } else {
    return null
  }
  if (integer.length > MAX_DIGITS) return null
  const cents = Number(integer) * 100 + Number(fraction.padEnd(2, '0'))
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null
}

/** Cents as an editable amount ("1234,56"): no thousands separator, so `parseCents` reads it back in any language. */
export function centsToInput(cents: number): string {
  return formatCents(cents, 'pt').replace(/\./g, '')
}

/** Formats cents for display ("1.234,56"), using integers only. */
export function formatCents(cents: number, language: string): string {
  const negative = cents < 0
  const absolute = BigInt(Math.abs(cents))
  const locale = language === 'pt' ? 'pt-BR' : 'en-US'
  const whole = new Intl.NumberFormat(locale).format(absolute / 100n)
  const decimal = language === 'pt' ? ',' : '.'
  const fraction = String(absolute % 100n).padStart(2, '0')
  return `${negative ? '-' : ''}${whole}${decimal}${fraction}`
}
