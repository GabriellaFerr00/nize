/**
 * Light/dark theme (REQ-045, D-20). "system" follows the device; "light" and "dark" put `data-theme` on <html>,
 * which is what the tokens in styles/index.css react to. The choice is remembered in localStorage.
 */
export const THEMES = ['system', 'light', 'dark'] as const
export type Theme = (typeof THEMES)[number]

const STORAGE_KEY = 'nize.theme'

function isTheme(value: string | null): value is Theme {
  return value !== null && (THEMES as readonly string[]).includes(value)
}

export function storedTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isTheme(stored) ? stored : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(theme: Theme) {
  const root = document.documentElement
  if (theme === 'system') delete root.dataset.theme
  else root.dataset.theme = theme
}

/** Switches the theme right away and remembers the choice. */
export function setTheme(theme: Theme) {
  applyTheme(theme)
  try {
    if (theme === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // storage unavailable (private mode): the choice just isn't remembered
  }
}
