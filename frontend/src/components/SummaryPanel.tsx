import { ChevronDown } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { CardEntry, MonthView } from '../api/monthApi'
import { formatPercent } from '../lib/format'
import type { YearCatalogs } from '../lib/useMonth'
import { CardBadge } from './CardBadge'
import { Money } from './Money'
import { TagDot } from './TagDot'

function Block({ title, empty, children }: { title: string; empty?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="flex min-w-0 flex-col gap-2">
      <h3 className="text-sm font-semibold text-ink">{title}</h3>
      {empty ? <p className="text-sm text-ink-soft">{empty}</p> : <ul className="flex flex-col gap-1.5">{children}</ul>}
    </section>
  )
}

type LineProps = {
  label: string
  cents: number
  extra?: string
  sub?: boolean
  percent?: number
  limit?: ReactNode
  /** Color dot or card image before the name (REQ-052). */
  lead?: ReactNode
}

function Line({ label, cents, extra, sub = false, percent, limit, lead }: LineProps) {
  return (
    <li className={`flex flex-col gap-1 text-sm ${sub ? 'pl-3 text-ink-soft' : 'text-ink'}`}>
      <span className="flex items-baseline justify-between gap-2">
        <span className="flex min-w-0 items-center gap-2 break-words">
          {lead}
          {label}
        </span>
        <span className="flex shrink-0 items-baseline gap-2">
          {extra && <span className="text-xs text-ink-soft tabular-nums">{extra}</span>}
          <Money cents={cents} className={sub ? '' : 'font-medium'} />
        </span>
      </span>
      {limit}
      {percent !== undefined && (
        // as long as the percentage the API computed (display only)
        <span className="h-1.5 rounded-full bg-line" aria-hidden>
          <span className="block h-full rounded-full bg-brand-text" style={{ width: `${Math.min(percent, 100)}%` }} />
        </span>
      )}
    </li>
  )
}

/**
 * Summaries of the month (REQ-035..038) and the own balance (REQ-034), exactly as the API computed them:
 * category with percentage (and "used of limit", REQ-051), payment method, card and third parties by person and card.
 * Collapsible on phones; always open from 768px up (NFR-006).
 */
export function SummaryPanel({ view, catalogs }: { view: MonthView; catalogs?: YearCatalogs }) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const { totals, summaries } = view
  const cardName = (c: CardEntry) => c.cardName ?? ''
  const noExpenses = summaries.byCategory.length === 0 ? t('month.summary.noExpenses') : undefined

  return (
    <aside aria-label={t('month.summary.title')} className="flex min-w-0 flex-col gap-4 rounded-2xl bg-soft p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-ink">{t('month.summary.title')}</h2>
        <button
          type="button"
          aria-expanded={open}
          aria-controls="month-summaries"
          onClick={() => setOpen(!open)}
          className="flex min-h-11 items-center gap-1 rounded-lg px-3 text-sm font-semibold text-ink underline-offset-4 hover:underline md:hidden"
        >
          {open ? t('month.summary.hide') : t('month.summary.show')}
          <ChevronDown className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden />
        </button>
      </div>

      <section aria-label={t('month.summary.ownBalance')} className="flex flex-col gap-2 rounded-xl bg-card p-3">
        <div className="flex items-baseline justify-between gap-2">
          <h3 className="text-sm font-semibold text-ink">{t('month.summary.ownBalance')}</h3>
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
              lead={<TagDot color={catalogs?.categories.find((x) => x.id === c.categoryId)?.color} />}
              cents={c.amountCents}
              extra={formatPercent(c.percent, i18n.language)}
              percent={c.percent}
              limit={
                c.limitCents !== undefined && (
                  // "used of limit" (REQ-051); the warning word comes from the API status, never recomputed here
                  <span className={`text-xs ${c.limitStatus === 'OVER' || c.limitStatus === 'NEAR' ? 'font-medium text-warning' : 'text-ink-soft'}`}>
                    <Money cents={c.amountCents} /> {t('month.limits.of')} <Money cents={c.limitCents} />
                    {c.limitStatus === 'OVER' && ` · ${t('month.limits.over')}`}
                    {c.limitStatus === 'NEAR' && ` · ${t('month.limits.near')}`}
                  </span>
                )
              }
            />
          ))}
        </Block>

        <Block title={t('month.summary.byPayment')} empty={noExpenses}>
          <Line label={t('expense.payment.PIX')} cents={summaries.byPayment.pixCents} />
          <Line label={t('expense.payment.CREDIT')} cents={summaries.byPayment.creditCents} />
        </Block>

        <Block title={t('month.summary.byCard')} empty={summaries.byCard.length === 0 ? t('month.summary.noCredit') : undefined}>
          {summaries.byCard.map((c) => (
            <Line
              key={c.cardId}
              label={cardName(c)}
              cents={c.amountCents}
              lead={
                <CardBadge
                  id={c.cardId}
                  name={cardName(c)}
                  size="sm"
                  hasLogo={catalogs?.cards.find((x) => x.id === c.cardId)?.hasLogo}
                  brand={catalogs?.cards.find((x) => x.id === c.cardId)?.brand}
                />
              }
            />
          ))}
        </Block>

        <Block
          title={t('month.summary.byPerson')}
          empty={summaries.byPerson.length === 0 ? t('month.summary.noThirdParty') : undefined}
        >
          {summaries.byPerson.map((p) => (
            <li key={p.personId} className="flex flex-col gap-1">
              <ul className="flex flex-col gap-1">
                <Line
                  label={p.personName ?? '—'}
                  cents={p.amountCents}
                  lead={<TagDot color={catalogs?.people.find((x) => x.id === p.personId)?.color} />}
                />
                {p.byCard.map((c) => (
                  <Line sub key={c.cardId} label={cardName(c)} cents={c.amountCents} />
                ))}
              </ul>
            </li>
          ))}
        </Block>
      </div>
    </aside>
  )
}
