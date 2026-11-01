import { CalendarDays, ChartColumn, CreditCard, LogOut, Settings, Tags, Users } from 'lucide-react'
import type { ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../api/authState'
import { currentMonth } from '../lib/month'
import { LanguageSwitcher } from './LanguageSwitcher'
import { OnboardingModal } from './OnboardingModal'

type NavItem = {
  key: string
  to: string
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>
}

function navItems(): NavItem[] {
  const now = currentMonth()
  return [
    { key: 'month', to: `/month/${now}`, icon: CalendarDays },
    { key: 'overview', to: `/overview/${now.slice(0, 4)}`, icon: ChartColumn },
    { key: 'categories', to: '/categories', icon: Tags },
    { key: 'cards', to: '/cards', icon: CreditCard },
    { key: 'people', to: '/people', icon: Users },
    { key: 'settings', to: '/settings', icon: Settings },
  ]
}

function Brand() {
  const { t } = useTranslation()
  return (
    <div className="flex items-center gap-2">
      <span className="grid size-8 place-items-center rounded-lg bg-brand-600 text-sm font-bold text-white">N</span>
      <span className="text-lg font-semibold text-brand-900">{t('app.name')}</span>
    </div>
  )
}

/**
 * App shell: side menu from 768px up, bottom bar below (REQ-043).
 * Shown classes (`hidden md:flex` / `md:hidden`) are the single switch between the two.
 */
export function AppLayout() {
  const { t } = useTranslation()
  const items = navItems()
  const { signOut } = useAuth()

  return (
    <div className="min-h-screen">
      <aside className="fixed inset-y-0 left-0 hidden w-60 flex-col gap-6 border-r border-line bg-white p-4 md:flex">
        <Brand />
        <nav aria-label={t('nav.label')} data-testid="side-nav" className="flex flex-1 flex-col gap-1">
          {items.map(({ key, to, icon: Icon }) => (
            <NavLink
              key={key}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium ${
                  isActive ? 'bg-brand-100 text-brand-700' : 'text-ink-soft hover:bg-brand-50'
                }`
              }
            >
              <Icon className="size-5" aria-hidden />
              {t(`nav.${key}`)}
            </NavLink>
          ))}
        </nav>
        <LanguageSwitcher />
        <button
          type="button"
          onClick={signOut}
          className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-ink-soft hover:bg-brand-50"
        >
          <LogOut className="size-5" aria-hidden />
          {t('auth.signOut')}
        </button>
      </aside>

      <header className="flex items-center justify-between border-b border-line bg-white px-4 py-3 md:hidden">
        <Brand />
        <div className="flex items-center gap-2">
          <LanguageSwitcher />
          <button
            type="button"
            onClick={signOut}
            aria-label={t('auth.signOut')}
            className="grid size-9 place-items-center rounded-lg border border-line text-ink-soft"
          >
            <LogOut className="size-5" aria-hidden />
          </button>
        </div>
      </header>

      <OnboardingModal />

      <main className="px-4 pb-24 pt-6 md:pb-8 md:pl-[17rem] md:pr-8">
        <Outlet />
      </main>

      <nav
        aria-label={t('nav.label')}
        data-testid="bottom-nav"
        className="fixed inset-x-0 bottom-0 z-10 grid grid-cols-6 border-t border-line bg-white md:hidden"
      >
        {items.map(({ key, to, icon: Icon }) => (
          <NavLink
            key={key}
            to={to}
            className={({ isActive }) =>
              `flex min-w-0 flex-col items-center gap-0.5 px-0.5 py-2 text-[10px] font-medium ${
                isActive ? 'text-brand-700' : 'text-ink-soft'
              }`
            }
          >
            <Icon className="size-5" aria-hidden />
            <span className="max-w-full">{t(`nav.${key}`)}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
