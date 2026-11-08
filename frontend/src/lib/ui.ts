/**
 * Shared class names for buttons and fields (design 0.2.9), so every screen has the same sizes and states.
 * Colors are only the semantic tokens of styles/index.css (D-20); every action is at least 44px tall (WCAG 2.5.8).
 */

const base =
  'inline-flex min-h-11 items-center justify-center gap-1.5 rounded-xl px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60'

/** Main action of a screen or window (Verde Nize fill). */
export const primaryButton = `${base} bg-brand text-on-brand hover:bg-brand-hover`

/** Second action beside a primary one. */
export const secondaryButton = `${base} border border-line-strong bg-card font-medium text-ink hover:bg-soft`

/** Quiet action, such as Cancel. */
export const quietButton = `${base} font-medium text-ink-soft hover:bg-soft hover:text-ink`

/** Action that deletes something. */
export const dangerButton = `${base} bg-negative text-on-negative`

/** Square icon button; it always needs an `aria-label`. */
export const iconButton =
  'grid size-11 shrink-0 place-items-center rounded-lg text-ink-soft hover:bg-soft hover:text-ink disabled:opacity-60'

/** Text inputs and selects in forms. */
export const field =
  'min-h-11 rounded-xl border border-line-strong bg-card px-3 text-base text-ink aria-invalid:border-negative'

/** Small selects in page headers (month, year, language). */
export const compactSelect = 'min-h-11 rounded-lg border border-line-strong bg-card px-2 text-sm text-ink'

/** Page title, the same on every screen. */
export const pageTitle = 'text-3xl font-bold tracking-tight text-ink sm:text-4xl'
