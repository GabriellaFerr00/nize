import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { createYear, getSettings, listYears, updateSettings, type Settings } from '../api/yearsApi'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { ClosingDaysField } from '../components/ClosingDaysField'
import { apiErrorMessage } from '../lib/apiErrorMessage'

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
    <section className="flex max-w-xl flex-col gap-6">
      <h1 className="text-2xl font-semibold text-ink">{t('pages.settings')}</h1>

      <form onSubmit={onSave} className="flex flex-col gap-4 rounded-2xl border border-line bg-card p-5">
        <h2 className="text-lg font-semibold text-ink">{t('settings.general')}</h2>
        {save.isSuccess && <FormMessage kind="success">{t('settings.saved')}</FormMessage>}
        {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}
        <label className="flex flex-col gap-1 text-sm font-medium text-ink">
          {t('settings.currentYear')}
          <select
            value={currentYear ?? ''}
            onChange={(e) => setDraft({ ...draft, currentYear: Number(e.target.value) })}
            className="rounded-xl border border-line-strong bg-card px-3 py-2.5 text-base font-normal text-ink"
          >
            {(years.data ?? []).map((y) => (
              <option key={y.year} value={y.year}>
                {y.year}
              </option>
            ))}
          </select>
          <span className="text-xs font-normal text-ink-soft">{t('settings.currentYearHelp')}</span>
        </label>
        <label className="flex items-start gap-3 text-sm text-ink">
          <input
            type="checkbox"
            checked={autoInstallments}
            onChange={(e) => setDraft({ ...draft, autoInstallments: e.target.checked })}
            className="mt-1 size-4 accent-brand-text"
          />
          <span>
            <span className="font-medium">{t('settings.autoInstallments')}</span>
            <span className="block text-xs text-ink-soft">{t('settings.autoInstallmentsHelp')}</span>
          </span>
        </label>
        <label className="flex items-start gap-3 text-sm text-ink">
          <input
            type="checkbox"
            checked={closingsEnabled}
            onChange={(e) => setDraft({ ...draft, closingsEnabled: e.target.checked })}
            className="mt-1 size-4 accent-brand-text"
          />
          <span>
            <span className="font-medium">{t('settings.closings')}</span>
            <span className="block text-xs text-ink-soft">{t('settings.closingsHelp')}</span>
          </span>
        </label>
        <ClosingDaysField days={closingDays} onChange={(days) => setDraft({ ...draft, closingDays: days })} />
        <button
          type="submit"
          disabled={save.isPending || settings.isLoading}
          className="self-start inline-flex min-h-11 items-center justify-center rounded-xl bg-brand px-4 text-sm font-semibold text-on-brand hover:bg-brand-hover disabled:opacity-60"
        >
          {t('catalog.save')}
        </button>
      </form>

      <form onSubmit={onAddYear} noValidate className="flex flex-col gap-4 rounded-2xl border border-line bg-card p-5">
        <h2 className="text-lg font-semibold text-ink">{t('settings.newYear')}</h2>
        <p className="text-sm text-ink-soft">{t('settings.newYearHelp')}</p>
        {addYear.isSuccess && <FormMessage kind="success">{t('settings.yearCreated', { year: addYear.data.year })}</FormMessage>}
        {addYear.isError && <FormMessage kind="error">{apiErrorMessage(t, addYear.error)}</FormMessage>}
        <TextField
          label={t('settings.yearNumber')}
          type="number"
          inputMode="numeric"
          value={newYear}
          onChange={(e) => setNewYearDraft(e.target.value)}
        />
        <button
          type="submit"
          disabled={addYear.isPending}
          className="self-start inline-flex min-h-11 items-center justify-center rounded-xl bg-brand px-4 text-sm font-semibold text-on-brand hover:bg-brand-hover disabled:opacity-60"
        >
          {t('settings.createYear')}
        </button>
      </form>
    </section>
  )
}
