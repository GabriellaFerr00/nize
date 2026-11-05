import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  createItem,
  deleteCategory,
  listItems,
  updateItem,
  type CatalogInput,
  type CatalogItem,
  type CatalogKind,
} from '../api/catalogApi'
import { ApiError } from '../api/client'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { useDefaultMonth } from '../lib/useMonth'
import { useSelectedYear } from '../lib/useSelectedYear'
import { Dialog } from './Dialog'
import { FormMessage } from './FormMessage'
import { TextField } from './TextField'
import { YearSelect } from './YearSelect'

type Props = {
  kind: CatalogKind
  /** Which extra fields the form shows besides the name. */
  fields: { description?: boolean; dueDay?: boolean }
  canDelete?: boolean
}

type DialogState =
  | { mode: 'form'; item?: CatalogItem }
  | { mode: 'delete'; item: CatalogItem }
  | { mode: 'inUse'; item: CatalogItem; count: number }
  | null

const asInput = (item: CatalogItem): CatalogInput => ({
  name: item.name,
  ...(item.description !== undefined && { description: item.description }),
  ...(item.dueDay !== undefined && { dueDay: item.dueDay }),
})

/** List + create/edit/activate/delete for categories, cards and people (REQ-011..014). */
export function CatalogManager({ kind, fields, canDelete = false }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { month: defaultMonth } = useDefaultMonth()
  const { year, years, setYear, isLoading } = useSelectedYear()
  const [dialog, setDialog] = useState<DialogState>(null)

  const items = useQuery({
    queryKey: ['catalog', kind, year],
    queryFn: () => listItems(kind, year as number),
    enabled: year !== null,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['catalog', kind] })

  const toggle = useMutation({
    mutationFn: (item: CatalogItem) => updateItem(kind, item.id, { ...asInput(item), active: !item.active }),
    onSuccess: refresh,
  })

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-ink">{t(`pages.${kind}`)}</h1>
        <div className="flex items-center gap-3">
          <YearSelect years={years} value={year} onChange={setYear} />
          <button
            type="button"
            disabled={year === null}
            onClick={() => setDialog({ mode: 'form' })}
            className="inline-flex min-h-11 items-center justify-center rounded-xl bg-brand px-4 text-sm font-semibold text-on-brand hover:bg-brand-hover disabled:opacity-60"
          >
            {t(`catalog.${kind}.new`)}
          </button>
        </div>
      </div>

      {toggle.isError && <FormMessage kind="error">{apiErrorMessage(t, toggle.error)}</FormMessage>}
      {items.isError && <FormMessage kind="error">{apiErrorMessage(t, items.error)}</FormMessage>}

      {!isLoading && items.data?.length === 0 && (
        <p className="rounded-2xl border border-dashed border-line bg-card p-6 text-ink-soft">
          {t(`catalog.${kind}.empty`)}
        </p>
      )}

      <ul className="flex flex-col gap-2">
        {items.data?.map((item) => (
          <li
            key={item.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-line bg-card p-4"
          >
            <div className="min-w-0">
              <p className="flex items-center gap-2 font-medium text-ink">
                <span className={item.active ? '' : 'text-ink-soft line-through'}>{item.name}</span>
                {!item.active && (
                  <span className="rounded-full border border-line-strong px-2 py-0.5 text-xs font-normal text-ink-soft">
                    {t('catalog.inactive')}
                  </span>
                )}
              </p>
              {item.dueDay !== undefined && (
                <p className="text-sm text-ink-soft">{t('catalog.dueOn', { day: item.dueDay })}</p>
              )}
              {item.description && <p className="text-sm text-ink-soft">{item.description}</p>}
            </div>
            <div className="-mx-3 flex flex-wrap text-sm">
              <button type="button" className="inline-flex min-h-11 items-center rounded-lg px-3 font-medium text-brand-text hover:bg-soft" onClick={() => setDialog({ mode: 'form', item })}>
                {t('catalog.edit')}
              </button>
              <button
                type="button"
                className="inline-flex min-h-11 items-center rounded-lg px-3 font-medium text-brand-text hover:bg-soft"
                disabled={toggle.isPending}
                onClick={() => toggle.mutate(item)}
              >
                {item.active ? t('catalog.deactivate') : t('catalog.activate')}
              </button>
              {canDelete && (
                <button type="button" className="inline-flex min-h-11 items-center rounded-lg px-3 font-medium text-negative hover:bg-soft" onClick={() => setDialog({ mode: 'delete', item })}>
                  {t('catalog.delete')}
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>

      {dialog?.mode === 'form' && year !== null && (
        <ItemFormDialog
          kind={kind}
          year={year}
          fields={fields}
          item={dialog.item}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null)
            void refresh()
          }}
        />
      )}
      {dialog?.mode === 'delete' && (
        <DeleteDialog
          item={dialog.item}
          onClose={() => setDialog(null)}
          onDeleted={() => {
            setDialog(null)
            void refresh()
          }}
          onInUse={(count) => setDialog({ mode: 'inUse', item: dialog.item, count })}
        />
      )}
      {dialog?.mode === 'inUse' && (
        <Dialog title={t('catalog.inUseTitle')} onClose={() => setDialog(null)}>
          <p className="text-sm text-ink">{t('catalog.inUse', { count: dialog.count, name: dialog.item.name })}</p>
          <p className="mt-2 text-sm text-ink-soft">{t('catalog.inUseQuestion')}</p>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className="min-h-11 rounded-xl px-4 text-sm font-medium text-ink-soft hover:bg-soft hover:text-ink" onClick={() => setDialog(null)}>
              {t('catalog.cancel')}
            </button>
            <button
              type="button"
              className="inline-flex min-h-11 items-center justify-center rounded-xl bg-brand px-4 text-sm font-semibold text-on-brand"
              onClick={() => navigate(`/month/${defaultMonth}`)}
            >
              {t('catalog.changeExpenses')}
            </button>
          </div>
        </Dialog>
      )}
    </section>
  )
}

type FormProps = {
  kind: CatalogKind
  year: number
  fields: Props['fields']
  item?: CatalogItem
  onClose: () => void
  onSaved: () => void
}

function ItemFormDialog({ kind, year, fields, item, onClose, onSaved }: FormProps) {
  const { t } = useTranslation()
  const [name, setName] = useState(item?.name ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  const [dueDay, setDueDay] = useState(item?.dueDay !== undefined ? String(item.dueDay) : '')
  const [errors, setErrors] = useState<{ name?: string; description?: string; dueDay?: string }>({})

  const save = useMutation({
    mutationFn: (input: CatalogInput) =>
      item ? updateItem(kind, item.id, { ...input, active: item.active }) : createItem(kind, year, input),
    onSuccess: onSaved,
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const day = Number(dueDay)
    const next = {
      name: !name.trim() ? t('catalog.errors.nameRequired') : name.trim().length > 80 ? t('catalog.errors.nameTooLong') : undefined,
      description: fields.description && description.length > 255 ? t('catalog.errors.descriptionTooLong') : undefined,
      dueDay: fields.dueDay && !(Number.isInteger(day) && day >= 1 && day <= 31) ? t('catalog.errors.dueDay') : undefined,
    }
    setErrors(next)
    if (next.name || next.description || next.dueDay) return
    save.mutate({
      name: name.trim(),
      ...(fields.description && { description: description.trim() }),
      ...(fields.dueDay && { dueDay: day }),
    })
  }

  return (
    <Dialog title={t(item ? `catalog.${kind}.edit` : `catalog.${kind}.new`)} onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}
        <TextField label={t('catalog.fields.name')} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} />
        {fields.description && (
          <TextField
            label={t('catalog.fields.description')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={errors.description}
          />
        )}
        {fields.dueDay && (
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
        )}
        <div className="flex justify-end gap-2">
          <button type="button" className="min-h-11 rounded-xl px-4 text-sm font-medium text-ink-soft hover:bg-soft hover:text-ink" onClick={onClose}>
            {t('catalog.cancel')}
          </button>
          <button
            type="submit"
            disabled={save.isPending}
            className="inline-flex min-h-11 items-center justify-center rounded-xl bg-brand px-4 text-sm font-semibold text-on-brand hover:bg-brand-hover disabled:opacity-60"
          >
            {t('catalog.save')}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

type DeleteProps = {
  item: CatalogItem
  onClose: () => void
  onDeleted: () => void
  onInUse: (count: number) => void
}

function DeleteDialog({ item, onClose, onDeleted, onInUse }: DeleteProps) {
  const { t } = useTranslation()
  const remove = useMutation({
    mutationFn: () => deleteCategory(item.id),
    onSuccess: onDeleted,
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'CATEGORY_IN_USE') onInUse(error.count ?? 0)
    },
  })
  const showError = remove.isError && !(remove.error instanceof ApiError && remove.error.code === 'CATEGORY_IN_USE')

  return (
    <Dialog title={t('catalog.deleteTitle')} onClose={onClose}>
      <p className="text-sm text-ink">{t('catalog.deleteQuestion', { name: item.name })}</p>
      {showError && <FormMessage kind="error">{apiErrorMessage(t, remove.error)}</FormMessage>}
      <div className="mt-5 flex justify-end gap-2">
        <button type="button" className="min-h-11 rounded-xl px-4 text-sm font-medium text-ink-soft hover:bg-soft hover:text-ink" onClick={onClose}>
          {t('catalog.cancel')}
        </button>
        <button
          type="button"
          disabled={remove.isPending}
          className="min-h-11 rounded-xl bg-negative px-4 text-sm font-semibold text-on-negative disabled:opacity-60"
          onClick={() => remove.mutate()}
        >
          {t('catalog.delete')}
        </button>
      </div>
    </Dialog>
  )
}
