import { useTranslation } from 'react-i18next'
import type { MonthView } from '../api/monthApi'
import { Money } from './Money'

function Stat({ label, cents, className = '' }: { label: string; cents: number; className?: string }) {
  return (
    <div className={`flex min-w-0 flex-col gap-1 rounded-2xl border border-line bg-card p-3 shadow-card sm:p-4 ${className}`}>
      <dt className="text-xs font-medium text-ink-soft sm:text-sm">{label}</dt>
      <dd className="text-base font-semibold text-ink sm:text-lg">
        <Money cents={cents} />
      </dd>
    </div>
  )
}

/**
 * Header of the month (REQ-041): the balance first and largest (the number that matters most), then the other
 * totals and, when the closings are on, the position of each interval (REQ-030/031/048). Every number comes from
 * the API as is.
 */
export function MonthHeader({ view }: { view: MonthView }) {
  const { t } = useTranslation()
  const { totals } = view
  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3 sm:gap-3 lg:grid-cols-[1.5fr_1fr_1fr_1fr]">
        <div className="col-span-2 flex min-w-0 flex-col justify-center gap-1 rounded-2xl bg-soft p-4 shadow-card sm:col-span-3 sm:p-5 lg:col-span-1">
          <dt className="text-sm font-medium text-ink-soft">{t('month.totals.balance')}</dt>
          <dd className="text-[clamp(1.75rem,1.4rem+1.6vw,2.5rem)] leading-tight font-bold text-ink">
            <Money cents={totals.balanceCents} tone />
          </dd>
        </div>
        <Stat label={t('month.totals.expenses')} cents={totals.totalExpensesCents} />
        <Stat label={t('month.totals.fixed')} cents={totals.totalFixedCents} />
        <Stat label={t('month.totals.income')} cents={totals.totalIncomeCents} className="col-span-2 sm:col-span-1" />
      </dl>
      {view.closingsEnabled && view.positions.length > 0 && (
        <section aria-label={t('month.positions.title')} className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold text-ink-soft">{t('month.positions.title')}</h2>
          <ul className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
            {view.positions.map((p) => (
              <li
                key={p.fromDay}
                className="flex min-w-0 flex-col rounded-xl border border-line bg-card px-3 py-2 text-sm shadow-card"
              >
                <span className="text-ink-soft">{t('month.positions.range', { from: p.fromDay, to: p.toDay })}</span>
                <span className="font-semibold text-ink">
                  {p.balanceCents < 0 ? t('month.positions.missing') : t('month.positions.left')}{' '}
                  <Money cents={p.balanceCents} tone unsigned />
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
