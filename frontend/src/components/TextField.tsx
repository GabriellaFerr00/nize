import { useId, type InputHTMLAttributes } from 'react'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  label: string
  error?: string | null
}

export function TextField({ label, error, ...input }: Props) {
  const id = useId()
  const errorId = `${id}-error`
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className="rounded-xl border border-line bg-white px-3 py-2.5 text-base text-ink focus:outline-2 focus:outline-brand-500 aria-invalid:border-negative"
        {...input}
      />
      {error && (
        <p id={errorId} className="text-sm text-negative">
          {error}
        </p>
      )}
    </div>
  )
}
