import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <section>
      <h1 className="text-2xl font-semibold text-brand-900">{t('notFound.title')}</h1>
      <Link to="/" className="mt-3 inline-block text-brand-600 underline">
        {t('notFound.back')}
      </Link>
    </section>
  )
}
