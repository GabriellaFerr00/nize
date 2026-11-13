import { Pencil, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { Expense } from '../api/expensesApi'
import type { MonthSummaries } from '../api/monthApi'
import type { Income } from '../api/incomesApi'
import { formatDay } from '../lib/format'
import type { YearCatalogs } from '../lib/useMonth'
import { Money } from './Money'
import { TagDot } from './TagDot'

type ListProps = { title: string; empty: string; totalCents?: number; children: ReactNode[] }

/**
 * A titled list of the month (Entradas, Fixos, Cartão, Terceiros); the optional total comes from the API. The lists share one
 * surface (`EntryLists`), like the pages of a ledger.
 */
function EntryList({ title, empty, totalCents, children }: ListProps) {
  return (
    <section aria-label={title} className="flex min-w-0 flex-col py-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold text-ink">{title}</h2>
        {totalCents !== undefined && <Money cents={totalCents} className="text-sm font-semibold text-ink-soft" />}
      </div>
      {children.length === 0 ? (
        <p className="mt-3 text-sm text-ink-soft">{empty}</p>
      ) : (
        <ul className="mt-1 divide-y divide-line">{children}</ul>
      )}
    </section>
  )
}

type RowProps = {
  name: string
  date: string
  details: (string | undefined)[]
  installmentLabel?: string
  cents: number
  tone?: boolean
  leading?: ReactNode
  /** Color of the category (REQ-052). */
  color?: string
  /** Without them the row is only to look at (Terceiros, REQ-041). */
  onEdit?: () => void
  onDelete?: () => void
}

function Row({ name, date, details, installmentLabel, cents, tone = false, leading, color, onEdit, onDelete }: RowProps) {
  const { t, i18n } = useTranslation()
  return (
    <li className="flex items-start gap-3 py-3">
      {leading}
      {/* the entry date leads the row (ledger style): day/month with a caption saying what it is; screen readers
          get the full date */}
      <span className="flex w-12 shrink-0 flex-col items-center">
        <time dateTime={date} className="text-base leading-6 font-semibold text-ink tabular-nums">
          <span aria-hidden>{`${date.slice(8)}/${date.slice(5, 7)}`}</span>
          <span className="sr-only">{t('month.entryDate', { date: formatDay(date, i18n.language) })}</span>
        </time>
        <span aria-hidden className="text-xs text-ink-soft">{t('month.entryDateCaption')}</span>
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex min-w-0 items-baseline justify-between gap-2">
          <p className="flex min-w-0 items-center gap-2">
            <TagDot color={color} />
            <span className="truncate font-medium text-ink">{name}</span>
            {installmentLabel && (
              <span
                className="shrink-0 rounded-md bg-soft px-1.5 py-0.5 text-xs font-semibold text-ink tabular-nums"
                title={t('month.installment', { label: installmentLabel })}
              >
                {installmentLabel}
              </span>
            )}
          </p>
          <Money cents={cents} tone={tone} className={`text-sm font-semibold ${tone ? '' : 'text-ink'}`} />
        </div>
        <div className="flex min-w-0 items-center justify-between gap-2">
          <p className="min-w-0 text-xs break-words text-ink-soft">{details.filter(Boolean).join(' · ')}</p>
          {onEdit && onDelete && <RowActions name={name} onEdit={onEdit} onDelete={onDelete} />}
        </div>
      </div>
    </li>
  )
}

function RowActions({ name, onEdit, onDelete }: { name: string; onEdit: () => void; onDelete: () => void }) {
  const { t } = useTranslation()
  return (
      <div className="-my-2 -mr-2 flex shrink-0">
        <button
          type="button"
          onClick={onEdit}
          aria-label={t('month.edit', { name })}
          className="grid size-11 place-items-center rounded-lg text-ink-soft hover:bg-soft hover:text-ink"
        >
          <Pencil className="size-4" aria-hidden />
        </button>
        <button
          type="button"
          onClick={onDelete}
          aria-label={t('month.delete', { name })}
          className="grid size-11 place-items-center rounded-lg text-ink-soft hover:bg-soft hover:text-negative"
        >
          <Trash2 className="size-4" aria-hidden />
        </button>
      </div>
  )
}

type ExpenseListProps = {
  kind: 'FIXED' | 'CARD'
  expenses: Expense[]
  catalogs: YearCatalogs
  totalCents?: number
  onTogglePaid: (expense: Expense) => void
  onEdit: (expense: Expense) => void
  onDelete: (expense: Expense) => void
}

/** Fixos or Cartão of the month (REQ-015/016): paid mark on fixed ones, N/M indicator (REQ-021), edit and delete. */
export function ExpenseList({ kind, expenses, catalogs, totalCents, onTogglePaid, onEdit, onDelete }: ExpenseListProps) {
  const { t } = useTranslation()
  const fixed = kind === 'FIXED'
  return (
    <EntryList
      title={fixed ? t('month.lists.fixed') : t('month.lists.card')}
      empty={fixed ? t('month.lists.fixedEmpty') : t('month.lists.cardEmpty')}
      totalCents={totalCents}
    >
      {expenses.map((e) => {
        // an old credit row without a card asks for one (there is no "Sem cartão" anymore, REQ-016)
        const card = e.paymentMethod === 'CREDIT' ? (catalogs.name('cards', e.cardId) ?? t('expense.cardMissing')) : undefined
        const person = catalogs.name('people', e.personId)
        return (
          <Row
            key={e.id}
            name={e.name}
            installmentLabel={e.installmentLabel}
            cents={e.amountCents}
            date={e.date}
            color={catalogs.categories.find((c) => c.id === e.categoryId)?.color}
            details={[
              catalogs.name('categories', e.categoryId),
              fixed ? t(`expense.payment.${e.paymentMethod}`) : undefined,
              card,
              person && t('month.thirdParty', { name: person }),
            ]}
            leading={
              fixed && (
                // the label around the box makes the touch area 44px (WCAG 2.5.8)
                <label className="-my-2.5 -ml-2.5 grid size-11 shrink-0 cursor-pointer place-items-center">
                  <input
                    type="checkbox"
                    checked={e.paid}
                    onChange={() => onTogglePaid(e)}
                    aria-label={t('month.paid', { name: e.name })}
                    title={t('expense.fields.paid')}
                    className="size-5 accent-brand-text"
                  />
                </label>
              )
            }
            onEdit={() => onEdit(e)}
            onDelete={() => onDelete(e)}
          />
        )
      })}
    </EntryList>
  )
}

type IncomeListProps = {
  incomes: Income[]
  catalogs: YearCatalogs
  totalCents: number
  onEdit: (income: Income) => void
  onDelete: (income: Income) => void
}

/** Entradas of the month (REQ-024..026, REQ-053): fixed ones are marked, reimbursements show who paid back. */
export function IncomeList({ incomes, catalogs, totalCents, onEdit, onDelete }: IncomeListProps) {
  const { t } = useTranslation()
  return (
    <EntryList title={t('month.lists.income')} empty={t('month.lists.incomeEmpty')} totalCents={totalCents}>
      {incomes.map((i) => {
        const person = catalogs.name('people', i.personId)
        return (
          <Row
            key={i.id}
            name={i.name}
            installmentLabel={i.installmentLabel}
            cents={i.amountCents}
            tone
            date={i.date}
            details={[
              i.fixed ? t('month.fixedIncome') : undefined,
              i.reimbursement ? t('month.reimbursementFrom', { name: person ?? '' }) : undefined,
            ]}
            onEdit={() => onEdit(i)}
            onDelete={() => onDelete(i)}
          />
        )
      })}
    </EntryList>
  )
}

type ThirdPartyListProps = {
  expenses: Expense[]
  byPerson: MonthSummaries['byPerson']
  catalogs: YearCatalogs
}

/**
 * Terceiros (REQ-041, 2026-11-12): each person with the amount the API summed and, below, her expenses of the month,
 * Pix or credit. Only to look at: the expenses stay in Fixos or Cartão, so no paid mark, edit or delete here, and the
 * front only filters by person, it never adds up.
 */
export function ThirdPartyList({ expenses, byPerson, catalogs }: ThirdPartyListProps) {
  const { t } = useTranslation()
  const title = t('month.lists.thirdParty')
  return (
    <section aria-label={title} className="flex min-w-0 flex-col py-4">
      <h2 className="text-lg font-semibold text-ink">{title}</h2>
      {byPerson.length === 0 ? (
        <p className="mt-3 text-sm text-ink-soft">{t('month.lists.thirdPartyEmpty')}</p>
      ) : (
        byPerson.map((p) => {
          const name = p.personName ?? catalogs.name('people', p.personId) ?? '—'
          const own = expenses
            .filter((e) => e.personId === p.personId)
            .sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id)
          return (
            <div key={p.personId} className="mt-3 flex flex-col">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h3 className="flex items-center gap-2 font-semibold text-ink">
                  <TagDot color={catalogs.people.find((x) => x.id === p.personId)?.color} />
                  {name}
                </h3>
                <Money cents={p.amountCents} className="text-sm font-semibold text-ink-soft" />
              </div>
              <ul className="mt-1 divide-y divide-line">
                {own.map((e) => (
                  <Row
                    key={e.id}
                    name={e.name}
                    installmentLabel={e.installmentLabel}
                    cents={e.amountCents}
                    date={e.date}
                    color={catalogs.categories.find((c) => c.id === e.categoryId)?.color}
                    details={[
                      catalogs.name('categories', e.categoryId),
                      e.paymentMethod === 'PIX'
                        ? t('expense.payment.PIX')
                        : (catalogs.name('cards', e.cardId) ?? t('expense.cardMissing')),
                    ]}
                  />
                ))}
              </ul>
            </div>
          )
        })
      )}
    </section>
  )
}

/** The one surface that holds the lists of the month, separated by rules. */
export function EntryLists({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col divide-y divide-line rounded-2xl border border-line bg-card px-4 shadow-card sm:px-5">
      {children}
    </div>
  )
}
