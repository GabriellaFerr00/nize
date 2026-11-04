import { useMutation } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { deleteExpense, setExpensePaid, type Expense } from '../api/expensesApi'
import { deleteIncome, type Income } from '../api/incomesApi'
import { Dialog } from '../components/Dialog'
import { ExpenseList, IncomeList } from '../components/EntryLists'
import { ExpenseFormDialog } from '../components/ExpenseFormDialog'
import { FormMessage } from '../components/FormMessage'
import { IncomeFormDialog } from '../components/IncomeFormDialog'
import { MonthHeader } from '../components/MonthHeader'
import { MonthPicker } from '../components/MonthPicker'
import { SummaryPanel } from '../components/SummaryPanel'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { formatMonth } from '../lib/format'
import { isMonth } from '../lib/month'
import { useMonth, useRefreshMonths, useYearCatalogs } from '../lib/useMonth'

type DialogState =
  | { kind: 'expense'; expense?: Expense }
  | { kind: 'income'; income?: Income }
  | { kind: 'delete'; target: { type: 'expense' | 'income'; id: number; name: string } }
  | null

const primary = 'flex items-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700'
const secondary =
  'flex items-center gap-1.5 rounded-xl border border-brand-600 bg-white px-4 py-2 text-sm font-medium text-brand-700 hover:bg-brand-50'

/**
 * The month screen (REQ-041): header with totals and closing positions, month/year navigation (REQ-040), lists of
 * fixed, card and income entries, summaries with the own balance, and quick entry (REQ-042). Opening a month runs
 * the month load on the server (REQ-022). Every number shown comes from `GET /api/months/{month}` (D-1).
 */
export function MonthPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { yyyyMm = '' } = useParams()
  const valid = isMonth(yyyyMm)
  const month = useMonth(yyyyMm, valid)
  const catalogs = useYearCatalogs(Number(yyyyMm.slice(0, 4)))
  const refreshMonths = useRefreshMonths()
  const [dialog, setDialog] = useState<DialogState>(null)
  const close = () => setDialog(null)

  const togglePaid = useMutation({
    mutationFn: (expense: Expense) => setExpensePaid(expense.id, !expense.paid),
    onSettled: refreshMonths,
  })
  const remove = useMutation({
    mutationFn: ({ type, id }: { type: 'expense' | 'income'; id: number }) =>
      type === 'expense' ? deleteExpense(id) : deleteIncome(id),
    onSuccess: () => {
      close()
      void refreshMonths()
    },
  })

  if (!valid) {
    return <FormMessage kind="error">{t('errors.INVALID_MONTH')}</FormMessage>
  }

  const view = month.data
  const askDelete = (type: 'expense' | 'income', item: { id: number; name: string }) => {
    remove.reset()
    setDialog({ kind: 'delete', target: { type, id: item.id, name: item.name } })
  }

  return (
    <section className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-brand-900 first-letter:uppercase">{formatMonth(yyyyMm, i18n.language)}</h1>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => setDialog({ kind: 'expense' })} className={primary}>
            <Plus className="size-4" aria-hidden />
            {t('expense.new')}
          </button>
          <button type="button" onClick={() => setDialog({ kind: 'income' })} className={secondary}>
            <Plus className="size-4" aria-hidden />
            {t('income.new')}
          </button>
        </div>
      </div>

      <MonthPicker month={yyyyMm} onChange={(next) => navigate(`/month/${next}`)} />

      {month.isPending && <p className="text-ink-soft">{t('month.loading')}</p>}
      {month.isError && <FormMessage kind="error">{apiErrorMessage(t, month.error)}</FormMessage>}
      {togglePaid.isError && <FormMessage kind="error">{apiErrorMessage(t, togglePaid.error)}</FormMessage>}

      {view && (
        <>
          <MonthHeader view={view} />
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
            <div className="flex min-w-0 flex-col gap-4">
              <ExpenseList
                kind="FIXED"
                expenses={view.expenses.filter((e) => e.type === 'FIXED')}
                catalogs={catalogs}
                totalCents={view.totals.totalFixedCents}
                onTogglePaid={(e) => togglePaid.mutate(e)}
                onEdit={(e) => setDialog({ kind: 'expense', expense: e })}
                onDelete={(e) => askDelete('expense', e)}
              />
              <ExpenseList
                kind="CARD"
                expenses={view.expenses.filter((e) => e.type === 'CARD')}
                catalogs={catalogs}
                onTogglePaid={(e) => togglePaid.mutate(e)}
                onEdit={(e) => setDialog({ kind: 'expense', expense: e })}
                onDelete={(e) => askDelete('expense', e)}
              />
              <IncomeList
                incomes={view.incomes}
                catalogs={catalogs}
                totalCents={view.totals.totalIncomeCents}
                onEdit={(i) => setDialog({ kind: 'income', income: i })}
                onDelete={(i) => askDelete('income', i)}
              />
            </div>
            <SummaryPanel view={view} />
          </div>
        </>
      )}

      {dialog?.kind === 'expense' && <ExpenseFormDialog month={yyyyMm} expense={dialog.expense} onClose={close} />}
      {dialog?.kind === 'income' && <IncomeFormDialog month={yyyyMm} income={dialog.income} onClose={close} />}
      {dialog?.kind === 'delete' && (
        <Dialog title={t('month.deleteTitle')} onClose={close}>
          <div className="flex flex-col gap-4">
            {remove.isError && <FormMessage kind="error">{apiErrorMessage(t, remove.error)}</FormMessage>}
            <p className="text-ink">{t('month.deleteQuestion', { name: dialog.target.name })}</p>
            <div className="flex flex-wrap justify-end gap-2">
              <button type="button" className="rounded-xl px-4 py-2 text-sm text-ink-soft" onClick={close}>
                {t('catalog.cancel')}
              </button>
              <button
                type="button"
                disabled={remove.isPending}
                onClick={() => remove.mutate(dialog.target)}
                className="rounded-xl bg-negative px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {t('catalog.delete')}
              </button>
            </div>
          </div>
        </Dialog>
      )}
    </section>
  )
}
