import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { clearMonthLimit, getMonthLimits, setMonthLimit, type MonthLimit } from '../api/monthApi'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { centsToInput, parseCents } from '../lib/money'
import { formatMonth } from '../lib/format'
import { primaryButton, quietButton } from '../lib/ui'
import { useRefreshMonths } from '../lib/useMonth'
import { Dialog } from './Dialog'
import { FormMessage } from './FormMessage'
import { Money } from './Money'
import { TextField } from './TextField'

type Props = { month: string; onClose: () => void }

/**
 * Limits of one month (REQ-051, D-22): each category shows its default limit and takes a value only for this month;
 * an empty field means "use the default".
 */
export function MonthLimitsDialog({ month, onClose }: Props) {
  const { t, i18n } = useTranslation()
  const limits = useQuery({ queryKey: ['monthLimits', month], queryFn: () => getMonthLimits(month) })
  return (
    <Dialog title={t('month.limits.dialogTitle', { month: formatMonth(month, i18n.language) })} onClose={onClose}>
      {limits.isError && <FormMessage kind="error">{apiErrorMessage(t, limits.error)}</FormMessage>}
      {limits.data && <LimitsForm month={month} rows={limits.data} onClose={onClose} />}
    </Dialog>
  )
}

function LimitsForm({ month, rows, onClose }: { month: string; rows: MonthLimit[]; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const refreshMonths = useRefreshMonths()
  const [values, setValues] = useState<Record<number, string>>(() =>
    Object.fromEntries(rows.map((r) => [r.categoryId, r.monthCents === null ? '' : centsToInput(r.monthCents)])),
  )
  const [errors, setErrors] = useState<Record<number, string>>({})

  const save = useMutation({
    mutationFn: async (changes: { categoryId: number; cents: number | null }[]) => {
      for (const c of changes) {
        if (c.cents === null) await clearMonthLimit(month, c.categoryId)
        else await setMonthLimit(month, c.categoryId, c.cents)
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['monthLimits', month] })
      void refreshMonths()
      onClose()
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const next: Record<number, string> = {}
    const changes: { categoryId: number; cents: number | null }[] = []
    for (const r of rows) {
      const typed = (values[r.categoryId] ?? '').trim()
      const cents = typed === '' ? null : parseCents(typed)
      if (typed !== '' && (cents === null || cents <= 0)) {
        next[r.categoryId] = t('catalog.errors.limit')
        continue
      }
      if (cents !== r.monthCents) changes.push({ categoryId: r.categoryId, cents })
    }
    setErrors(next)
    if (Object.keys(next).length === 0) save.mutate(changes)
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <p className="text-sm text-ink-soft">{t('month.limits.dialogHelp')}</p>
      {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}
      <ul className="flex flex-col divide-y divide-line">
        {rows.map((r) => (
          <li key={r.categoryId} className="flex flex-col gap-1 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
            <div className="flex min-w-0 flex-col pt-1">
              <span className="font-medium text-ink">{r.categoryName}</span>
              <span className="text-xs text-ink-soft">
                {r.defaultCents === null ? (
                  t('month.limits.noDefault')
                ) : (
                  <>
                    {t('month.limits.default')} <Money cents={r.defaultCents} />
                  </>
                )}
              </span>
            </div>
            <div className="sm:w-44">
              <TextField
                label={t('month.limits.thisMonth', { name: r.categoryName })}
                inputMode="decimal"
                value={values[r.categoryId] ?? ''}
                onChange={(e) => setValues((v) => ({ ...v, [r.categoryId]: e.target.value }))}
                error={errors[r.categoryId]}
              />
            </div>
          </li>
        ))}
      </ul>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button type="button" className={quietButton} onClick={onClose}>
          {t('catalog.cancel')}
        </button>
        <button type="submit" disabled={save.isPending} className={primaryButton}>
          {t('catalog.save')}
        </button>
      </div>
    </form>
  )
}
