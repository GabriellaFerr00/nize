import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyTheme, setTheme, storedTheme } from './theme'

function deviceIsDark(dark: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: dark && query.includes('dark') }))
}

afterEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
  vi.unstubAllGlobals()
})

describe('theme (REQ-045, D-20)', () => {
  it('starts with the device theme while nothing was chosen', () => {
    deviceIsDark(true)
    expect(storedTheme()).toBe('dark')
    deviceIsDark(false)
    expect(storedTheme()).toBe('light')
  })

  it('falls back to light when the device theme is unknown', () => {
    vi.stubGlobal('matchMedia', undefined)
    expect(storedTheme()).toBe('light')
  })

  it('always puts the theme on <html>', () => {
    applyTheme('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('applies and remembers the choice, which wins over the device', () => {
    deviceIsDark(true)
    setTheme('light')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('nize.theme')).toBe('light')
    expect(storedTheme()).toBe('light')
  })

  it('ignores an unknown or old stored value', () => {
    deviceIsDark(false)
    localStorage.setItem('nize.theme', 'system')
    expect(storedTheme()).toBe('light')
  })
})
