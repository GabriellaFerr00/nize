import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { listItems } from '../api/catalogApi'
import { createExpense, type ExpenseType, type NewExpense, type PaymentMethod } from '../api/expensesApi'
import { getSettings } from '../api/yearsApi'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { parseCents } from '../lib/money'
import { Dialog } from './Dialog'
import { FormMessage } from './FormMessage'
import { TextField } from './TextField'

type Props = { month: string; onClose: () => void }
type Parceling = 'none' | 'auto' | 'manual'

const selectClass =
  'rounded-xl border border-line bg-white px-3 py-2.5 text-base text-ink focus:outline-2 focus:outline-brand-500'

function defaultDate(month: string): string {
  const today = new Date()
  const current = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
  return current === month ? `${month}-${String(today.getDate()).padStart(2, '0')}` : `${month}-01`
}

/** The "Novo lançamento" window (REQ-016..019, REQ-042): one expense, optionally in installments. */
export function ExpenseFormDialog({ month, onClose }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const year = Number(month.slice(0, 4))
  const nameInput = useRef<HTMLInputElement>(null)

  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings })
  const categories = useQuery({ queryKey: ['catalog', 'categories', year], queryFn: () => listItems('categories', year) })
  const cards = useQuery({ queryKey: ['catalog', 'cards', year], queryFn: () => listItems('cards', year) })
  const people = useQuery({ queryKey: ['catalog', 'people', year], queryFn: () => listItems('people', year) })
  const activeCategories = categories.data?.filter((c) => c.active) ?? []
  const activeCards = cards.data?.filter((c) => c.active) ?? []
  const activePeople = people.data?.filter((p) => p.active) ?? []
  const autoAllowed = settings.data?.autoInstallments ?? false

  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  const [type, setType] = useState<ExpenseType>('FIXED')
  const [payment, setPayment] = useState<PaymentMethod>('PIX')
  const [categoryId, setCategoryId] = useState('')
  const [cardId, setCardId] = useState('')
  const [personId, setPersonId] = useState('')
  const [date, setDate] = useState(() => defaultDate(month))
  const [paid, setPaid] = useState(false)
  const [parceling, setParceling] = useState<Parceling>('none')
  const [count, setCount] = useState('')
  const [amountIsTotal, setAmountIsTotal] = useState(true)
  const [number, setNumber] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [savedMessage, setSavedMessage] = useState(false)

  const isCard = type === 'CARD'
  const effectivePayment: PaymentMethod = isCard ? 'CREDIT' : payment
  const usesCredit = effectivePayment === 'CREDIT'
  const parcelingChoice: Parceling = isCard ? (parceling === 'auto' && !autoAllowed ? 'none' : parceling) : 'none'

  const save = useMutation({ mutationFn: (expense: NewExpense) => createExpense(month, expense) })

  function validate(): { body: NewExpense } | null {
    const next: Record<string, string> = {}
    const cents = parseCents(amount)
    if (!name.trim()) next.name = t('expense.errors.name')
    if (cents === null) next.amount = t('expense.errors.amount')
    if (!categoryId) next.category = t('expense.errors.category')
    if (!date || !date.startsWith(`${month}-`)) next.date = t('expense.errors.date', { month })
    let installments: Partial<NewExpense> = {}
    if (parcelingChoice === 'auto') {
      const n = Number(count)
      if (!Number.isInteger(n) || n < 2 || n > 120) next.count = t('expense.errors.autoCount')
      installments = { generateInstallments: true, installmentCount: n, amountIsTotal }
    } else if (parcelingChoice === 'manual') {
      const current = Number(number)
      const total = Number(count)
      if (!Number.isInteger(total) || total < 2 || total > 120) next.count = t('expense.errors.manualCount')
      else if (!Number.isInteger(current) || current < 1 || current > total) next.number = t('expense.errors.manualNumber')
      installments = { installmentNumber: current, installmentCount: total }
    }
    setErrors(next)
    if (Object.keys(next).length > 0 || cents === null) return null
    return {
      body: {
        name: name.trim(),
        categoryId: Number(categoryId),
        amountCents: cents,
        type,
        paymentMethod: effectivePayment,
        ...(usesCredit && cardId && { cardId: Number(cardId) }),
        ...(isCard && personId && { personId: Number(personId) }),
        ...(type === 'FIXED' && { paid }),
        date,
        ...installments,
      },
    }
  }

  function submit(event: FormEvent, another: boolean) {
    event.preventDefault()
    setSavedMessage(false)
    const checked = validate()
    if (!checked) return
    save.mutate(checked.body, {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['month', month] })
        if (!another) return onClose()
        // keep category, type, card, person and date for the next one; clear what changes every time
        setName('')
        setAmount('')
        setCount('')
        setNumber('')
        setPaid(false)
        setErrors({})
        setSavedMessage(true)
        nameInput.current?.focus()
      },
    })
  }

  return (
    <Dialog title={t('expense.title')} onClose={onClose}>
      <form onSubmit={(e) => submit(e, false)} noValidate className="flex flex-col gap-4">
        {savedMessage && <FormMessage kind="success">{t('expense.saved')}</FormMessage>}
        {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}

        <TextField
          ref={nameInput}
          label={t('expense.fields.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
        />
        <TextField
          label={t('expense.fields.amount')}
          inputMode="decimal"
          placeholder="0,00"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          error={errors.amount}
        />

        <label className="flex flex-col gap-1 text-sm font-medium text-ink">
          {t('expense.fields.type')}
          <select className={selectClass} value={type} onChange={(e) => setType(e.target.value as ExpenseType)}>
            <option value="FIXED">{t('expense.type.FIXED')}</option>
            <option value="CARD">{t('expense.type.CARD')}</option>
          </select>
        </label>

        {!isCard && (
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('expense.fields.payment')}
            <select className={selectClass} value={payment} onChange={(e) => setPayment(e.target.value as PaymentMethod)}>
              <option value="PIX">{t('expense.payment.PIX')}</option>
              <option value="CREDIT">{t('expense.payment.CREDIT')}</option>
            </select>
          </label>
        )}

        <div className="flex flex-col gap-1">
          <label htmlFor="expense-category" className="text-sm font-medium text-ink">
            {t('expense.fields.category')}
          </label>
          <select
            id="expense-category"
            className={selectClass}
            value={categoryId}
            aria-invalid={errors.category ? true : undefined}
            aria-describedby={errors.category ? 'expense-category-error' : undefined}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">{t('expense.choose')}</option>
            {activeCategories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          {errors.category && (
            <p id="expense-category-error" className="text-sm text-negative">
              {errors.category}
            </p>
          )}
        </div>

        {usesCredit && (
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('expense.fields.card')}
            <select className={selectClass} value={cardId} onChange={(e) => setCardId(e.target.value)}>
              <option value="">{t('expense.noCard')}</option>
              {activeCards.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
        )}

        {isCard && (
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('expense.fields.person')}
            <select className={selectClass} value={personId} onChange={(e) => setPersonId(e.target.value)}>
              <option value="">{t('expense.nobody')}</option>
              {activePeople.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}

        <TextField
          label={t('expense.fields.date')}
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          error={errors.date}
        />

        {type === 'FIXED' && (
          <label className="flex items-center gap-2 text-sm text-ink">
            <input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} className="size-4 accent-brand-600" />
            {t('expense.fields.paid')}
          </label>
        )}

        {isCard && (
          <fieldset className="flex flex-col gap-3 rounded-xl border border-line p-3">
            <legend className="px-1 text-sm font-medium text-ink">{t('expense.parceling.title')}</legend>
            <label className="flex flex-col gap-1 text-sm font-medium text-ink">
              {t('expense.parceling.mode')}
              <select className={selectClass} value={parcelingChoice} onChange={(e) => setParceling(e.target.value as Parceling)}>
                <option value="none">{t('expense.parceling.none')}</option>
                {autoAllowed && <option value="auto">{t('expense.parceling.auto')}</option>}
                <option value="manual">{t('expense.parceling.manual')}</option>
              </select>
            </label>
            {parcelingChoice === 'auto' && (
              <>
                <TextField
                  label={t('expense.parceling.count')}
                  type="number"
                  inputMode="numeric"
                  min={2}
                  value={count}
                  onChange={(e) => setCount(e.target.value)}
                  error={errors.count}
                />
                <label className="flex flex-col gap-1 text-sm font-medium text-ink">
                  {t('expense.parceling.amountIs')}
                  <select className={selectClass} value={amountIsTotal ? 'total' : 'each'} onChange={(e) => setAmountIsTotal(e.target.value === 'total')}>
                    <option value="total">{t('expense.parceling.total')}</option>
                    <option value="each">{t('expense.parceling.each')}</option>
                  </select>
                </label>
              </>
            )}
            {parcelingChoice === 'manual' && (
              <div className="grid grid-cols-2 gap-3">
                <TextField
                  label={t('expense.parceling.current')}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  value={number}
                  onChange={(e) => setNumber(e.target.value)}
                  error={errors.number}
                />
                <TextField
                  label={t('expense.parceling.totalCount')}
                  type="number"
                  inputMode="numeric"
                  min={2}
                  value={count}
                  onChange={(e) => setCount(e.target.value)}
                  error={errors.count}
                />
              </div>
            )}
          </fieldset>
        )}

        <div className="flex flex-wrap justify-end gap-2">
          <button type="button" className="rounded-xl px-4 py-2 text-sm text-ink-soft" onClick={onClose}>
            {t('catalog.cancel')}
          </button>
          <button
            type="button"
            disabled={save.isPending}
            onClick={(e) => submit(e, true)}
            className="rounded-xl border border-brand-600 px-4 py-2 text-sm font-medium text-brand-700 disabled:opacity-60"
          >
            {t('expense.saveAndAnother')}
          </button>
          <button
            type="submit"
            disabled={save.isPending}
            className="rounded-xl bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60"
          >
            {t('catalog.save')}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
