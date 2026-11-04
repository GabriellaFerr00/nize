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
 * month. Colors come from the theme tokens.
 */
export default function OverviewChart({ rows, onOpenMonth }: Props) {
  const { t, i18n } = useTranslation()
  const money = (value: unknown) => formatCents(Number(value), i18n.language)
  const open = (_: unknown, index: number) => onOpenMonth(rows[index].month)

  return (
    <ResponsiveContainer width="100%" height={280} initialDimension={{ width: 320, height: 280 }}>
      <BarChart data={rows} margin={{ top: 8, right: 4, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--color-chart-grid)" />
        {/* On narrow screens some month names are skipped instead of overlapping; the bars stay. */}
        <XAxis dataKey="label" tickLine={false} interval="preserveStartEnd" minTickGap={4} fontSize={11} />
        <YAxis
          width={56}
          allowDecimals={false}
          tickLine={false}
          fontSize={11}
          tickFormatter={(value: number) => formatCompactCents(value, i18n.language)}
        />
        <Tooltip formatter={money} cursor={{ fill: 'var(--color-brand-50)' }} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Bar
          dataKey="totalIncomeCents"
          name={t('overview.incomes')}
          fill="var(--color-chart-income)"
          radius={[3, 3, 0, 0]}
          cursor="pointer"
          onClick={open}
        />
        <Bar
          dataKey="totalExpensesCents"
          name={t('overview.expenses')}
          fill="var(--color-chart-expense)"
          radius={[3, 3, 0, 0]}
          cursor="pointer"
          onClick={open}
        />
      </BarChart>
    </ResponsiveContainer>
  )
}
