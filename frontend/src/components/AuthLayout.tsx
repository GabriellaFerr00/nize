import { CalendarDays, PencilLine, Scale } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router-dom'
import { LanguageSwitcher } from './LanguageSwitcher'
import { Logo } from './Logo'
import { ThemeSwitcher } from './ThemeSwitcher'

const POINTS = [
  { key: 'month', icon: CalendarDays },
  { key: 'closings', icon: Scale },
  { key: 'noBank', icon: PencilLine },
] as const

/**
 * Layout of the sign-in, sign-up and password screens (design 0.2.9): from 768px a soft green panel with the logo,
 * the tagline and what Nize does, beside the form; on phones the logo on top and the form right on the page.
 */
export function AuthLayout() {
  const { t } = useTranslation()
  return (
    <div className="min-h-screen md:grid md:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside className="hidden flex-col gap-12 bg-soft p-10 md:flex lg:p-14">
        <Logo size="lg" />
        <div className="flex max-w-md flex-col gap-8">
          <p className="text-4xl leading-tight font-bold tracking-tight text-ink lg:text-5xl">{t('app.tagline')}</p>
          <ul className="flex flex-col gap-4">
            {POINTS.map(({ key, icon: Icon }) => (
              <li key={key} className="flex items-start gap-3 text-ink">
                <Icon className="mt-0.5 size-5 shrink-0 text-brand-text" aria-hidden />
                <span>{t(`auth.pitch.${key}`)}</span>
              </li>
            ))}
          </ul>
        </div>
      </aside>

      <div className="flex min-h-screen flex-col px-4 py-4 sm:px-8 sm:py-6">
        <div className="flex items-center justify-between gap-2">
          <span className="md:invisible">
            <Logo size="sm" />
          </span>
          <div className="flex items-center gap-2">
            <ThemeSwitcher />
            <LanguageSwitcher compact />
          </div>
        </div>
        <div className="flex flex-1 flex-col justify-center py-8">
          <p className="mb-8 text-2xl leading-tight font-bold tracking-tight text-ink md:hidden">{t('app.tagline')}</p>
          <main id="content" className="mx-auto w-full max-w-sm">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}
