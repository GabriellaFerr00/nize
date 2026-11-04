import { describe, expect, it } from 'vitest'
import { formatDay, formatMonth, formatPercent, monthNames } from './format'
import { centsToInput, parseCents } from './money'
import { currentMonth, defaultDate, defaultMonth, isMonth, shiftMonth } from './month'

describe('month navigation helpers (REQ-040)', () => {
  it.each([
    ['2026-11', -1, '2026-10'],
    ['2026-11', 1, '2026-12'],
    ['2026-12', 1, '2027-01'],
    ['2027-01', -1, '2026-12'],
    ['2026-05', 12, '2027-05'],
    ['2026-05', -17, '2024-12'],
  ])('%s %+i → %s', (month, delta, expected) => {
    expect(shiftMonth(month, delta)).toBe(expected)
  })

  it('opens today\'s calendar month inside the current year (REQ-006)', () => {
    const today = new Date(2026, 10, 4)
    expect(defaultMonth(2027, today)).toBe('2027-11')
    expect(defaultMonth(2026, today)).toBe('2026-11')
    expect(defaultMonth(undefined, today)).toBe('2026-11')
  })

  it('accepts only YYYY-MM with a real month', () => {
    expect(isMonth('2026-01')).toBe(true)
    expect(isMonth('2026-12')).toBe(true)
    for (const bad of ['2026-13', '2026-00', '2026-1', '26-01', '2026-01-01', '']) expect(isMonth(bad)).toBe(false)
  })

  it('starts a new entry today in the current month, otherwise on the first day', () => {
    const today = new Date(2026, 10, 4)
    expect(currentMonth(today)).toBe('2026-11')
    expect(defaultDate('2026-11', today)).toBe('2026-11-04')
    expect(defaultDate('2026-12', today)).toBe('2026-12-01')
  })
})

describe('display helpers', () => {
  it('formats months, days and the API percentage in each language', () => {
    expect(formatMonth('2026-03', 'pt')).toBe('março de 2026')
    expect(formatMonth('2026-03', 'en')).toBe('March 2026')
    expect(monthNames('pt')).toHaveLength(12)
    expect(monthNames('en')[0]).toBe('January')
    expect(formatDay('2026-11-05', 'pt')).toBe('05/11')
    expect(formatDay('2026-11-05', 'en')).toBe('11/05')
    expect(formatPercent(23.86, 'pt')).toBe('23,86%')
    expect(formatPercent(100, 'en')).toBe('100.00%')
  })

  it('turns cents into an editable amount that reads back to the same cents', () => {
    for (const cents of [1, 99, 100, 99_399, 123_456, 100_000_099]) {
      expect(parseCents(centsToInput(cents))).toBe(cents)
    }
    expect(centsToInput(123_456)).toBe('1234,56')
  })
})
