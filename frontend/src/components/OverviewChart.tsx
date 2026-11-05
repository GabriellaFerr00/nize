import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatCents, formatCompactCents } from '../lib/money'

export type OverviewChartRow = {
  month: string
  label: string
  totalExpensesCents: number
  totalIncomeCents: number
}

type Props = { rows: OverviewChartRow[]; onOpenMonth: (month: string) => void }

/**
 * Expenses and incomes of each month as bars (REQ-039). Only draws what the API sent; clicking a bar opens the
 * month. Colors come from the theme tokens (D-20): in light mode the Verde Nize bars get a darker outline so they
 * keep 3:1 against the card; in dark mode they get a soft glow (decoration only).
 */
export default function OverviewChart({ rows, onOpenMonth }: Props) {
  const { t, i18n } = useTranslation()
  const money = (value: unknown) => formatCents(Number(value), i18n.language)
  const open = (_: unknown, index: number) => onOpenMonth(rows[index].month)

  return (
    <ResponsiveContainer width="100%" height={280} initialDimension={{ width: 320, height: 280 }}>
      <BarChart data={rows} margin={{ top: 8, right: 4, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--nz-chart-grid)" />
        {/* On narrow screens some month names are skipped instead of overlapping; the bars stay. */}
        <XAxis
          dataKey="label"
          tickLine={false}
          axisLine={{ stroke: 'var(--nz-line-strong)' }}
          tick={{ fill: 'var(--nz-ink-soft)' }}
          interval="preserveStartEnd"
          minTickGap={4}
          fontSize={11}
        />
        <YAxis
          width={56}
          allowDecimals={false}
          tickLine={false}
          axisLine={false}
          tick={{ fill: 'var(--nz-ink-soft)' }}
          fontSize={11}
          tickFormatter={(value: number) => formatCompactCents(value, i18n.language)}
        />
        <Tooltip
          formatter={money}
          cursor={{ fill: 'var(--nz-soft)' }}
          contentStyle={{
            background: 'var(--nz-card)',
            border: '1px solid var(--nz-line-strong)',
            borderRadius: 12,
            color: 'var(--nz-ink)',
          }}
          labelStyle={{ color: 'var(--nz-ink)', fontWeight: 600 }}
          itemStyle={{ color: 'var(--nz-ink)' }}
        />
        {/* Legend text in the text color: the Verde Nize of the bars has no contrast as text in light mode. */}
        <Legend
          wrapperStyle={{ fontSize: 12 }}
          formatter={(value: string) => <span style={{ color: 'var(--nz-ink)' }}>{value}</span>}
        />
        <Bar
          dataKey="totalIncomeCents"
          name={t('overview.incomes')}
          fill="var(--nz-chart-income)"
          stroke="var(--nz-chart-income-stroke)"
          strokeWidth={1}
          style={{ filter: 'var(--nz-chart-glow)' }}
          radius={[3, 3, 0, 0]}
          cursor="pointer"
          onClick={open}
        />
        <Bar
          dataKey="totalExpensesCents"
          name={t('overview.expenses')}
          fill="var(--nz-chart-expense)"
          radius={[3, 3, 0, 0]}
          cursor="pointer"
          onClick={open}
        />
      </BarChart>
    </ResponsiveContainer>
  )
}
