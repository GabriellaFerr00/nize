import { useTranslation } from 'react-i18next'
import type { MonthView } from '../api/monthApi'
import { Money } from './Money'

function Stat({ label, cents, tone = false }: { label: string; cents: number; tone?: boolean }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 rounded-2xl border border-line bg-white p-3 sm:p-4">
      <dt className="text-xs font-medium text-ink-soft sm:text-sm">{label}</dt>
      <dd className="text-base font-semibold text-ink sm:text-lg">
        <Money cents={cents} tone={tone} />
      </dd>
    </div>
  )
}

/**
 * Header of the month (REQ-041): totals and, when the closings are on, the position on each closing day
 * (REQ-030/031/048). Every number comes from the API as is.
 */
export function MonthHeader({ view }: { view: MonthView }) {
  const { t } = useTranslation()
  const { totals } = view
  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-2 gap-2 sm:gap-3 lg:grid-cols-4">
        <Stat label={t('month.totals.expenses')} cents={totals.totalExpensesCents} />
        <Stat label={t('month.totals.fixed')} cents={totals.totalFixedCents} />
        <Stat label={t('month.totals.income')} cents={totals.totalIncomeCents} />
        <Stat label={t('month.totals.balance')} cents={totals.balanceCents} tone />
      </dl>
      {view.closingsEnabled && view.positions.length > 0 && (
        <section aria-label={t('month.positions.title')} className="flex flex-col gap-2">
          <h2 className="text-sm font-medium text-ink-soft">{t('month.positions.title')}</h2>
          <ul className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
            {view.positions.map((p) => (
              <li
                key={p.fromDay}
                className="flex min-w-0 flex-col rounded-xl border border-line bg-white px-3 py-2 text-sm"
              >
                <span className="text-ink-soft">{t('month.positions.range', { from: p.fromDay, to: p.toDay })}</span>
                <span className="font-semibold">
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
