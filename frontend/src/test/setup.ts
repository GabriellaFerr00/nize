import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setLanguage } from '../i18n'

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
  setLanguage('pt')
})
