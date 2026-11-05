import { useTranslation } from 'react-i18next'
import { LANGUAGES, setLanguage, type Language } from '../i18n'

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation()
  return (
    <label className="flex items-center gap-2 text-sm text-ink-soft">
      <span className="sr-only md:not-sr-only">{t('language.label')}</span>
      <select
        aria-label={t('language.label')}
        value={i18n.language}
        onChange={(event) => setLanguage(event.target.value as Language)}
        className="min-h-11 rounded-lg border border-line-strong bg-card px-1.5 text-sm text-ink sm:px-2"
      >
        {LANGUAGES.map((language) => (
          <option key={language} value={language}>
            {t(`language.${language}`)}
          </option>
        ))}
      </select>
    </label>
  )
}
