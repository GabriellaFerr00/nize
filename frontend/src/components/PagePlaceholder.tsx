import { useTranslation } from 'react-i18next'

export function PagePlaceholder({ titleKey }: { titleKey: string }) {
  const { t } = useTranslation()
  return (
    <section>
      <h1 className="text-2xl font-semibold text-brand-900">{t(titleKey)}</h1>
      <p className="mt-3 rounded-2xl border border-dashed border-line bg-white p-6 text-ink-soft">
        {t('pages.soon')}
      </p>
    </section>
  )
}
