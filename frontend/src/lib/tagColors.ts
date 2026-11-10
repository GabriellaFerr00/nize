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

/** Any color the user picked, as the API stores it: lowercase #rrggbb (REQ-052, 2026-11-09). */
export const isHexColor = (value: unknown): value is string => typeof value === 'string' && /^#[0-9a-f]{6}$/.test(value)

function hslToHex(h: number, s: number, l: number): string {
  const a = (s / 100) * Math.min(l / 100, 1 - l / 100)
  const f = (n: number) => {
    const k = (n + h / 30) % 12
    const c = l / 100 - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))
    return Math.round(255 * c)
      .toString(16)
      .padStart(2, '0')
  }
  return `#${f(0)}${f(8)}${f(4)}`
}

/** 12 hues (every 30°) × 6 tones, darkest first: the "palette of colors and tones" of the color picker. */
export const HUES = [0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330]
export const TONES = [25, 35, 45, 55, 65, 75]
export const TONE_GRID: string[][] = HUES.map((h) => TONES.map((l) => hslToHex(h, 70, l)))
