import { useEffect, useRef, type ReactNode } from 'react'

type Props = { title: string; onClose: () => void; children: ReactNode }

/** Modal window: closes with Esc or the backdrop; focus goes to the first field. */
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
      className="fixed inset-0 z-40 grid place-items-center bg-ink/50 p-4"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div
        ref={box}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="max-h-full w-full max-w-md overflow-y-auto rounded-2xl bg-white p-6 shadow-xl"
      >
        <h2 className="text-lg font-semibold text-brand-900">{title}</h2>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  )
}
