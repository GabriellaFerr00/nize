import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { isHexColor, TAG_COLORS, TAG_DOT_CLASS, TONE_GRID } from '../lib/tagColors'

type Props = { value: string; onChange: (color: string) => void }

const ring = 'ring-2 ring-ink ring-offset-2 ring-offset-card'

/**
 * Color of a category or person (REQ-052): the 8 quick colors, or any color from a palette of 12 hues × 6 tones, or
 * the device's color picker. The value is a quick key or lowercase #rrggbb; '' = no color.
 */
export function ColorPicker({ value, onChange }: Props) {
  const { t } = useTranslation()
  const [more, setMore] = useState(isHexColor(value))
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-medium text-ink">{t('catalog.fields.color')}</legend>
      <div className="flex flex-wrap gap-1">
        {(['', ...TAG_COLORS] as const).map((key) => (
          <label
            key={key || 'none'}
            title={t(`catalog.colors.${key || 'none'}`)}
            className="grid size-11 cursor-pointer place-items-center rounded-lg has-[:checked]:bg-soft has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-focus"
          >
            <input
              type="radio"
              name="tag-color"
              value={key}
              checked={value === key}
              onChange={() => onChange(key)}
              className="sr-only"
              aria-label={t(`catalog.colors.${key || 'none'}`)}
            />
            {key ? (
              <span aria-hidden className={`size-6 rounded-full border border-line-strong ${TAG_DOT_CLASS[key]} ${value === key ? ring : ''}`} />
            ) : (
              <span aria-hidden className={`size-6 rounded-full border-2 border-dashed border-line-strong ${value === '' ? ring : ''}`} />
            )}
          </label>
        ))}
      </div>
      <button
        type="button"
        aria-expanded={more}
        onClick={() => setMore(!more)}
        className="self-start rounded-lg py-2 text-sm font-semibold text-brand-text underline-offset-4 hover:underline"
      >
        {t(more ? 'catalog.colors.less' : 'catalog.colors.more')}
      </button>
      {more && (
        <div className="flex flex-col gap-2">
          <div role="radiogroup" aria-label={t('catalog.colors.tones')} className="grid grid-cols-6 gap-1 sm:max-w-80">
            {TONE_GRID.flatMap((tones) =>
              tones.map((hex) => (
                <label key={hex} title={hex} className="grid aspect-square min-h-8 cursor-pointer place-items-center rounded-md has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-focus">
                  <input
                    type="radio"
                    name="tag-color"
                    value={hex}
                    checked={value === hex}
                    onChange={() => onChange(hex)}
                    className="sr-only"
                    aria-label={t('catalog.colors.tone', { hex })}
                  />
                  <span aria-hidden className={`size-full rounded-md border border-line-strong ${value === hex ? ring : ''}`} style={{ backgroundColor: hex }} />
                </label>
              )),
            )}
          </div>
          <label className="flex min-h-11 items-center gap-3 text-sm text-ink">
            <input
              type="color"
              value={isHexColor(value) ? value : '#3f7a1e'}
              onChange={(e) => onChange(e.target.value.toLowerCase())}
              className="h-10 w-14 cursor-pointer rounded-md border border-line-strong bg-card"
            />
            {t('catalog.colors.custom')}
          </label>
        </div>
      )}
    </fieldset>
  )
}
