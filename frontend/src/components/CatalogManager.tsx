import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, Pencil, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  createItem,
  deleteItem,
  listItems,
  updateItem,
  type CatalogInput,
  type CatalogItem,
  type CatalogKind,
} from '../api/catalogApi'
import { ApiError } from '../api/client'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { centsToInput, parseCents } from '../lib/money'
import { useDefaultMonth } from '../lib/useMonth'
import { BrandPicker } from './BrandPicker'
import { useCardIdentity } from '../lib/cardIdentity'
import { CardBadge } from './CardBadge'
import { ColorPicker } from './ColorPicker'
import { Dialog } from './Dialog'
import { Money } from './Money'
import { FormMessage } from './FormMessage'
import { TagDot } from './TagDot'
import { TextField } from './TextField'
import { dangerButton, iconButton, pageTitle, primaryButton, quietButton } from '../lib/ui'

type Props = {
  kind: CatalogKind
  /** Which extra fields the form shows besides the name. */
  fields: { description?: boolean; dueDay?: boolean; monthlyLimit?: boolean; color?: boolean; brand?: boolean }
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
  // PUT replaces every field: keep the limit when only (de)activating (REQ-051)
  ...(item.monthlyLimitCents !== undefined && { monthlyLimitCents: item.monthlyLimitCents }),
  ...(item.color !== undefined && { color: item.color }),
  ...(item.brand && { brand: item.brand }),
})

