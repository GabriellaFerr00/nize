import { isHexColor, isTagColor, TAG_DOT_CLASS } from '../lib/tagColors'

/**
 * The color of a category or person as a small dot beside its name (REQ-052); nothing without a color. The outline
 * keeps any color (also a free #rrggbb) visible on both themes; the name next to it carries the meaning.
 */
export function TagDot({ color }: { color?: string | null }) {
  const base = 'inline-block size-2.5 shrink-0 rounded-full border border-line-strong'
  if (isTagColor(color)) return <span aria-hidden data-color={color} className={`${base} ${TAG_DOT_CLASS[color]}`} />
  if (isHexColor(color)) return <span aria-hidden data-color={color} className={base} style={{ backgroundColor: color }} />
  return null
}
