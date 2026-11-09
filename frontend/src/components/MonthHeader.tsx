import { useTranslation } from 'react-i18next'
import type { MonthView } from '../api/monthApi'
import { Money } from './Money'

function Total({ label, cents }: { label: string; cents: number }) {
  return (
    <div className="flex min-w-0 flex-col">
      <dt className="text-xs text-ink-soft sm:text-sm">{label}</dt>
      <dd className="text-base font-semibold text-ink">
        <Money cents={cents} />
      </dd>
    </div>
  )
}

/**
 * Header of the month (REQ-041): the balance first and largest (the number that matters most), the other totals on
 * one quiet line under it and, when the closings are on, the total of each interval between closings
 * (REQ-030/031/048). Every number comes from the API as is.
 */
export function MonthHeader({ view }: { view: MonthView }) {
  const { t } = useTranslation()
  const { totals } = view
  return (
    <div className="flex flex-col gap-6 rounded-3xl border border-line bg-card p-4 shadow-card sm:p-6">
      <dl className="flex flex-wrap items-end gap-x-8 gap-y-4">
        <div className="flex w-full min-w-0 flex-col lg:w-auto lg:pr-4">
          <dt className="text-sm font-medium text-ink-soft">{t('month.totals.balance')}</dt>
          <dd className="text-[clamp(2.25rem,1.6rem+2.8vw,3.5rem)] leading-none font-bold tracking-tight text-ink">
            <Money cents={totals.balanceCents} tone />
          </dd>
        </div>
        <Total label={t('month.totals.income')} cents={totals.totalIncomeCents} />
        <Total label={t('month.totals.expenses')} cents={totals.totalExpensesCents} />
        <Total label={t('month.totals.fixed')} cents={totals.totalFixedCents} />
      </dl>
      {view.closingsEnabled && view.positions.length > 0 && (
        <section aria-label={t('month.positions.title')} className="flex flex-col gap-2 border-t border-line pt-4">
          <h2 className="text-sm font-semibold text-ink-soft">{t('month.positions.title')}</h2>
          <ul className="grid grid-cols-2 gap-x-8 gap-y-3 sm:flex sm:flex-wrap">
            {view.positions.map((p) => (
              <li key={p.fromDay} className="flex min-w-0 flex-col">
                <span className="text-xs text-ink-soft sm:text-sm">{t('month.positions.range', { from: p.fromDay, to: p.toDay })}</span>
                <span className="flex flex-col text-sm text-ink-soft">
                  {p.balanceCents < 0 ? t('month.positions.missing') : t('month.positions.left')}{' '}
                  <Money cents={p.balanceCents} tone unsigned className="text-base font-semibold" />
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
