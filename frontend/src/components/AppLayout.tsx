import { CalendarDays, ChartColumn, CreditCard, LogOut, Settings, Tags, Users } from 'lucide-react'
import type { ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../api/authState'
import { useDefaultMonth } from '../lib/useMonth'
import { LanguageSwitcher } from './LanguageSwitcher'
import { Logo } from './Logo'
import { OnboardingModal } from './OnboardingModal'
import { ThemeSwitcher } from './ThemeSwitcher'

type NavItem = {
  key: string
  to: string
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>
}

/** Month and overview open in the user's current year (REQ-006). */
function navItems(month: string): NavItem[] {
  return [
    { key: 'month', to: `/month/${month}`, icon: CalendarDays },
    { key: 'overview', to: `/overview/${month.slice(0, 4)}`, icon: ChartColumn },
    { key: 'categories', to: '/categories', icon: Tags },
    { key: 'cards', to: '/cards', icon: CreditCard },
    { key: 'people', to: '/people', icon: Users },
    { key: 'settings', to: '/settings', icon: Settings },
  ]
}

/**
 * App shell: side menu from 768px up, bottom bar below (REQ-043).
 * Shown classes (`hidden md:flex` / `md:hidden`) are the single switch between the two.
 * NavLink marks the open page with aria-current="page"; the active item also has a bar, not only a color.
 */
export function AppLayout() {
  const { t } = useTranslation()
  const items = navItems(useDefaultMonth().month)
  const { signOut } = useAuth()

  return (
    <div className="min-h-screen">
      <a
        href="#content"
        className="sr-only z-50 rounded-lg bg-brand px-4 py-3 font-semibold text-on-brand focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        {t('nav.skip')}
      </a>

      <aside className="fixed inset-y-0 left-0 hidden w-60 flex-col gap-6 border-r border-line bg-sidebar p-4 md:flex">
        <Link to="/" className="self-start rounded-xl p-1">
          <Logo />
        </Link>
        <nav aria-label={t('nav.label')} data-testid="side-nav" className="flex flex-1 flex-col gap-1">
          {items.map(({ key, to, icon: Icon }) => (
            <NavLink
              key={key}
              to={to}
              className={({ isActive }) =>
                `relative flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm ${
                  isActive
                    ? 'bg-soft font-semibold text-ink before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:rounded-full before:bg-brand-text'
                    : 'font-medium text-ink-soft hover:bg-soft hover:text-ink'
                }`
              }
            >
              <Icon className="size-5" aria-hidden />
              {t(`nav.${key}`)}
            </NavLink>
          ))}
        </nav>
        <div className="flex flex-col gap-1 border-t border-line pt-4">
          <ThemeSwitcher showLabel />
          <div className="px-3">
            <LanguageSwitcher />
          </div>
        </div>
        <button
          type="button"
          onClick={signOut}
          className="flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium text-ink-soft hover:bg-soft hover:text-ink"
        >
          <LogOut className="size-5" aria-hidden />
          {t('auth.signOut')}
        </button>
      </aside>

      <header className="flex items-center justify-between gap-2 border-b border-line bg-sidebar px-4 py-2 md:hidden">
        <Link to="/" className="rounded-xl p-0.5">
          <Logo size="sm" />
        </Link>
        <div className="flex min-w-0 items-center gap-1 sm:gap-1.5">
          <ThemeSwitcher />
          <LanguageSwitcher compact />
          <button
            type="button"
            onClick={signOut}
            aria-label={t('auth.signOut')}
            className="grid size-11 shrink-0 place-items-center rounded-lg border border-line-strong text-ink-soft hover:text-ink"
          >
            <LogOut className="size-5" aria-hidden />
          </button>
        </div>
      </header>

      <OnboardingModal />

      <main id="content" tabIndex={-1} className="px-4 pb-28 pt-6 outline-none md:pb-10 md:pl-[17rem] md:pr-8">
        <div className="mx-auto w-full max-w-[1280px]">
          <Outlet />
        </div>
      </main>

      <nav
        aria-label={t('nav.label')}
        data-testid="bottom-nav"
        className="fixed inset-x-0 bottom-0 z-10 grid grid-cols-6 border-t border-line bg-sidebar pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        {items.map(({ key, to, icon: Icon }) => (
          <NavLink
            key={key}
            to={to}
            className={({ isActive }) =>
              `relative flex min-h-14 min-w-0 flex-col items-center justify-center gap-0.5 px-0.5 text-[11px] leading-tight ${
                isActive
                  ? 'font-medium text-brand-text before:absolute before:inset-x-3 before:top-0 before:h-[3px] before:rounded-b-full before:bg-brand-text'
                  : 'font-medium text-ink-soft'
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
