import { useTranslation } from 'react-i18next'
import { LANGUAGES, setLanguage, type Language } from '../i18n'
import { compactSelect } from '../lib/ui'

/** Interface language (REQ-046). `compact` shows the short codes (PT/EN) for the phone header. */
export function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { t, i18n } = useTranslation()
  return (
    <label className="flex items-center gap-2 text-sm text-ink-soft">
      <span className={compact ? 'sr-only' : ''}>{t('language.label')}</span>
      <select
        aria-label={t('language.label')}
        value={i18n.language}
        onChange={(event) => setLanguage(event.target.value as Language)}
        className={`${compactSelect} ${compact ? '' : 'flex-1'}`}
      >
        {LANGUAGES.map((language) => (
          <option key={language} value={language}>
            {compact ? t(`language.short.${language}`) : t(`language.${language}`)}
          </option>
        ))}
      </select>
    </label>
  )
}
