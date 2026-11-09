import { useId, type InputHTMLAttributes, type Ref } from 'react'
import { field } from '../lib/ui'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  label: string
  error?: string | null
  ref?: Ref<HTMLInputElement>
}

export function TextField({ label, error, ref, ...input }: Props) {
  const id = useId()
  const errorId = `${id}-error`
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <input
        ref={ref}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={field}
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
