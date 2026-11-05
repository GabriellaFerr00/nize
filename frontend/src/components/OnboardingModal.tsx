import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchMe, markOnboardingSeen, type Me } from '../api/meApi'

const STEPS = ['log', 'position', 'setup'] as const

/** Short welcome shown once, on the first sign-in (REQ-005). */
export function OnboardingModal() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const me = useQuery({ queryKey: ['me'], queryFn: fetchMe })
  const closeButton = useRef<HTMLButtonElement>(null)

  const dismiss = useMutation({
    mutationFn: markOnboardingSeen,
    // Close right away; if saving fails the welcome simply shows again next time.
    onMutate: () => {
      queryClient.setQueryData<Me>(['me'], (current) => current && { ...current, onboardingSeen: true })
    },
  })

  const open = me.data?.onboardingSeen === false

  useEffect(() => {
    if (!open) return
    closeButton.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') dismiss.mutate()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, dismiss])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-backdrop p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="onboarding-title"
        className="w-full max-w-md rounded-2xl border border-line bg-card p-6 shadow-xl"
      >
        <h2 id="onboarding-title" className="text-xl font-semibold text-ink">
          {t('onboarding.title', { name: me.data?.name })}
        </h2>
        <p className="mt-2 text-sm text-ink-soft">{t('onboarding.intro')}</p>
        <ol className="mt-4 flex flex-col gap-3">
          {STEPS.map((step, index) => (
            <li key={step} className="flex gap-3">
              <span className="grid size-7 shrink-0 place-items-center rounded-full bg-brand text-sm font-semibold text-on-brand">
                {index + 1}
              </span>
              <span className="text-sm text-ink">{t(`onboarding.steps.${step}`)}</span>
            </li>
          ))}
        </ol>
        <button
          ref={closeButton}
          type="button"
          onClick={() => dismiss.mutate()}
          className="mt-6 w-full min-h-11 rounded-xl bg-brand px-4 font-semibold text-on-brand hover:bg-brand-hover"
        >
          {t('onboarding.dismiss')}
        </button>
      </div>
    </div>
  )
}
