import { TriangleAlert, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { LimitStatus, MonthView } from '../api/monthApi'
import { Money } from './Money'

type Notice = { id: number; categoryId: number; name: string; status: 'NEAR' | 'OVER'; spent: number; limit: number }

/** How long a notice stays before it goes away on its own. */
export const LIMIT_NOTICE_MS = 8000

const rank = (status?: LimitStatus) => (status === 'OVER' ? 2 : status === 'NEAR' ? 1 : 0)

/**
 * Notifies, without staying on the screen, when an entry takes a category near or over its monthly limit
 * (REQ-051, user decision 2026-11-09). It compares the status the API sent before and after the month was fetched
 * again; opening a month only remembers the statuses, so nothing pops up on its own. The summary by category keeps
 * the "used of limit" line.
 */
export function LimitToast({ view }: { view: MonthView }) {
  const { t } = useTranslation()
  const seen = useRef<{ month: string; status: Map<number, LimitStatus | undefined> } | null>(null)
  const [notices, setNotices] = useState<Notice[]>([])

  useEffect(() => {
    const now = new Map(view.summaries.byCategory.map((c) => [c.categoryId, c.limitStatus]))
    const before = seen.current
    seen.current = { month: view.month, status: now }
    if (!before || before.month !== view.month) return
    const fresh: Notice[] = view.summaries.byCategory
      .filter((c) => (c.limitStatus === 'NEAR' || c.limitStatus === 'OVER') && rank(c.limitStatus) > rank(before.status.get(c.categoryId)))
      .map((c) => ({
        id: Date.now() + c.categoryId,
        categoryId: c.categoryId,
        name: c.categoryName ?? '—',
        status: c.limitStatus as 'NEAR' | 'OVER',
        spent: c.amountCents,
        limit: c.limitCents ?? 0,
      }))
    if (fresh.length === 0) return
    // a notification answers the user's own save, so it is shown right away
    setNotices((list) => [...list.filter((n) => !fresh.some((f) => f.categoryId === n.categoryId)), ...fresh])
  }, [view])

  useEffect(() => {
    if (notices.length === 0) return
    const timer = window.setTimeout(() => setNotices((list) => list.slice(1)), LIMIT_NOTICE_MS)
    return () => window.clearTimeout(timer)
  }, [notices])

  return (
    <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-24 z-40 flex flex-col items-end gap-2 md:right-6 md:bottom-6 md:left-auto">
      {notices.map((n) => (
        <div
          key={n.id}
          role="status"
          className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border border-warning/50 bg-card p-3 text-sm text-ink shadow-card"
        >
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden />
          <p className="min-w-0 flex-1">
            <span className="font-medium">{n.name}</span> {t(n.status === 'OVER' ? 'month.limits.over' : 'month.limits.near')}{' '}
            <span className="text-ink-soft">
              (<Money cents={n.spent} /> {t('month.limits.of')} <Money cents={n.limit} />)
            </span>
          </p>
          <button
            type="button"
            onClick={() => setNotices((list) => list.filter((x) => x.id !== n.id))}
            aria-label={t('month.limits.close')}
            className="-m-2 grid size-11 shrink-0 place-items-center rounded-lg text-ink-soft hover:bg-soft hover:text-ink"
          >
            <X className="size-4" aria-hidden />
          </button>
        </div>
      ))}
    </div>
  )
}
