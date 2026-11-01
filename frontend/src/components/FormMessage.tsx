import type { ReactNode } from 'react'

export function FormMessage({ kind, children }: { kind: 'error' | 'success'; children: ReactNode }) {
  const style =
    kind === 'error' ? 'border-negative/40 bg-negative/10 text-negative' : 'border-positive/40 bg-positive/10 text-positive'
  return (
    <p role={kind === 'error' ? 'alert' : 'status'} className={`rounded-xl border px-3 py-2 text-sm ${style}`}>
      {children}
    </p>
  )
}
