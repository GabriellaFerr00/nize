import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router-dom'
import { LanguageSwitcher } from './LanguageSwitcher'
import { Logo } from './Logo'
import { ThemeSwitcher } from './ThemeSwitcher'

/** Centered card used by the sign-in, sign-up and password screens. */
export function AuthLayout() {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-screen flex-col items-center px-4 py-6 sm:py-10">
      <div className="flex w-full max-w-sm justify-end gap-2">
        <ThemeSwitcher />
        <LanguageSwitcher />
      </div>
      <header className="mb-6 mt-6 flex flex-col items-center gap-3 text-center sm:mt-10">
        <Logo size="lg" />
        <p className="text-sm text-ink-soft">{t('app.tagline')}</p>
      </header>
      <main id="content" className="w-full max-w-sm rounded-2xl border border-line bg-card p-6 shadow-card">
        <Outlet />
      </main>
    </div>
  )
}
