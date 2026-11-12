import { useMutation } from '@tanstack/react-query'
import { Plus, Gauge } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { deleteExpense, setExpensePaid, type DeleteScope, type Expense } from '../api/expensesApi'
import { deleteIncome, type Income } from '../api/incomesApi'
import { Dialog } from '../components/Dialog'
import { EntryLists, ExpenseList, IncomeList } from '../components/EntryLists'
import { ExpenseFormDialog } from '../components/ExpenseFormDialog'
import { FormMessage } from '../components/FormMessage'
import { IncomeFormDialog } from '../components/IncomeFormDialog'
import { LimitToast } from '../components/LimitToast'
import { MonthLimitsDialog } from '../components/MonthLimitsDialog'
import { MonthHeader } from '../components/MonthHeader'
import { MonthPicker } from '../components/MonthPicker'
import { SummaryPanel } from '../components/SummaryPanel'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { formatMonth } from '../lib/format'
import { isMonth } from '../lib/month'
import { useMonth, useRefreshMonths, useCatalogs } from '../lib/useMonth'
import { dangerButton, pageTitle, primaryButton, quietButton, secondaryButton } from '../lib/ui'

type DialogState =
  | { kind: 'expense'; expense?: Expense }
  | { kind: 'income'; income?: Income }
  | { kind: 'delete'; target: DeleteTarget }
  | { kind: 'limits' }
  | null

type DeleteTarget = {
  type: 'expense' | 'income'
  id: number
  name: string
  installmentNumber?: number
  installmentCount?: number
}

/** Only an installment that is not the last one offers "this one and the next ones" (REQ-050). */
const hasNextInstallments = (target: DeleteTarget) =>
  target.installmentNumber !== undefined &&
  target.installmentCount !== undefined &&
  target.installmentNumber < target.installmentCount

/**
 * The month screen (REQ-041): header with totals and closing positions, month/year navigation (REQ-040), lists of
 * income, fixed and card entries, summaries with the own balance, and quick entry (REQ-042). Opening a month runs
 * the month load on the server (REQ-022). Every number shown comes from `GET /api/months/{month}` (D-1).
 */
export function MonthPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { yyyyMm = '' } = useParams()
  const valid = isMonth(yyyyMm)
  const month = useMonth(yyyyMm, valid)
  const catalogs = useCatalogs()
  const refreshMonths = useRefreshMonths()
  const [dialog, setDialog] = useState<DialogState>(null)
  const close = () => setDialog(null)

  const togglePaid = useMutation({
    mutationFn: (expense: Expense) => setExpensePaid(expense.id, !expense.paid),
    onSettled: refreshMonths,
  })
  const [deleteScope, setDeleteScope] = useState<DeleteScope>('this')
  const remove = useMutation({
    mutationFn: ({ type, id, scope }: { type: 'expense' | 'income'; id: number; scope: DeleteScope }) =>
      type === 'expense' ? deleteExpense(id, scope) : deleteIncome(id, scope),
    onSuccess: () => {
      close()
      void refreshMonths()
    },
  })

  if (!valid) {
    return <FormMessage kind="error">{t('errors.INVALID_MONTH')}</FormMessage>
  }

  const view = month.data
  const askDelete = (
    type: 'expense' | 'income',
    item: { id: number; name: string; installmentNumber?: number; installmentCount?: number },
  ) => {
    remove.reset()
    setDeleteScope('this')
    setDialog({
      kind: 'delete',
      target: {
        type,
        id: item.id,
        name: item.name,
        installmentNumber: item.installmentNumber,
        installmentCount: item.installmentCount,
      },
    })
  }

  return (
    <section className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className={`${pageTitle} first-letter:uppercase`}>{formatMonth(yyyyMm, i18n.language)}</h1>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => setDialog({ kind: 'expense' })} className={primaryButton}>
            <Plus className="size-4" aria-hidden />
            {t('expense.new')}
          </button>
          <button type="button" onClick={() => setDialog({ kind: 'income' })} className={secondaryButton}>
            <Plus className="size-4" aria-hidden />
            {t('income.new')}
          </button>
          <button type="button" onClick={() => setDialog({ kind: 'limits' })} className={secondaryButton}>
            <Gauge className="size-4" aria-hidden />
            {t('month.limits.open')}
          </button>
        </div>
      </div>

      <MonthPicker month={yyyyMm} onChange={(next) => navigate(`/month/${next}`)} />

      {month.isPending && <p className="text-ink-soft">{t('month.loading')}</p>}
      {month.isError && <FormMessage kind="error">{apiErrorMessage(t, month.error)}</FormMessage>}
      {togglePaid.isError && <FormMessage kind="error">{apiErrorMessage(t, togglePaid.error)}</FormMessage>}

      {view && (
        <>
          <LimitToast view={view} />
          <MonthHeader view={view} />
          <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
            <EntryLists>
              {/* Entradas first, right after the closings of the header (REQ-041, 2026-11-12) */}
              <IncomeList
                incomes={view.incomes}
                catalogs={catalogs}
                totalCents={view.totals.totalIncomeCents}
                onEdit={(i) => setDialog({ kind: 'income', income: i })}
                onDelete={(i) => askDelete('income', i)}
              />
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
            </EntryLists>
            <SummaryPanel view={view} catalogs={catalogs} />
          </div>
        </>
      )}

      {dialog?.kind === 'expense' && <ExpenseFormDialog month={yyyyMm} expense={dialog.expense} onClose={close} />}
      {dialog?.kind === 'limits' && <MonthLimitsDialog month={yyyyMm} onClose={close} />}
      {dialog?.kind === 'income' && <IncomeFormDialog month={yyyyMm} income={dialog.income} onClose={close} />}
      {dialog?.kind === 'delete' && (
        <Dialog title={t('month.deleteTitle')} onClose={close}>
          <div className="flex flex-col gap-4">
            {remove.isError && <FormMessage kind="error">{apiErrorMessage(t, remove.error)}</FormMessage>}
            <p className="text-ink">{t('month.deleteQuestion', { name: dialog.target.name })}</p>
            {hasNextInstallments(dialog.target) && (
              <fieldset className="flex flex-col gap-1 rounded-xl border border-line p-3">
                <legend className="px-1 text-sm font-medium text-ink">{t('month.deleteScope.title')}</legend>
                {(['this', 'following'] as const).map((scope) => (
                  <label key={scope} className="flex min-h-11 cursor-pointer items-center gap-3 text-sm text-ink">
                    <input
                      type="radio"
                      name="delete-scope"
                      value={scope}
                      checked={deleteScope === scope}
                      onChange={() => setDeleteScope(scope)}
                      className="size-4 accent-brand"
                    />
                    {t(`month.deleteScope.${scope}`, {
                      number: dialog.target.installmentNumber,
                      count: dialog.target.installmentCount,
                    })}
                  </label>
                ))}
              </fieldset>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
              <button type="button" className={quietButton} onClick={close}>
                {t('catalog.cancel')}
              </button>
              <button
                type="button"
                disabled={remove.isPending}
                onClick={() =>
                  remove.mutate({
                    ...dialog.target,
                    scope: hasNextInstallments(dialog.target) ? deleteScope : 'this',
                  })
                }
                className={dangerButton}
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
