import { isTagColor, TAG_DOT_CLASS } from '../lib/tagColors'

/** The color of a category or person as a small dot beside its name (REQ-052); nothing without a color. */
export function TagDot({ color }: { color?: string | null }) {
  if (!isTagColor(color)) return null
  return <span aria-hidden data-color={color} className={`inline-block size-2.5 shrink-0 rounded-full ${TAG_DOT_CLASS[color]}`} />
}
