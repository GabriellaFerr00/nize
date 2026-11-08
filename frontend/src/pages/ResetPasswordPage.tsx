import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { resetPassword } from '../api/authApi'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { passwordProblem } from '../lib/validation'
import { pageTitle, primaryButton } from '../lib/ui'

export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const token = useSearchParams()[0].get('token') ?? ''
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (newPassword: string) => resetPassword(token, newPassword),
    onSuccess: () => navigate('/sign-in', { replace: true, state: { notice: 'passwordReset' } }),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const problem = passwordProblem(password)
    setError(problem ? t(`auth.errors.password.${problem}`) : null)
    if (!problem) mutation.mutate(password)
  }

  if (!token) {
    return (
      <div className="flex flex-col gap-4">
        <FormMessage kind="error">{t('errors.INVALID_RESET_TOKEN')}</FormMessage>
        <Link to="/forgot-password" className="text-sm text-brand-text underline">
          {t('auth.reset.requestNew')}
        </Link>
      </div>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <h1 className={pageTitle}>{t('auth.reset.title')}</h1>
      {mutation.isError && <FormMessage kind="error">{apiErrorMessage(t, mutation.error)}</FormMessage>}
      <TextField
        label={t('auth.fields.newPassword')}
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={error}
      />
      <button
        type="submit"
        disabled={mutation.isPending}
        className={primaryButton}
      >
        {t('auth.reset.submit')}
      </button>
    </form>
  )
}
