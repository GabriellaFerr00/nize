import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { searchBrands, type CardBrand } from '../lib/cardBrands'
import { field } from '../lib/ui'
import { CardBadge } from './CardBadge'

type Props = { value: string; onChange: (brand: CardBrand | null) => void }

/**
 * The cards most used in Brazil, with a search, so a new card is one tap away (REQ-052). Choosing one is optional;
 * "Nenhuma" keeps the card without a brand.
 */
export function BrandPicker({ value, onChange }: Props) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const brands = searchBrands(query)
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-medium text-ink">{t('catalog.brand.title')}</legend>
      <input
        type="search"
        className={field}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={t('catalog.brand.search')}
        aria-label={t('catalog.brand.search')}
      />
      <div className="grid max-h-56 grid-cols-2 gap-1 overflow-y-auto sm:grid-cols-3" role="radiogroup" aria-label={t('catalog.brand.title')}>
        <label className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg px-2 text-sm text-ink has-[:checked]:bg-soft has-[:checked]:font-semibold">
          <input type="radio" name="card-brand" className="sr-only" checked={value === ''} onChange={() => onChange(null)} />
          <span aria-hidden className="size-5 rounded-md border-2 border-dashed border-line-strong" />
          {t('catalog.brand.none')}
        </label>
        {brands.map((b) => (
          <label
            key={b.key}
            className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg px-2 text-sm text-ink has-[:checked]:bg-soft has-[:checked]:font-semibold has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-focus"
          >
            <input type="radio" name="card-brand" className="sr-only" checked={value === b.key} onChange={() => onChange(b)} />
            <CardBadge name={b.name} brand={b.key} size="sm" />
            <span className="min-w-0 truncate">{b.name}</span>
          </label>
        ))}
        {brands.length === 0 && <p className="col-span-full text-sm text-ink-soft">{t('catalog.brand.noMatch')}</p>}
      </div>
      <p className="text-xs text-ink-soft">{t('catalog.brand.notice')}</p>
    </fieldset>
  )
}
