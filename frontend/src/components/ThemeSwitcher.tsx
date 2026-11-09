import { Moon, Sun } from 'lucide-react'
import { useSyncExternalStore } from 'react'
import { useTranslation } from 'react-i18next'
import { currentTheme, setTheme, subscribeTheme, type Theme } from '../lib/theme'

/**
 * Light / dark toggle (REQ-045, D-20; design 0.2.9): one button named "Tema escuro" that is pressed in dark mode.
 * The choice is applied right away and remembered (`lib/theme`). With `showLabel` the name is also visible.
 * The icon shows where the button takes you: a sun in dark mode, a moon in light mode (REQ-045, 2026-11-09).
 */
export function ThemeSwitcher({ showLabel = false }: { showLabel?: boolean }) {
  const { t } = useTranslation()
  const theme = useSyncExternalStore(subscribeTheme, currentTheme)
  const dark = theme === 'dark'
  const toggle = () => {
    const next: Theme = dark ? 'light' : 'dark'
    setTheme(next)
  }
  return (
    <button
      type="button"
      aria-pressed={dark}
      aria-label={showLabel ? undefined : t('theme.darkMode')}
      onClick={toggle}
      className={`flex min-h-11 shrink-0 items-center gap-3 rounded-xl text-sm font-medium text-ink-soft hover:bg-soft hover:text-ink ${
        showLabel ? 'px-3' : 'w-11 justify-center border border-line-strong'
      }`}
    >
      {dark ? (
        <Sun className="size-5" aria-hidden data-icon="sun" />
      ) : (
        <Moon className="size-5" aria-hidden data-icon="moon" />
      )}
      {showLabel && <span className="flex-1 text-left">{t('theme.darkMode')}</span>}
      {/* a small switch shows the state without relying on color alone */}
      {showLabel && (
        <span
          aria-hidden
          className={`flex h-6 w-10 items-center rounded-full border border-line-strong p-0.5 ${dark ? 'justify-end bg-brand' : 'justify-start bg-card'}`}
        >
          <span className={`size-4 rounded-full ${dark ? 'bg-on-brand' : 'bg-ink-soft'}`} />
        </span>
      )}
    </button>
  )
}
