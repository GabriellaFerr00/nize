import { useQuery } from '@tanstack/react-query'
import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { getOverview } from '../api/overviewApi'
import { listYears } from '../api/yearsApi'
import { FormMessage } from '../components/FormMessage'
import { YearSelect } from '../components/YearSelect'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { formatCents } from '../lib/money'

// The chart library is only downloaded when the overview is opened.
const OverviewChart = lazy(() => import('../components/OverviewChart'))

function monthLabel(month: string, language: string, style: 'short' | 'long') {
  const date = new Date(Number(month.slice(0, 4)), Number(month.slice(5)) - 1, 1)
  return new Intl.DateTimeFormat(language === 'pt' ? 'pt-BR' : 'en-US', { month: style }).format(date)
}

/**
 * Annual overview (REQ-039, REQ-040): expenses and incomes of the 12 months in a chart and the monthly balance in a
 * table. Every number comes from the API; clicking a month opens it.
 */
export function OverviewPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { year: yearParam = '' } = useParams()
  const year = /^\d{4}$/.test(yearParam) ? Number(yearParam) : null

  const years = useQuery({ queryKey: ['years'], queryFn: listYears })
  const overview = useQuery({
    queryKey: ['overview', year],
    queryFn: () => getOverview(year as number),
    enabled: year !== null,
  })

  const unknownYear = year === null || (overview.error instanceof ApiError && overview.error.status === 404)
  const openMonth = (month: string) => navigate(`/month/${month}`)
  const months = overview.data?.months ?? []
  const rows = months.map((m) => ({ ...m, label: monthLabel(m.month, i18n.language, 'short') }))

  return (
    <section className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-ink">
          {t('pages.overview')}
          {year !== null && <span className="ml-2 text-ink-soft">{year}</span>}
        </h1>
        <YearSelect years={years.data ?? []} value={year} onChange={(next) => navigate(`/overview/${next}`)} />
      </div>

      {unknownYear && <FormMessage kind="error">{t('overview.unknownYear')}</FormMessage>}
      {!unknownYear && overview.isError && <FormMessage kind="error">{apiErrorMessage(t, overview.error)}</FormMessage>}
      {overview.isLoading && <p className="text-sm text-ink-soft">{t('overview.loading')}</p>}

      {overview.data && (
        <>
          <figure
            aria-label={t('overview.chart')}
            className="min-w-0 rounded-2xl border border-line bg-card p-3 shadow-card sm:p-4"
          >
            <figcaption className="mb-2 text-sm font-medium text-ink-soft">{t('overview.chart')}</figcaption>
            <Suspense fallback={<div className="h-[280px]" />}>
              <OverviewChart rows={rows} onOpenMonth={openMonth} />
            </Suspense>
          </figure>

          <div
            role="region"
            aria-label={t('overview.table')}
            tabIndex={0}
            className="min-w-0 overflow-x-auto rounded-2xl border border-line bg-card shadow-card"
          >
            <table className="w-full text-xs tabular-nums sm:text-sm">
              <caption className="px-3 pt-3 text-left text-sm font-medium text-ink-soft sm:px-4">
                {t('overview.table')}
              </caption>
              <thead>
                <tr className="border-b border-line text-left text-xs text-ink-soft">
                  <th scope="col" className="px-2 py-2 font-medium sm:px-4">{t('overview.month')}</th>
                  <th scope="col" className="px-1 py-2 text-right font-medium sm:px-2">{t('overview.incomes')}</th>
                  <th scope="col" className="px-1 py-2 text-right font-medium sm:px-2">{t('overview.expenses')}</th>
                  <th scope="col" className="px-2 py-2 text-right font-medium sm:px-4">{t('overview.balance')}</th>
                </tr>
              </thead>
              <tbody>
                {months.map((m) => (
                  <tr key={m.month} className="border-b border-line last:border-0 hover:bg-soft">
                    <th scope="row" className="px-2 py-2 text-left font-medium sm:px-4">
                      <Link
                        to={`/month/${m.month}`}
                        aria-label={t('overview.openMonth', { month: monthLabel(m.month, i18n.language, 'long') })}
                        className="capitalize text-brand-text hover:underline"
                      >
                        {monthLabel(m.month, i18n.language, 'short')}
                      </Link>
                    </th>
                    <td className="px-1 py-2 text-right sm:px-2">{formatCents(m.totalIncomeCents, i18n.language)}</td>
                    <td className="px-1 py-2 text-right sm:px-2">{formatCents(m.totalExpensesCents, i18n.language)}</td>
                    <td className="px-2 py-2 text-right font-medium sm:px-4">
                      <span
                        className={`inline-flex items-center justify-end gap-0.5 ${
                          m.balanceCents < 0 ? 'text-negative' : 'text-positive'
                        }`}
                      >
                        {m.balanceCents !== 0 &&
                          (m.balanceCents < 0 ? (
                            <ArrowDownRight className="size-[1em] shrink-0" aria-hidden />
                          ) : (
                            <ArrowUpRight className="size-[1em] shrink-0" aria-hidden />
                          ))}
                        {formatCents(m.balanceCents, i18n.language)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}
