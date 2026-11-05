import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { listYears } from '../api/yearsApi'
import { monthNames } from '../lib/format'
import { shiftMonth } from '../lib/month'

type Props = { month: string; onChange: (month: string) => void }

const control =
  'rounded-lg border border-line-strong bg-card px-2 py-1.5 text-sm text-ink capitalize'
const arrow = 'grid size-9 shrink-0 place-items-center rounded-lg border border-line bg-card text-ink-soft hover:bg-soft'

/** Previous/next month plus month and year selectors (REQ-040). The year list comes from the API. */
export function MonthPicker({ month, onChange }: Props) {
  const { t, i18n } = useTranslation()
  const years = useQuery({ queryKey: ['years'], queryFn: listYears })
  const year = Number(month.slice(0, 4))
  const monthIndex = month.slice(5)
  // the shown year is offered even before the user has it (opening a month creates its year)
  const yearOptions = [...new Set([...(years.data ?? []).map((y) => y.year), year])].sort((a, b) => a - b)

  return (
    <div className="flex flex-wrap items-center gap-2">
      <button type="button" className={arrow} aria-label={t('month.previous')} onClick={() => onChange(shiftMonth(month, -1))}>
        <ChevronLeft className="size-5" aria-hidden />
      </button>
      <select
        aria-label={t('month.pickMonth')}
        className={control}
        value={monthIndex}
        onChange={(e) => onChange(`${month.slice(0, 4)}-${e.target.value}`)}
      >
        {monthNames(i18n.language).map((name, i) => {
          const value = String(i + 1).padStart(2, '0')
          return (
            <option key={value} value={value}>
              {name}
            </option>
          )
        })}
      </select>
      <select
        aria-label={t('month.pickYear')}
        className={control}
        value={year}
        onChange={(e) => onChange(`${e.target.value}-${monthIndex}`)}
      >
        {yearOptions.map((y) => (
          <option key={y} value={y}>
            {y}
          </option>
        ))}
      </select>
      <button type="button" className={arrow} aria-label={t('month.next')} onClick={() => onChange(shiftMonth(month, 1))}>
        <ChevronRight className="size-5" aria-hidden />
      </button>
    </div>
  )
}
