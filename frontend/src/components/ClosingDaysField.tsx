import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FormMessage } from './FormMessage'
import { field, secondaryButton } from '../lib/ui'

const LAST_DAY = 31

/** Closing days (REQ-048): chips with remove buttons plus a field to add a day from 1 to 31. */
export function ClosingDaysField({ days, onChange }: { days: number[]; onChange: (days: number[]) => void }) {
  const { t } = useTranslation()
  const [value, setValue] = useState('')
  const [invalid, setInvalid] = useState(false)
  const label = (day: number) => (day === LAST_DAY ? t('settings.lastDay') : t('settings.closingDay', { day }))

  function add() {
    const day = Number(value)
    if (!/^\d+$/.test(value) || day < 1 || day > LAST_DAY || days.includes(day)) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setValue('')
    onChange([...days, day].sort((a, b) => a - b))
  }

  return (
    <div className="flex flex-col gap-2 text-sm text-ink">
      <span id="closing-days-label" className="font-medium">{t('settings.closingDays')}</span>
      {days.length === 0 ? (
        <p className="text-xs text-ink-soft">{t('settings.noClosingDays')}</p>
      ) : (
        <ul aria-labelledby="closing-days-label" className="flex flex-wrap gap-2">
          {days.map((day) => (
            <li key={day} className="flex items-center gap-1 rounded-full border border-line bg-soft py-1 pl-3 pr-1">
              <span>{label(day)}</span>
              <button
                type="button"
                onClick={() => onChange(days.filter((d) => d !== day))}
                className="grid size-8 place-items-center rounded-full text-ink-soft hover:bg-card hover:text-ink"
              >
                <span aria-hidden="true">×</span>
                <span className="sr-only">{t('settings.removeClosingDay', { label: label(day) })}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="flex items-end gap-2">
        <label className="flex w-36 flex-col gap-1 text-xs text-ink-soft">
          {t('settings.newClosingDay')}
          <input
            type="text"
            inputMode="numeric"
            value={value}
            onChange={(e) => setValue(e.target.value.trim())}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                add()
              }
            }}
            className={`${field} w-full`}
          />
        </label>
        <button
          type="button"
          onClick={add}
          className={secondaryButton}
        >
          {t('settings.addClosingDay')}
        </button>
      </div>
      {invalid && <FormMessage kind="error">{t('settings.invalidClosingDay')}</FormMessage>}
    </div>
  )
}
