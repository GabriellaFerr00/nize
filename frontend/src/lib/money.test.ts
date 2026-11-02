import { describe, expect, it } from 'vitest'
import { formatCents, parseCents } from './money'

describe('parseCents: what the user types becomes whole cents (NFR-001)', () => {
  it.each([
    ['100', 10_000],
    ['100,00', 10_000],
    ['1.234,56', 123_456],
    ['1234,56', 123_456],
    ['0,5', 50],
    ['0,05', 5],
    ['R$ 50,00', 5_000],
    ['  47,5 ', 4_750],
    ['10.50', 1_050],
    ['10.5', 1_050],
    ['1.000', 100_000],
    ['1.000.000,99', 100_000_099],
  ])('%s → %i', (typed, cents) => {
    expect(parseCents(typed)).toBe(cents)
  })

  it.each(['', '   ', 'abc', '0', '0,00', '-5', '1,234', '1,2,3', '12,345', '1..000', ',5', '10,', '1e3', '99999999999999999'])(
    'rejects "%s"',
    (typed) => {
      expect(parseCents(typed)).toBeNull()
    },
  )

  it('never produces a fractional number of cents', () => {
    for (const typed of ['0,29', '19,99', '33,33', '0,07', '1.005,05']) {
      expect(Number.isInteger(parseCents(typed))).toBe(true)
    }
    expect(parseCents('0,29')).toBe(29) // 0.29 * 100 would be 28.999999999999996 in floating point
  })
})

describe('formatCents: integer cents for display only', () => {
  it('formats in Portuguese and English', () => {
    expect(formatCents(123_456, 'pt')).toBe('1.234,56')
    expect(formatCents(123_456, 'en')).toBe('1,234.56')
    expect(formatCents(5, 'pt')).toBe('0,05')
    expect(formatCents(100, 'pt')).toBe('1,00')
    expect(formatCents(0, 'pt')).toBe('0,00')
  })

  it('keeps the sign and handles huge values exactly', () => {
    expect(formatCents(-30_000, 'pt')).toBe('-300,00')
    expect(formatCents(9_000_000_000_001, 'en')).toBe('90,000,000,000.01')
  })
})
