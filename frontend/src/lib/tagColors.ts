/**
 * The Nize palette for categories and people (REQ-052, D-21). The API stores the key; the colors live in
 * styles/index.css (--nz-tag-*, light and dark). Full class names here so Tailwind keeps them.
 */
export const TAG_COLORS = ['green', 'teal', 'blue', 'purple', 'pink', 'red', 'orange', 'brown'] as const
export type TagColor = (typeof TAG_COLORS)[number]

export const TAG_DOT_CLASS: Record<TagColor, string> = {
  green: 'bg-tag-green',
  teal: 'bg-tag-teal',
  blue: 'bg-tag-blue',
  purple: 'bg-tag-purple',
  pink: 'bg-tag-pink',
  red: 'bg-tag-red',
  orange: 'bg-tag-orange',
  brown: 'bg-tag-brown',
}

export const isTagColor = (value: unknown): value is TagColor =>
  typeof value === 'string' && (TAG_COLORS as readonly string[]).includes(value)
