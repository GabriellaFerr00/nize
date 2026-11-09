import { Eye, EyeOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { setValuesHidden, useValuesHidden } from '../lib/privacy'

/**
 * Eye button that hides or shows every amount on screen (REQ-049). One button named "Ocultar valores", pressed while
 * the values are hidden; the open eye means "values visible", the crossed eye "values hidden".
 */
export function ValuesToggle({ showLabel = false }: { showLabel?: boolean }) {
  const { t } = useTranslation()
  const hidden = useValuesHidden()
  return (
    <button
      type="button"
      aria-pressed={hidden}
      aria-label={showLabel ? undefined : t('privacy.hideValues')}
      onClick={() => setValuesHidden(!hidden)}
      className={`flex min-h-11 shrink-0 items-center gap-3 rounded-xl text-sm font-medium text-ink-soft hover:bg-soft hover:text-ink ${
        showLabel ? 'px-3' : 'w-11 justify-center border border-line-strong'
      }`}
    >
      {hidden ? (
        <EyeOff className="size-5" aria-hidden data-icon="eye-off" />
      ) : (
        <Eye className="size-5" aria-hidden data-icon="eye" />
      )}
      {showLabel && <span className="flex-1 text-left">{t('privacy.hideValues')}</span>}
    </button>
  )
}
