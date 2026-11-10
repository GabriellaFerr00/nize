import { describe, expect, it } from 'vitest'
import { CARD_BRANDS, cardBrand, searchBrands } from './cardBrands'
import { isHexColor, TONE_GRID } from './tagColors'

describe('card brands (REQ-052)', () => {
  it('lists the cards used in Brazil with unique keys', () => {
    const keys = CARD_BRANDS.map((b) => b.key)
    expect(new Set(keys).size).toBe(keys.length)
    expect(keys).toEqual(expect.arrayContaining(['nubank', 'itau', 'bradesco', 'santander', 'bb', 'caixa', 'inter', 'c6']))
    expect(CARD_BRANDS.every((b) => isHexColor(b.color) && isHexColor(b.text))).toBe(true)
  })

  it('finds a brand ignoring case and accents', () => {
    expect(searchBrands('itau').map((b) => b.key)).toEqual(['itau'])
    expect(searchBrands('BANCO').map((b) => b.key)).toContain('bb')
    expect(searchBrands('  ')).toHaveLength(CARD_BRANDS.length)
    expect(searchBrands('zzz')).toEqual([])
  })

  it('knows a key and ignores the rest', () => {
    expect(cardBrand('nubank')?.name).toBe('Nubank')
    expect(cardBrand('banco-x')).toBeUndefined()
    expect(cardBrand(null)).toBeUndefined()
  })
})

describe('palette of colors and tones (REQ-052)', () => {
  it('has 12 hues × 6 tones, all distinct #rrggbb', () => {
    expect(TONE_GRID).toHaveLength(12)
    const all = TONE_GRID.flat()
    expect(all).toHaveLength(72)
    expect(all.every(isHexColor)).toBe(true)
    expect(new Set(all).size).toBe(72)
    expect(TONE_GRID[0][0]).toBe('#6c1313') // red, darkest tone
  })
})