/** List + create/edit/activate/delete for categories, cards and people (REQ-011..014). */
export function CatalogManager({ kind, fields, canDelete = false }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { month: defaultMonth } = useDefaultMonth()
  const [dialog, setDialog] = useState<DialogState>(null)

  const items = useQuery({ queryKey: ['catalog', kind], queryFn: () => listItems(kind) })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['catalog', kind] })

  const toggle = useMutation({
    mutationFn: (item: CatalogItem) => updateItem(kind, item.id, { ...asInput(item), active: !item.active }),
    onSuccess: refresh,
  })

  const active = items.data?.filter((item) => item.active) ?? []
  const inactive = items.data?.filter((item) => !item.active) ?? []
  const rowActions = {
    canDelete,
    busy: toggle.isPending,
    onEdit: (item: CatalogItem) => setDialog({ mode: 'form', item }),
    onToggle: (item: CatalogItem) => toggle.mutate(item),
    onDelete: (item: CatalogItem) => setDialog({ mode: 'delete', item }),
  }

  return (
    <section className="flex max-w-3xl flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className={pageTitle}>{t(`pages.${kind}`)}</h1>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setDialog({ mode: 'form' })}
            className={primaryButton}
          >
            {t(`catalog.${kind}.new`)}
          </button>
        </div>
      </div>

      {toggle.isError && <FormMessage kind="error">{apiErrorMessage(t, toggle.error)}</FormMessage>}
      {items.isError && <FormMessage kind="error">{apiErrorMessage(t, items.error)}</FormMessage>}

      {items.data?.length === 0 && (
        <p className="rounded-2xl border border-dashed border-line-strong bg-card p-6 text-ink-soft">
          {t(`catalog.${kind}.empty`)}
        </p>
      )}

      {active.length > 0 && (
        <ul className="flex flex-col divide-y divide-line rounded-2xl border border-line bg-card px-4 shadow-card sm:px-5">
          {active.map((item) => (
            <CatalogRow key={item.id} item={item} {...rowActions} />
          ))}
        </ul>
      )}

      {inactive.length > 0 && (
        <section aria-label={t(`catalog.${kind}.inactiveGroup`)} className="flex flex-col gap-2">
          <h2 className="text-sm font-semibold text-ink-soft">{t(`catalog.${kind}.inactiveGroup`)}</h2>
          <ul className="flex flex-col divide-y divide-line rounded-2xl border border-dashed border-line-strong px-4 sm:px-5">
            {inactive.map((item) => (
              <CatalogRow key={item.id} item={item} {...rowActions} />
            ))}
          </ul>
        </section>
      )}

      {dialog?.mode === 'form' && (
        <ItemFormDialog
          kind={kind}
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
          kind={kind}
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
        <Dialog title={t(`catalog.${kind}.inUseTitle`)} onClose={() => setDialog(null)}>
          <p className="text-sm text-ink">{t(`catalog.${kind}.inUse`, { count: dialog.count, name: dialog.item.name })}</p>
          <p className="mt-2 text-sm text-ink-soft">{t(`catalog.${kind}.inUseQuestion`)}</p>
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
            <button type="button" className={quietButton} onClick={() => setDialog(null)}>
              {t('catalog.cancel')}
            </button>
            <button
              type="button"
              className={primaryButton}
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

type RowProps = {
  item: CatalogItem
  canDelete: boolean
  busy: boolean
  onEdit: (item: CatalogItem) => void
  onToggle: (item: CatalogItem) => void
  onDelete: (item: CatalogItem) => void
}

/** One line of the list; a card leads with its due day, like the day of an entry on the month screen. */
function CatalogRow({ item, canDelete, busy, onEdit, onToggle, onDelete }: RowProps) {
  const { t } = useTranslation()
  const name = item.name
  return (
    <li className="flex items-start gap-3 py-3">
      {item.dueDay !== undefined && (
        <span className="flex w-9 shrink-0 flex-col items-center">
          <span aria-hidden className="text-lg leading-6 font-semibold text-ink tabular-nums">{item.dueDay}</span>
          <span aria-hidden className="text-xs text-ink-soft">{t('catalog.due')}</span>
          <span className="sr-only">{t('catalog.dueOn', { day: item.dueDay })}</span>
        </span>
      )}
      {item.dueDay !== undefined && <CardBadge name={name} brand={item.brand} />}
      <div className="flex min-w-0 flex-1 flex-col pt-px">
        <span className={`flex items-center gap-2 leading-6 font-medium break-words ${item.active ? 'text-ink' : 'text-ink-soft'}`}>
          <TagDot color={item.color} />
          {name}
        </span>
        {item.description && <span className="text-sm text-ink-soft">{item.description}</span>}
        {item.monthlyLimitCents !== undefined && (
          <span className="text-sm text-ink-soft">
            {t('catalog.limitPerMonth')} <Money cents={item.monthlyLimitCents} />
          </span>
        )}
      </div>
      <div className="-my-1.5 -mr-2 flex shrink-0">
        <button type="button" className={iconButton} aria-label={t('catalog.editItem', { name })} title={t('catalog.edit')} onClick={() => onEdit(item)}>
          <Pencil className="size-4" aria-hidden />
        </button>
        <button
          type="button"
          className={iconButton}
          disabled={busy}
          aria-label={t(item.active ? 'catalog.deactivateItem' : 'catalog.activateItem', { name })}
          title={item.active ? t('catalog.deactivate') : t('catalog.activate')}
          onClick={() => onToggle(item)}
        >
          {item.active ? <Archive className="size-4" aria-hidden /> : <ArchiveRestore className="size-4" aria-hidden />}
        </button>
        {canDelete && (
          <button
            type="button"
            className={`${iconButton} hover:text-negative`}
            aria-label={t('catalog.deleteItem', { name })}
            title={t('catalog.delete')}
            onClick={() => onDelete(item)}
          >
            <Trash2 className="size-4" aria-hidden />
          </button>
        )}
      </div>
    </li>
  )
}

type FormProps = {
  kind: CatalogKind
  fields: Props['fields']
  item?: CatalogItem
  onClose: () => void
  onSaved: () => void
}

function ItemFormDialog({ kind, fields, item, onClose, onSaved }: FormProps) {
  const { t } = useTranslation()
  const [name, setName] = useState(item?.name ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  const [dueDay, setDueDay] = useState(item?.dueDay !== undefined ? String(item.dueDay) : '')
  const [limit, setLimit] = useState(item?.monthlyLimitCents !== undefined ? centsToInput(item.monthlyLimitCents) : '')
  const [color, setColor] = useState(item?.color ?? '')
  const card = useCardIdentity(item)
  const [errors, setErrors] = useState<{ name?: string; description?: string; dueDay?: string; limit?: string }>({})

  const save = useMutation({
    mutationFn: (input: CatalogInput) =>
      item ? updateItem(kind, item.id, { ...input, active: item.active }) : createItem(kind, input),
    onSuccess: onSaved,
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const day = Number(dueDay)
    // null = left empty (no limit); undefined = typed something that is not a positive amount
    const typedLimit = limit.trim() === '' ? null : parseCents(limit)
    const limitCents = typedLimit === null ? (limit.trim() === '' ? null : undefined) : typedLimit > 0 ? typedLimit : undefined
    const next = {
      // a card's name comes from the card picker (REQ-052, 2026-11-12)
      name: fields.brand ? undefined : !name.trim() ? t('catalog.errors.nameRequired') : name.trim().length > 80 ? t('catalog.errors.nameTooLong') : undefined,
      description: fields.description && description.length > 255 ? t('catalog.errors.descriptionTooLong') : undefined,
      dueDay: fields.dueDay && !(Number.isInteger(day) && day >= 1 && day <= 31) ? t('catalog.errors.dueDay') : undefined,
      // empty = no limit; otherwise a positive amount (REQ-051)
      limit: fields.monthlyLimit && limitCents === undefined ? t('catalog.errors.limit') : undefined,
    }
    setErrors(next)
    const identity = fields.brand ? card.validate() : { name: name.trim() }
    if (!identity || next.name || next.description || next.dueDay || next.limit) return
    save.mutate({
      ...identity,
      ...(fields.description && { description: description.trim() }),
      ...(fields.dueDay && { dueDay: day }),
      ...(fields.monthlyLimit && limitCents !== null && limitCents !== undefined && { monthlyLimitCents: limitCents }),
      ...(fields.color && color && { color }),
    })
  }

  return (
    <Dialog title={t(item ? `catalog.${kind}.edit` : `catalog.${kind}.new`)} onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        {save.isError && <FormMessage kind="error">{apiErrorMessage(t, save.error)}</FormMessage>}
        {fields.brand ? (
          <BrandPicker identity={card} />
        ) : (
          <TextField label={t('catalog.fields.name')} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} />
        )}
        {fields.description && (
          <TextField
            label={t('catalog.fields.description')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={errors.description}
          />
        )}
        {fields.monthlyLimit && (
          <TextField
            label={t('catalog.fields.monthlyLimit')}
            inputMode="decimal"
            value={limit}
            onChange={(e) => setLimit(e.target.value)}
            error={errors.limit}
          />
        )}
        {fields.color && <ColorPicker value={color} onChange={setColor} />}
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
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
          <button type="button" className={quietButton} onClick={onClose}>
            {t('catalog.cancel')}
          </button>
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

type DeleteProps = {
  kind: CatalogKind
  item: CatalogItem
  onClose: () => void
  onDeleted: () => void
  onInUse: (count: number) => void
}

const IN_USE_CODES = ['CATEGORY_IN_USE', 'CARD_IN_USE']
const isInUse = (error: unknown): error is ApiError => error instanceof ApiError && IN_USE_CODES.includes(error.code)

function DeleteDialog({ kind, item, onClose, onDeleted, onInUse }: DeleteProps) {
  const { t } = useTranslation()
  const remove = useMutation({
    mutationFn: () => deleteItem(kind, item.id),
    onSuccess: onDeleted,
    onError: (error) => {
      if (isInUse(error)) onInUse(error.count ?? 0)
    },
  })
  const showError = remove.isError && !isInUse(remove.error)

  return (
    <Dialog title={t(`catalog.${kind}.deleteTitle`)} onClose={onClose}>
      <p className="text-sm text-ink">{t(`catalog.${kind}.deleteQuestion`, { name: item.name })}</p>
      {showError && <FormMessage kind="error">{apiErrorMessage(t, remove.error)}</FormMessage>}
      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
        <button type="button" className={quietButton} onClick={onClose}>
          {t('catalog.cancel')}
        </button>
        <button
          type="button"
          disabled={remove.isPending}
          className={dangerButton}
          onClick={() => remove.mutate()}
        >
          {t('catalog.delete')}
        </button>
      </div>
    </Dialog>
  )
}
