import { useTranslation } from 'react-i18next'
import type { YearInfo } from '../api/yearsApi'
import { compactSelect } from '../lib/ui'

type Props = { years: YearInfo[]; value: number | null; onChange: (year: number) => void }

export function YearSelect({ years, value, onChange }: Props) {
  const { t } = useTranslation()
  return (
    <label className="flex items-center gap-2 text-sm text-ink-soft">
      {t('catalog.year')}
      <select
        value={value ?? ''}
        onChange={(event) => onChange(Number(event.target.value))}
        className={compactSelect}
      >
        {years.map((y) => (
          <option key={y.year} value={y.year}>
            {y.year}
          </option>
        ))}
      </select>
    </label>
  )
}
