import { useMutation } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { createIncome, updateIncome, type Income, type NewIncome } from '../api/incomesApi'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { centsToInput, parseCents } from '../lib/money'
import { defaultDate } from '../lib/month'
import { selectable, useRefreshMonths, useCatalogs } from '../lib/useMonth'
import { Dialog } from './Dialog'
import { FormMessage } from './FormMessage'
import { TextField } from './TextField'
import { field, primaryButton, quietButton, secondaryButton } from '../lib/ui'

/** Without `income` the window records a new one; with it, edits that income (REQ-023). */
type Props = { month: string; income?: Income; onClose: () => void }

/**
 * The "Nova entrada" window (REQ-024..026, REQ-042): salary or other income, optionally installment N of M
 * (the month load creates the next ones, REQ-025) or a reimbursement from a person (REQ-026).
 */
export function IncomeFormDialog({ month, income, onClose }: Props) {
  const { t } = useTranslation()
  const refreshMonths = useRefreshMonths()
  const catalogs = useCatalogs()
  const nameInput = useRef<HTMLInputElement>(null)
  const editing = income !== undefined

  const [name, setName] = useState(income?.name ?? '')
  const [amount, setAmount] = useState(income ? centsToInput(income.amountCents) : '')
  const [date, setDate] = useState(() => income?.date ?? defaultDate(month))
  const [personId, setPersonId] = useState(income?.personId ? String(income.personId) : '')
  const [inInstallments, setInInstallments] = useState(false)
  const [number, setNumber] = useState('')
  const [count, setCount] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [savedMessage, setSavedMessage] = useState(false)

  const save = useMutation({
    mutationFn: (body: NewIncome) => (editing ? updateIncome(income.id, body) : createIncome(month, body)),
  })

  function validate(): NewIncome | null {
    const next: Record<string, string> = {}
    const cents = parseCents(amount)
    if (!name.trim()) next.name = t('income.errors.name')
    if (cents === null) next.amount = t('income.errors.amount')
    if (!date || !date.startsWith(`${month}-`)) next.date = t('income.errors.date', { month })
    let installments: Partial<NewIncome> = {}
    if (inInstallments && !editing) {
      const current = Number(number)
      const total = Number(count)
      if (!Number.isInteger(total) || total < 2 || total > 120) next.count = t('income.errors.count')
      else if (!Number.isInteger(current) || current < 1 || current > total) next.number = t('income.errors.number')
      installments = { installmentNumber: current, installmentCount: total }
    }
    setErrors(next)
    if (Object.keys(next).length > 0 || cents === null) return null
    return {
      name: name.trim(),
      amountCents: cents,
      date,
      ...(personId && { personId: Number(personId) }),
      ...installments,
    }
  }

  function submit(event: FormEvent, another: boolean) {
    event.preventDefault()
    setSavedMessage(false)
    const body = validate()
    if (!body) return
    save.mutate(body, {
      onSuccess: () => {
        void refreshMonths()
        if (!another) return onClose()
        setName('')
        setAmount('')
        setNumber('')
        setCount('')
        setErrors({})
        setSavedMessage(true)
        nameInput.current?.focus()
      },
    })
  }

  return (
    <Dialog title={editing ? t('income.editTitle') : t('income.title')} onClose={onClose}>
      <form onSubmit={(e) => submit(e, false)} noValidate className="flex flex-col gap-4">
        {savedMessage && <FormMessage kind="success">{t('income.saved')}</FormMessage>}
        {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}

        <TextField
          ref={nameInput}
          label={t('income.fields.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
        />
        <TextField
          label={t('income.fields.amount')}
          inputMode="decimal"
          placeholder="0,00"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          error={errors.amount}
        />
        <TextField
          label={t('income.fields.date')}
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          error={errors.date}
        />

        <label className="flex flex-col gap-1 text-sm font-medium text-ink">
          {t('income.fields.person')}
          <select className={field} value={personId} onChange={(e) => setPersonId(e.target.value)}>
            <option value="">{t('income.nobody')}</option>
            {selectable(catalogs.people, income?.personId).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>

        {editing ? (
          income.installmentLabel && (
            <p className="text-sm text-ink-soft">{t('income.installmentFixed', { label: income.installmentLabel })}</p>
          )
        ) : (
          <fieldset className="flex flex-col gap-3 rounded-xl border border-line p-3">
            <legend className="px-1 text-sm font-medium text-ink">{t('income.installments.title')}</legend>
            <label className="flex items-center gap-2 text-sm text-ink">
              <input
                type="checkbox"
                checked={inInstallments}
                onChange={(e) => setInInstallments(e.target.checked)}
                className="size-4 accent-brand-text"
              />
              {t('income.installments.enable')}
            </label>
            {inInstallments && (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <TextField
                    label={t('income.installments.current')}
                    type="number"
                    inputMode="numeric"
                    min={1}
                    value={number}
                    onChange={(e) => setNumber(e.target.value)}
                    error={errors.number}
                  />
                  <TextField
                    label={t('income.installments.total')}
                    type="number"
                    inputMode="numeric"
                    min={2}
                    value={count}
                    onChange={(e) => setCount(e.target.value)}
                    error={errors.count}
                  />
                </div>
                <p className="text-sm text-ink-soft">{t('income.installments.help')}</p>
              </>
            )}
          </fieldset>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
          <button type="button" className={quietButton} onClick={onClose}>
            {t('catalog.cancel')}
          </button>
          {!editing && (
            <button
              type="button"
              disabled={save.isPending}
              onClick={(e) => submit(e, true)}
              className={secondaryButton}
            >
              {t('income.saveAndAnother')}
            </button>
          )}
          <button
            type="submit"
            disabled={save.isPending}
            className={primaryButton}
          >
            {t('catalog.save')}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
