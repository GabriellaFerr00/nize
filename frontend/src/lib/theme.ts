/**
 * Light/dark theme (REQ-045, D-20). The chosen theme goes on <html> as `data-theme`, which is what the tokens in
 * styles/index.css react to, and is remembered in localStorage. Until the user picks one, the device theme is used.
 */
export const THEMES = ['light', 'dark'] as const
export type Theme = (typeof THEMES)[number]

const STORAGE_KEY = 'nize.theme'

function isTheme(value: string | null): value is Theme {
  return value !== null && (THEMES as readonly string[]).includes(value)
}

function deviceTheme(): Theme {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

export function storedTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isTheme(stored) ? stored : deviceTheme()
  } catch {
    return deviceTheme()
  }
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
}

const CHANGE_EVENT = 'nize-theme-change'

/** Lets every theme control on screen follow the same choice (side menu and phone header). */
export function subscribeTheme(onChange: () => void) {
  window.addEventListener(CHANGE_EVENT, onChange)
  return () => window.removeEventListener(CHANGE_EVENT, onChange)
}

/** The theme on screen now: the one on <html>, or the stored/device one before the app sets it. */
export function currentTheme(): Theme {
  const shown = document.documentElement.dataset.theme ?? null
  return isTheme(shown) ? shown : storedTheme()
}

/** Switches the theme right away and remembers the choice. */
export function setTheme(theme: Theme) {
  applyTheme(theme)
  window.dispatchEvent(new Event(CHANGE_EVENT))
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // storage unavailable (private mode): the choice just isn't remembered
  }
}
