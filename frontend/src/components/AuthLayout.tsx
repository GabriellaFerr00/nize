import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router-dom'
import { LanguageSwitcher } from './LanguageSwitcher'

/** Centered card used by the sign-in, sign-up and password screens. */
export function AuthLayout() {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-screen flex-col items-center px-4 py-8">
      <div className="mb-6 flex w-full max-w-sm items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="grid size-9 place-items-center rounded-lg bg-brand-600 font-bold text-white">N</span>
          <span className="text-xl font-semibold text-brand-900">{t('app.name')}</span>
        </div>
        <LanguageSwitcher />
      </div>
      <main className="w-full max-w-sm rounded-2xl border border-line bg-white p-6 shadow-sm">
        <Outlet />
      </main>
    </div>
  )
}
