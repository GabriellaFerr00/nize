import { cardBrand } from '../lib/cardBrands'

type Props = { name: string; brand?: string | null; size?: 'sm' | 'md' }

/**
 * How a card is recognised (REQ-052, D-21): the brand chosen from the list (its saved logo, or its initials on the
 * brand color), or the first letter of the name. Decoration beside the name, so it is hidden from screen readers.
 */
export function CardBadge({ name, brand, size = 'md' }: Props) {
  const box = size === 'sm' ? 'size-5 text-[9px]' : 'size-8 text-xs'
  const known = cardBrand(brand)
  if (known?.icon && known.fullColor) {
    // the institution's own full-color logo reads on white in both themes
    return (
      <span aria-hidden data-brand={known.key} className={`${box} grid shrink-0 place-items-center rounded-md border border-line bg-white p-0.5`}>
        <img src={`/card-brands/${known.key}.svg`} alt="" className="size-full object-contain" />
      </span>
    )
  }
  if (known) {
    return (
      <span
        aria-hidden
        data-brand={known.key}
        className={`${box} grid shrink-0 place-items-center rounded-md font-bold`}
        style={{ backgroundColor: known.color, color: known.text }}
      >
        {known.icon ? <img src={`/card-brands/${known.key}.svg`} alt="" className="size-3/4" /> : known.initials}
      </span>
    )
  }
  return (
    <span aria-hidden className={`${box} grid shrink-0 place-items-center rounded-md border border-line bg-soft font-semibold text-ink-soft`}>
      {name.trim().charAt(0).toUpperCase()}
    </span>
  )
}
