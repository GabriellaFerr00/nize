import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import pt from './pt.json'

export const LANGUAGES = ['pt', 'en'] as const
export type Language = (typeof LANGUAGES)[number]

const STORAGE_KEY = 'nize.language'
/** Portuguese is the default language (REQ-046). */
const DEFAULT_LANGUAGE: Language = 'pt'

function isLanguage(value: string | null): value is Language {
  return value !== null && (LANGUAGES as readonly string[]).includes(value)
}

function storedLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isLanguage(stored) ? stored : DEFAULT_LANGUAGE
  } catch {
    return DEFAULT_LANGUAGE
  }
}

function applyDocumentLanguage(language: string) {
  document.documentElement.lang = language === 'pt' ? 'pt-BR' : 'en'
}

void i18n.use(initReactI18next).init({
  resources: { pt: { translation: pt }, en: { translation: en } },
  lng: storedLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  interpolation: { escapeValue: false },
})

applyDocumentLanguage(i18n.language)

/** Changes the interface language and remembers the choice. */
export function setLanguage(language: Language) {
  void i18n.changeLanguage(language)
  applyDocumentLanguage(language)
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // storage unavailable (private mode): the choice just isn't remembered
  }
}

export default i18n
