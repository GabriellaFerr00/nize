import { useEffect, useRef, type ReactNode } from 'react'

type Props = { title: string; onClose: () => void; children: ReactNode }

/**
 * Modal window: closes with Esc or the backdrop; focus goes to the first field. Below 640px it rises from the bottom
 * and takes the full width (easier with one hand); from 640px up it is centered (design 0.2.9).
 */
export function Dialog({ title, onClose, children }: Props) {
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    box.current?.querySelector<HTMLElement>('input, textarea, button')?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-40 flex items-end justify-center bg-backdrop sm:items-center sm:p-4"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div
        ref={box}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="max-h-[92dvh] w-full overflow-y-auto rounded-t-3xl border border-line bg-card px-5 pt-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] shadow-card sm:max-h-full sm:max-w-md sm:rounded-2xl sm:p-6"
      >
        <h2 className="text-xl font-bold tracking-tight text-ink">{title}</h2>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  )
}
