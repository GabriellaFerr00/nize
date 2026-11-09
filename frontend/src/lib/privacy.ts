import { useSyncExternalStore } from 'react'

/**
 * Hide/show money on screen (REQ-049). Display only: the data and every calculation stay the same. The choice is
 * remembered in this browser and every control and amount on screen follows it right away.
 */
const STORAGE_KEY = 'nize.hideValues'
const CHANGE_EVENT = 'nize-hide-values-change'

export function valuesHidden(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

export function setValuesHidden(hidden: boolean) {
  try {
    localStorage.setItem(STORAGE_KEY, String(hidden))
  } catch {
    // storage unavailable (private mode): the choice just isn't remembered
  }
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGE_EVENT, onChange)
  window.addEventListener('storage', onChange)
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange)
    window.removeEventListener('storage', onChange)
  }
}

export function useValuesHidden(): boolean {
  return useSyncExternalStore(subscribe, valuesHidden)
}

/** What a hidden amount shows: the currency and dots, never the digits. */
export const HIDDEN_DIGITS = '••••'
