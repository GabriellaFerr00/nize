import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login } from '../api/authApi'
import { useAuth } from '../api/authState'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { isEmail } from '../lib/validation'

type LocationState = { from?: string; notice?: string } | null

export function SignInPage() {
  const { t } = useTranslation()
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const state = useLocation().state as LocationState
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (result) => {
      signIn(result.token, result.expiresInSeconds)
      navigate(state?.from ?? '/', { replace: true })
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const next = {
      email: isEmail(email) ? undefined : t('auth.errors.email'),
      password: password ? undefined : t('auth.errors.passwordRequired'),
    }
    setErrors(next)
    if (next.email || next.password) return
    mutation.mutate({ email: email.trim(), password })
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold text-brand-900">{t('auth.signIn.title')}</h1>
      {state?.notice === 'passwordReset' && <FormMessage kind="success">{t('auth.reset.done')}</FormMessage>}
      {mutation.isError && <FormMessage kind="error">{apiErrorMessage(t, mutation.error)}</FormMessage>}
      <TextField
        label={t('auth.fields.email')}
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={errors.email}
      />
      <TextField
        label={t('auth.fields.password')}
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.password}
      />
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-xl bg-brand-600 px-4 py-2.5 font-medium text-white hover:bg-brand-700 disabled:opacity-60"
      >
        {t('auth.signIn.submit')}
      </button>
      <div className="flex flex-col gap-1 text-sm">
        <Link to="/forgot-password" className="text-brand-600 underline">
          {t('auth.signIn.forgot')}
        </Link>
        <Link to="/sign-up" className="text-brand-600 underline">
          {t('auth.signIn.toSignUp')}
        </Link>
      </div>
    </form>
  )
}
