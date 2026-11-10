import { useQuery } from '@tanstack/react-query'
import { cardLogoDataUrl } from '../api/catalogApi'

type Props = { id: number; name: string; hasLogo?: boolean; size?: 'sm' | 'md' }

/**
 * A card's own image (REQ-052, D-21), or the first letter of its name when there is none. Decoration beside the
 * name, so it is hidden from screen readers.
 */
export function CardBadge({ id, name, hasLogo = false, size = 'md' }: Props) {
  const logo = useQuery({
    queryKey: ['cardLogo', id],
    queryFn: () => cardLogoDataUrl(id),
    enabled: hasLogo,
    staleTime: Infinity,
  })
  const box = size === 'sm' ? 'size-5 text-[10px]' : 'size-8 text-sm'
  if (hasLogo && logo.data) {
    return <img src={logo.data} alt="" aria-hidden className={`${box} shrink-0 rounded-md border border-line bg-card object-contain`} />
  }
  return (
    <span aria-hidden className={`${box} grid shrink-0 place-items-center rounded-md border border-line bg-soft font-semibold text-ink-soft`}>
      {name.trim().charAt(0).toUpperCase()}
    </span>
  )
}
