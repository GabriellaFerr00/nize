import { useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, ChevronDown, Plus } from 'lucide-react'
import { cardBrand, searchBrands } from '../lib/cardBrands'
import { NEW_CARD_CHOICE, type CardIdentity } from '../lib/cardIdentity'
import { field } from '../lib/ui'
import { CardBadge } from './CardBadge'
import { TextField } from './TextField'

type Props = { identity: CardIdentity }

/**
 * "Selecionar cartão": a button that opens the list of cards most used in Brazil, each with its logo, a search and,
 * last, "Adicionar um novo cartão". Below it, the nickname (with a brand) or the name (new card).
 */
export function BrandPicker({ identity }: Props) {
  const { t } = useTranslation()
  const { choice, name, setName, pick, errors } = identity
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const listId = useId()
  const errorId = `${listId}-error`
  const button = useRef<HTMLButtonElement>(null)
  const brands = searchBrands(query)
  const selected = cardBrand(choice)

  function choose(next: string) {
    pick(next)
    setOpen(false)
    setQuery('')
    button.current?.focus()
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <span id={`${listId}-label`} className="text-sm font-medium text-ink">
          {t('catalog.brand.title')}
        </span>
        <button
          ref={button}
          type="button"
          className={`${field} flex items-center gap-2 text-left`}
          aria-labelledby={`${listId}-label ${listId}-value`}
          aria-expanded={open}
          aria-controls={listId}
          aria-invalid={errors.choice ? true : undefined}
          aria-describedby={errors.choice ? errorId : undefined}
          onClick={() => setOpen(!open)}
        >
          {selected && <CardBadge name={selected.name} brand={selected.key} size="sm" />}
          {choice === NEW_CARD_CHOICE && <Plus className="size-5 text-ink-soft" aria-hidden />}
          <span id={`${listId}-value`} className={`min-w-0 flex-1 truncate ${choice ? '' : 'text-ink-soft'}`}>
            {selected ? selected.name : choice === NEW_CARD_CHOICE ? t('catalog.brand.newCard') : t('catalog.brand.placeholder')}
          </span>
          <ChevronDown className={`size-4 shrink-0 text-ink-soft ${open ? 'rotate-180' : ''}`} aria-hidden />
        </button>
        {errors.choice && (
          <p id={errorId} className="text-sm text-negative">
            {errors.choice}
          </p>
        )}
      </div>

      {open && (
        <div id={listId} className="flex flex-col gap-2 rounded-xl border border-line p-2">
          <input
            type="search"
            className={field}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('catalog.brand.search')}
            aria-label={t('catalog.brand.search')}
            autoFocus
          />
          <ul className="flex max-h-60 flex-col overflow-y-auto" aria-label={t('catalog.brand.title')}>
            {brands.map((b) => (
              <li key={b.key}>
                <button
                  type="button"
                  aria-pressed={choice === b.key}
                  className="flex min-h-11 w-full items-center gap-3 rounded-lg px-2 text-left text-sm text-ink hover:bg-soft aria-pressed:bg-soft aria-pressed:font-semibold"
                  onClick={() => choose(b.key)}
                >
                  <CardBadge name={b.name} brand={b.key} size="sm" />
                  <span className="min-w-0 flex-1 truncate">{b.name}</span>
                  {choice === b.key && <Check className="size-4 text-brand-text" aria-hidden />}
                </button>
              </li>
            ))}
            {brands.length === 0 && <li className="px-2 py-2 text-sm text-ink-soft">{t('catalog.brand.noMatch')}</li>}
            <li className="mt-1 border-t border-line pt-1">
              <button
                type="button"
                aria-pressed={choice === NEW_CARD_CHOICE}
                className="flex min-h-11 w-full items-center gap-3 rounded-lg px-2 text-left text-sm font-medium text-ink hover:bg-soft aria-pressed:bg-soft"
                onClick={() => choose(NEW_CARD_CHOICE)}
              >
                <Plus className="size-5 text-ink-soft" aria-hidden />
                {t('catalog.brand.newCard')}
              </button>
            </li>
          </ul>
          <p className="px-1 text-xs text-ink-soft">{t('catalog.brand.notice')}</p>
        </div>
      )}

      {choice && (
        <TextField
          label={t(selected ? 'catalog.brand.nickname' : 'catalog.brand.cardName')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={selected?.name}
          error={errors.name}
        />
      )}
    </div>
  )
}
