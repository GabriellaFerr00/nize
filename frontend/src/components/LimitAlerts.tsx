import { TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { MonthView } from '../api/monthApi'
import { Money } from './Money'

/**
 * Categories near or over their monthly limit (REQ-051), exactly as the API marked them. Only a warning: nothing is
 * blocked. It shows right after an entry is saved because the month is fetched again.
 */
export function LimitAlerts({ view }: { view: MonthView }) {
  const { t } = useTranslation()
  const alerts = view.summaries.byCategory.filter((c) => c.limitStatus === 'NEAR' || c.limitStatus === 'OVER')
  if (alerts.length === 0) return null
  return (
    <section
      role="status"
      aria-label={t('month.limits.title')}
      className="flex flex-col gap-2 rounded-2xl border border-warning/50 bg-warning/10 p-4 text-sm text-ink"
    >
      <h2 className="flex items-center gap-2 font-semibold">
        <TriangleAlert className="size-4 shrink-0 text-warning" aria-hidden />
        {t('month.limits.title')}
      </h2>
      <ul className="flex flex-col gap-1">
        {alerts.map((c) => (
          <li key={c.categoryId}>
            <span className="font-medium">{c.categoryName}</span>{' '}
            <span>{t(c.limitStatus === 'OVER' ? 'month.limits.over' : 'month.limits.near')}</span>{' '}
            <span className="text-ink-soft">
              (<Money cents={c.amountCents} /> {t('month.limits.of')} <Money cents={c.limitCents ?? 0} />)
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
