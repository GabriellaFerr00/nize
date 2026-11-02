import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import { ExpenseFormDialog } from '../components/ExpenseFormDialog'

/** Placeholder of the month screen (built in Phase 6); already offers the "Novo lançamento" window (REQ-042). */
export function MonthPage() {
  const { t, i18n } = useTranslation()
  const { yyyyMm = '' } = useParams()
  const [open, setOpen] = useState(false)
  const valid = /^\d{4}-(0[1-9]|1[0-2])$/.test(yyyyMm)
  const label = valid
    ? new Intl.DateTimeFormat(i18n.language === 'pt' ? 'pt-BR' : 'en-US', { month: 'long', year: 'numeric' }).format(
        new Date(Number(yyyyMm.slice(0, 4)), Number(yyyyMm.slice(5)) - 1, 1),
      )
    : yyyyMm

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold capitalize text-brand-900">{label}</h1>
        {valid && (
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="rounded-xl bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            {t('expense.new')}
          </button>
        )}
      </div>
      <p className="rounded-2xl border border-dashed border-line bg-white p-6 text-ink-soft">{t('pages.soon')}</p>
      {open && <ExpenseFormDialog month={yyyyMm} onClose={() => setOpen(false)} />}
    </section>
  )
}
