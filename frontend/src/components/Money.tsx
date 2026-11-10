import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { formatCents } from '../lib/money'
import { HIDDEN_DIGITS, useValuesHidden } from '../lib/privacy'

type Props = { cents: number; className?: string; tone?: boolean; unsigned?: boolean; compactCurrency?: boolean }

/**
 * An amount the API sent, as "R$ 1.234,56" ("-R$ 300,00" when negative). Display only: no arithmetic (D-1).
 * With `tone`, positive amounts are green with an up arrow and negative ones coral with a down arrow, so color is
 * never the only cue. `unsigned` hides the minus sign and the arrow when a word beside it already says so
 * ("faltam R$ 300,00"). `compactCurrency` hides the "R$" below 640px (screen readers still read it), for
 * narrow tables. While the values are hidden (REQ-049) only "R$ ••••" shows, without sign, color or arrow, and screen
 * readers hear "valor oculto".
 */
export function Money({ cents, className = '', tone = false, unsigned = false, compactCurrency = false }: Props) {
  const { t, i18n } = useTranslation()
  const hidden = useValuesHidden()
  if (hidden) {
    return (
      <span className={`inline-flex items-center whitespace-nowrap ${className}`}>
        <span aria-hidden>
          {compactCurrency ? <span className="max-sm:hidden">{t('month.currency')} </span> : `${t('month.currency')} `}
          {HIDDEN_DIGITS}
        </span>
        <span className="sr-only">{t('privacy.hidden')}</span>
      </span>
    )
  }
  const text = formatCents(cents, i18n.language)
  const negative = text.startsWith('-')
  const color = !tone ? '' : negative ? 'text-negative' : cents === 0 ? '' : 'text-positive'
  const Arrow = tone && !unsigned && cents !== 0 ? (negative ? ArrowDownRight : ArrowUpRight) : null
  return (
    <span className={`inline-flex items-center whitespace-nowrap tabular-nums ${color} ${className}`}>
      {Arrow && <Arrow className="mr-0.5 size-[1em] shrink-0" aria-hidden />}
      {negative && !unsigned ? '-' : ''}
      {compactCurrency ? <span className="max-sm:sr-only">{t('month.currency')} </span> : `${t('month.currency')} `}
      {negative ? text.slice(1) : text}
    </span>
  )
}
