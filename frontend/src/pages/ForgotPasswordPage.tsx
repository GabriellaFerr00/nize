import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../api/authApi'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { isEmail } from '../lib/validation'

export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const mutation = useMutation({ mutationFn: forgotPassword })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const problem = isEmail(email) ? null : t('auth.errors.email')
    setError(problem)
    if (!problem) mutation.mutate(email.trim())
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold text-brand-900">{t('auth.forgot.title')}</h1>
      <p className="text-sm text-ink-soft">{t('auth.forgot.help')}</p>
      {mutation.isSuccess && <FormMessage kind="success">{t('auth.forgot.sent')}</FormMessage>}
      {mutation.isError && <FormMessage kind="error">{apiErrorMessage(t, mutation.error)}</FormMessage>}
      <TextField
        label={t('auth.fields.email')}
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={error}
      />
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-xl bg-brand-600 px-4 py-2.5 font-medium text-white hover:bg-brand-700 disabled:opacity-60"
      >
        {t('auth.forgot.submit')}
      </button>
      <Link to="/sign-in" className="text-sm text-brand-600 underline">
        {t('auth.forgot.back')}
      </Link>
    </form>
  )
}
