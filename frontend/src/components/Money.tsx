import { useTranslation } from 'react-i18next'
import { formatCents } from '../lib/money'

type Props = { cents: number; className?: string; tone?: boolean; unsigned?: boolean }

/**
 * An amount the API sent, as "R$ 1.234,56" ("-R$ 300,00" when negative). Display only: no arithmetic (D-1).
 * With `tone`, positive amounts are green and negative ones coral; `unsigned` hides the minus sign when a word
 * beside it already says so ("faltam R$ 300,00").
 */
export function Money({ cents, className = '', tone = false, unsigned = false }: Props) {
  const { t, i18n } = useTranslation()
  const text = formatCents(cents, i18n.language)
  const negative = text.startsWith('-')
  const color = !tone ? '' : negative ? 'text-negative' : cents === 0 ? '' : 'text-positive'
  return (
    <span className={`whitespace-nowrap tabular-nums ${color} ${className}`}>
      {negative && !unsigned ? '-' : ''}
      {t('month.currency')} {negative ? text.slice(1) : text}
    </span>
  )
}
