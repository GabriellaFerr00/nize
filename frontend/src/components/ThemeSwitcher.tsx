import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { setTheme, storedTheme, THEMES, type Theme } from '../lib/theme'

/** Light / dark selector, next to the language one (REQ-045, D-20). */
export function ThemeSwitcher() {
  const { t } = useTranslation()
  const [theme, setCurrent] = useState<Theme>(storedTheme)
  return (
    <label className="flex items-center gap-2 text-sm text-ink-soft">
      <span className="sr-only md:not-sr-only">{t('theme.label')}</span>
      <select
        aria-label={t('theme.label')}
        value={theme}
        onChange={(event) => {
          const next = event.target.value as Theme
          setTheme(next)
          setCurrent(next)
        }}
        className="min-h-11 rounded-lg border border-line-strong bg-card px-1.5 text-sm text-ink sm:px-2"
      >
        {THEMES.map((value) => (
          <option key={value} value={value}>
            {t(`theme.${value}`)}
          </option>
        ))}
      </select>
    </label>
  )
}
