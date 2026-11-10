import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  createExpense,
  updateExpense,
  type Expense,
  type ExpenseEdit,
  type ExpenseType,
  type NewExpense,
  type PaymentMethod,
} from '../api/expensesApi'
import { createItem } from '../api/catalogApi'
import { getSettings } from '../api/yearsApi'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { centsToInput, parseCents } from '../lib/money'
import { defaultDate } from '../lib/month'
import { selectable, useRefreshMonths, useYearCatalogs } from '../lib/useMonth'
import { BrandPicker } from './BrandPicker'
import { Dialog } from './Dialog'
import { FormMessage } from './FormMessage'
import { TextField } from './TextField'
import { field, primaryButton, quietButton, secondaryButton } from '../lib/ui'

/** Without `expense` the window records a new one; with it, edits that expense (REQ-023). */
type Props = { month: string; expense?: Expense; onClose: () => void }
type Parceling = 'none' | 'auto' | 'manual'

/** Value of the card list option that opens the "new card" fields (REQ-016, 2026-11-09). */
const NEW_CARD = 'new'

/** The "Novo lançamento" window (REQ-016..019, REQ-042): one expense, optionally in installments. */
export function ExpenseFormDialog({ month, expense, onClose }: Props) {
  const { t } = useTranslation()
  const refreshMonths = useRefreshMonths()
  const year = Number(month.slice(0, 4))
  const nameInput = useRef<HTMLInputElement>(null)
  const editing = expense !== undefined

  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings })
  const catalogs = useYearCatalogs(year)
  const autoAllowed = settings.data?.autoInstallments ?? false

  const [name, setName] = useState(expense?.name ?? '')
  const [amount, setAmount] = useState(expense ? centsToInput(expense.amountCents) : '')
  const [type, setType] = useState<ExpenseType>(expense?.type ?? 'FIXED')
  const [payment, setPayment] = useState<PaymentMethod>(expense?.paymentMethod ?? 'PIX')
  const [categoryId, setCategoryId] = useState(expense ? String(expense.categoryId) : '')
  const [cardId, setCardId] = useState(expense?.cardId ? String(expense.cardId) : '')
  const [personId, setPersonId] = useState(expense?.personId ? String(expense.personId) : '')
  const [date, setDate] = useState(() => expense?.date ?? defaultDate(month))
  const [paid, setPaid] = useState(expense?.paid ?? false)
  const [parceling, setParceling] = useState<Parceling>('none')
  const [count, setCount] = useState('')
  const [amountIsTotal, setAmountIsTotal] = useState(true)
  const [number, setNumber] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [savedMessage, setSavedMessage] = useState(false)

  const isCard = type === 'CARD'
  const effectivePayment: PaymentMethod = isCard ? 'CREDIT' : payment
  const usesCredit = effectivePayment === 'CREDIT'
  const parcelingChoice: Parceling = isCard && !editing ? (parceling === 'auto' && !autoAllowed ? 'none' : parceling) : 'none'
  // an installment stays a card expense: its N/M cannot change (the API keeps it)
  const typeLocked = editing && expense.installmentLabel !== undefined

  const save = useMutation({
    mutationFn: (body: NewExpense) =>
      editing ? updateExpense(expense.id, body as ExpenseEdit) : createExpense(month, body),
  })

  function validate(): NewExpense | null {
    const next: Record<string, string> = {}
    const cents = parseCents(amount)
    if (!name.trim()) next.name = t('expense.errors.name')
    if (cents === null) next.amount = t('expense.errors.amount')
    if (!categoryId) next.category = t('expense.errors.category')
    // credit always has a card (REQ-015/016, 2026-11-09)
    if (usesCredit && (!cardId || cardId === NEW_CARD)) next.card = t('expense.errors.card')
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
      name: name.trim(),
      categoryId: Number(categoryId),
      amountCents: cents,
      type,
      paymentMethod: effectivePayment,
      ...(usesCredit && { cardId: Number(cardId) }),
      ...(isCard && personId && { personId: Number(personId) }),
      // editing replaces every field, so a card expense keeps the mark it had
      ...(type === 'FIXED' ? { paid } : editing && { paid: expense.paid }),
      date,
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
    <Dialog title={editing ? t('expense.editTitle') : t('expense.title')} onClose={onClose}>
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
          <select
            className={field}
            value={type}
            disabled={typeLocked}
            onChange={(e) => setType(e.target.value as ExpenseType)}
          >
            <option value="FIXED">{t('expense.type.FIXED')}</option>
            <option value="CARD">{t('expense.type.CARD')}</option>
          </select>
        </label>

        {!isCard && (
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('expense.fields.payment')}
            <select className={field} value={payment} onChange={(e) => setPayment(e.target.value as PaymentMethod)}>
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
            className={field}
            value={categoryId}
            aria-invalid={errors.category ? true : undefined}
            aria-describedby={errors.category ? 'expense-category-error' : undefined}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">{t('expense.choose')}</option>
            {selectable(catalogs.categories, expense?.categoryId).map((c) => (
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
          <div className="flex flex-col gap-1">
            <label htmlFor="expense-card" className="text-sm font-medium text-ink">
              {t('expense.fields.card')}
            </label>
            <select
              id="expense-card"
              className={field}
              value={cardId}
              aria-invalid={errors.card ? true : undefined}
              aria-describedby={errors.card ? 'expense-card-error' : undefined}
              onChange={(e) => setCardId(e.target.value)}
            >
              <option value="">{t('expense.chooseCard')}</option>
              {selectable(catalogs.cards, expense?.cardId).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
              <option value={NEW_CARD}>{t('expense.newCard.option')}</option>
            </select>
            {errors.card && (
              <p id="expense-card-error" className="text-sm text-negative">
                {errors.card}
              </p>
            )}
            {cardId === NEW_CARD && <NewCardFields year={year} onCreated={(id) => setCardId(String(id))} />}
          </div>
        )}

        {isCard && (
          <label className="flex flex-col gap-1 text-sm font-medium text-ink">
            {t('expense.fields.person')}
            <select className={field} value={personId} onChange={(e) => setPersonId(e.target.value)}>
              <option value="">{t('expense.nobody')}</option>
              {selectable(catalogs.people, expense?.personId).map((p) => (
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
            <input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} className="size-4 accent-brand-text" />
            {t('expense.fields.paid')}
          </label>
        )}

        {typeLocked && (
          <p className="text-sm text-ink-soft">{t('expense.installmentFixed', { label: expense.installmentLabel })}</p>
        )}

        {isCard && !editing && (
          <fieldset className="flex flex-col gap-3 rounded-xl border border-line p-3">
            <legend className="px-1 text-sm font-medium text-ink">{t('expense.parceling.title')}</legend>
            <label className="flex flex-col gap-1 text-sm font-medium text-ink">
              {t('expense.parceling.mode')}
              <select className={field} value={parcelingChoice} onChange={(e) => setParceling(e.target.value as Parceling)}>
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
                  <select className={field} value={amountIsTotal ? 'total' : 'each'} onChange={(e) => setAmountIsTotal(e.target.value === 'total')}>
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
              {t('expense.saveAndAnother')}
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

/**
 * Creates a card without leaving the entry (REQ-016, 2026-11-09): name and due day, the same rules as the Cards
 * screen. The new card is selected right away.
 */
function NewCardFields({ year, onCreated }: { year: number; onCreated: (id: number) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [dueDay, setDueDay] = useState('')
  const [brand, setBrand] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const create = useMutation({
    mutationFn: () => createItem('cards', year, { name: name.trim(), dueDay: Number(dueDay), ...(brand && { brand }) }),
    onSuccess: async (card) => {
      await queryClient.invalidateQueries({ queryKey: ['catalog', 'cards'] })
      onCreated(card.id)
    },
  })

  function submit() {
    const next: Record<string, string> = {}
    const day = Number(dueDay)
    if (!name.trim()) next.name = t('catalog.errors.nameRequired')
    if (!Number.isInteger(day) || day < 1 || day > 31) next.dueDay = t('catalog.errors.dueDay')
    setErrors(next)
    if (Object.keys(next).length === 0) create.mutate()
  }

  return (
    <fieldset className="mt-2 flex flex-col gap-3 rounded-xl border border-line p-3">
      <legend className="px-1 text-sm font-medium text-ink">{t('expense.newCard.title')}</legend>
      {create.isError && <FormMessage kind="error">{apiErrorMessage(t, create.error)}</FormMessage>}
      <BrandPicker
        value={brand}
        onChange={(picked) => {
          setBrand(picked?.key ?? '')
          if (picked && !name.trim()) setName(picked.name)
        }}
      />
      <TextField label={t('catalog.fields.name')} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} />
      <TextField
        label={t('catalog.fields.dueDay')}
        type="number"
        inputMode="numeric"
        min={1}
        max={31}
        value={dueDay}
        onChange={(e) => setDueDay(e.target.value)}
        error={errors.dueDay}
      />
      <button type="button" className={secondaryButton} disabled={create.isPending} onClick={submit}>
        {t('expense.newCard.create')}
      </button>
    </fieldset>
  )
}
