import { useQuery } from '@tanstack/react-query'
import { cardLogoDataUrl } from '../api/catalogApi'
import { cardBrand } from '../lib/cardBrands'

type Props = { id?: number; name: string; hasLogo?: boolean; brand?: string | null; size?: 'sm' | 'md' }

/**
 * How a card is recognised (REQ-052, D-21): the user's own image first, then the chosen Brazilian brand (its CC0 logo
 * or its initials on the brand color), then the first letter of the name. Decoration beside the name, so it is
 * hidden from screen readers.
 */
export function CardBadge({ id, name, hasLogo = false, brand, size = 'md' }: Props) {
  const logo = useQuery({
    queryKey: ['cardLogo', id],
    queryFn: () => cardLogoDataUrl(id as number),
    enabled: hasLogo && id !== undefined,
    staleTime: Infinity,
  })
  const box = size === 'sm' ? 'size-5 text-[9px]' : 'size-8 text-xs'
  if (hasLogo && logo.data) {
    return <img src={logo.data} alt="" aria-hidden className={`${box} shrink-0 rounded-md border border-line bg-card object-contain`} />
  }
  const known = cardBrand(brand)
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
