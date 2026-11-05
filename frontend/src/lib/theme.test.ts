import { afterEach, describe, expect, it } from 'vitest'
import { applyTheme, setTheme, storedTheme } from './theme'

afterEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
})

describe('theme (REQ-045, D-20)', () => {
  it('follows the system when nothing was chosen', () => {
    expect(storedTheme()).toBe('system')
    applyTheme(storedTheme())
    expect(document.documentElement.dataset.theme).toBeUndefined()
  })

  it('applies and remembers light or dark', () => {
    setTheme('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(storedTheme()).toBe('dark')

    setTheme('light')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('nize.theme')).toBe('light')
  })

  it('going back to system forgets the choice', () => {
    setTheme('dark')
    setTheme('system')
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(localStorage.getItem('nize.theme')).toBeNull()
  })

  it('ignores an unknown stored value', () => {
    localStorage.setItem('nize.theme', 'purple')
    expect(storedTheme()).toBe('system')
  })
})
