import { ChevronDown } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { CardEntry, MonthView } from '../api/monthApi'
import { formatPercent } from '../lib/format'
import { Money } from './Money'

function Block({ title, empty, children }: { title: string; empty?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="flex min-w-0 flex-col gap-2">
      <h3 className="text-sm font-semibold text-brand-900">{title}</h3>
      {empty ? <p className="text-sm text-ink-soft">{empty}</p> : <ul className="flex flex-col gap-1.5">{children}</ul>}
    </section>
  )
}

function Line({ label, cents, extra, sub = false }: { label: string; cents: number; extra?: string; sub?: boolean }) {
  return (
    <li className={`flex items-baseline justify-between gap-2 text-sm ${sub ? 'pl-3 text-ink-soft' : 'text-ink'}`}>
      <span className="min-w-0 break-words">{label}</span>
      <span className="flex shrink-0 items-baseline gap-2">
        {extra && <span className="text-xs text-ink-soft tabular-nums">{extra}</span>}
        <Money cents={cents} className={sub ? '' : 'font-medium'} />
      </span>
    </li>
  )
}

/**
 * Summaries of the month (REQ-035..038) and the own balance (REQ-034), exactly as the API computed them:
 * category with percentage, payment method, card (with "no card") and third parties by person and card.
 * Collapsible on phones; always open from 768px up (NFR-006).
 */
export function SummaryPanel({ view }: { view: MonthView }) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const { totals, summaries } = view
  const cardName = (c: CardEntry) => c.cardName ?? t('expense.noCard')
  const noExpenses = summaries.byCategory.length === 0 ? t('month.summary.noExpenses') : undefined

  return (
    <aside aria-label={t('month.summary.title')} className="flex min-w-0 flex-col gap-4 rounded-2xl border border-line bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-brand-900">{t('month.summary.title')}</h2>
        <button
          type="button"
          aria-expanded={open}
          aria-controls="month-summaries"
          onClick={() => setOpen(!open)}
          className="flex items-center gap-1 rounded-lg px-2 py-1 text-sm font-medium text-brand-700 hover:bg-brand-50 md:hidden"
        >
          {open ? t('month.summary.hide') : t('month.summary.show')}
          <ChevronDown className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden />
        </button>
      </div>

      <section aria-label={t('month.summary.ownBalance')} className="flex flex-col gap-2 rounded-xl bg-brand-50 p-3">
        <div className="flex items-baseline justify-between gap-2">
          <h3 className="text-sm font-semibold text-brand-900">{t('month.summary.ownBalance')}</h3>
          <Money cents={totals.ownBalanceCents} tone className="text-lg font-semibold" />
        </div>
        <ul className="flex flex-col gap-1">
          <Line sub label={t('month.summary.balance')} cents={totals.balanceCents} />
          <Line sub label={t('month.summary.thirdParty')} cents={totals.thirdPartyExpensesCents} />
          <Line sub label={t('month.summary.reimbursements')} cents={totals.reimbursementsCents} />
        </ul>
        <p className="text-xs text-ink-soft">{t('month.summary.ownBalanceHelp')}</p>
      </section>

      <div id="month-summaries" className={`flex-col gap-5 md:flex ${open ? 'flex' : 'hidden'}`}>
        <Block title={t('month.summary.byCategory')} empty={noExpenses}>
          {summaries.byCategory.map((c) => (
            <Line
              key={c.categoryId}
              label={c.categoryName ?? '—'}
              cents={c.amountCents}
              extra={formatPercent(c.percent, i18n.language)}
            />
          ))}
        </Block>

        <Block title={t('month.summary.byPayment')} empty={noExpenses}>
          <Line label={t('expense.payment.PIX')} cents={summaries.byPayment.pixCents} />
          <Line label={t('expense.payment.CREDIT')} cents={summaries.byPayment.creditCents} />
        </Block>

        <Block title={t('month.summary.byCard')} empty={summaries.byCard.length === 0 ? t('month.summary.noCredit') : undefined}>
          {summaries.byCard.map((c) => (
            <Line key={c.cardId ?? 'none'} label={cardName(c)} cents={c.amountCents} />
          ))}
        </Block>

        <Block
          title={t('month.summary.byPerson')}
          empty={summaries.byPerson.length === 0 ? t('month.summary.noThirdParty') : undefined}
        >
          {summaries.byPerson.map((p) => (
            <li key={p.personId} className="flex flex-col gap-1">
              <ul className="flex flex-col gap-1">
                <Line label={p.personName ?? '—'} cents={p.amountCents} />
                {p.byCard.map((c) => (
                  <Line sub key={c.cardId ?? 'none'} label={cardName(c)} cents={c.amountCents} />
                ))}
              </ul>
            </li>
          ))}
        </Block>
      </div>
    </aside>
  )
}
