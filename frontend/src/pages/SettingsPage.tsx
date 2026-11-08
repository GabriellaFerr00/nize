import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { createYear, getSettings, listYears, updateSettings, type Settings } from '../api/yearsApi'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { ClosingDaysField } from '../components/ClosingDaysField'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { field, pageTitle, primaryButton, secondaryButton } from '../lib/ui'

export function SettingsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings })
  const years = useQuery({ queryKey: ['years'], queryFn: listYears })

  // Edits live in a draft until saved; before any edit the screen shows what the server has.
  const [draft, setDraft] = useState<Partial<Settings>>({})
  const currentYear = draft.currentYear ?? settings.data?.currentYear
  const autoInstallments = draft.autoInstallments ?? settings.data?.autoInstallments ?? false
  const closingsEnabled = draft.closingsEnabled ?? settings.data?.closingsEnabled ?? false
  const closingDays = draft.closingDays ?? settings.data?.closingDays ?? []
  const latestYear = Math.max(0, ...(years.data ?? []).map((y) => y.year))
  const [newYearDraft, setNewYearDraft] = useState<string | null>(null)
  const newYear = newYearDraft ?? (latestYear ? String(latestYear + 1) : '')

  const save = useMutation({
    mutationFn: updateSettings,
    onSuccess: () => {
      setDraft({})
      void queryClient.invalidateQueries({ queryKey: ['settings'] })
      void queryClient.invalidateQueries({ queryKey: ['years'] })
    },
  })
  const addYear = useMutation({
    mutationFn: createYear,
    onSuccess: () => {
      setNewYearDraft(null)
      void queryClient.invalidateQueries({ queryKey: ['years'] })
    },
  })

  function onSave(event: FormEvent) {
    event.preventDefault()
    if (currentYear !== undefined) save.mutate({ currentYear, autoInstallments, closingsEnabled, closingDays })
  }

  function onAddYear(event: FormEvent) {
    event.preventDefault()
    const value = Number(newYear)
    if (Number.isInteger(value)) addYear.mutate(value)
  }

  return (
    <section className="flex max-w-2xl flex-col gap-6">
      <h1 className={pageTitle}>{t('pages.settings')}</h1>

      <form onSubmit={onSave} className="flex flex-col rounded-2xl border border-line bg-card px-4 shadow-card sm:px-5">
        <h2 className="pt-4 pb-1 text-lg font-semibold text-ink">{t('settings.general')}</h2>
        <div className="flex flex-col divide-y divide-line">
          <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 py-4">
            <label htmlFor="settings-year" className="flex min-w-0 flex-1 basis-60 flex-col">
              <span className="font-medium text-ink">{t('settings.currentYear')}</span>
              <span className="text-sm text-ink-soft">{t('settings.currentYearHelp')}</span>
            </label>
            <select
              id="settings-year"
              value={currentYear ?? ''}
              onChange={(e) => setDraft({ ...draft, currentYear: Number(e.target.value) })}
              className={`${field} w-32`}
            >
              {(years.data ?? []).map((y) => (
                <option key={y.year} value={y.year}>
                  {y.year}
                </option>
              ))}
            </select>
          </div>
          <Toggle
            label={t('settings.autoInstallments')}
            help={t('settings.autoInstallmentsHelp')}
            checked={autoInstallments}
            onChange={(checked) => setDraft({ ...draft, autoInstallments: checked })}
          />
          <div className="flex flex-col gap-4 py-4">
            <Toggle
              flush
              label={t('settings.closings')}
              help={t('settings.closingsHelp')}
              checked={closingsEnabled}
              onChange={(checked) => setDraft({ ...draft, closingsEnabled: checked })}
            />
            {closingsEnabled && (
              <ClosingDaysField days={closingDays} onChange={(days) => setDraft({ ...draft, closingDays: days })} />
            )}
          </div>
        </div>
        <div className="flex flex-col gap-3 border-t border-line py-4">
          {save.isSuccess && <FormMessage kind="success">{t('settings.saved')}</FormMessage>}
          {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}
          <button type="submit" disabled={save.isPending || settings.isLoading} className={`${primaryButton} self-start`}>
            {t('catalog.save')}
          </button>
        </div>
      </form>

      <form onSubmit={onAddYear} noValidate className="flex flex-col gap-3 rounded-2xl border border-dashed border-line-strong p-4 sm:p-5">
        <div>
          <h2 className="text-lg font-semibold text-ink">{t('settings.newYear')}</h2>
          <p className="text-sm text-ink-soft">{t('settings.newYearHelp')}</p>
        </div>
        {addYear.isSuccess && <FormMessage kind="success">{t('settings.yearCreated', { year: addYear.data.year })}</FormMessage>}
        {addYear.isError && <FormMessage kind="error">{apiErrorMessage(t, addYear.error)}</FormMessage>}
        <div className="flex flex-wrap items-end gap-2">
          <div className="w-32">
            <TextField
              label={t('settings.yearNumber')}
              type="number"
              inputMode="numeric"
              value={newYear}
              onChange={(e) => setNewYearDraft(e.target.value)}
            />
          </div>
          <button type="submit" disabled={addYear.isPending} className={secondaryButton}>
            {t('settings.createYear')}
          </button>
        </div>
      </form>
    </section>
  )
}

type ToggleProps = { label: string; help: string; checked: boolean; onChange: (checked: boolean) => void; flush?: boolean }

/** An option on its own line: name and help on the left, the checkbox on the right. */
function Toggle({ label, help, checked, onChange, flush = false }: ToggleProps) {
  return (
    <label className={`flex cursor-pointer items-center justify-between gap-6 ${flush ? '' : 'py-4'}`}>
      <span className="flex min-w-0 flex-col">
        <span className="font-medium text-ink">{label}</span>
        <span className="text-sm text-ink-soft">{help}</span>
      </span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="size-6 shrink-0 accent-brand-text"
      />
    </label>
  )
}
