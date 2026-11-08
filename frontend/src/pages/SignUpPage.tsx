import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { login, register } from '../api/authApi'
import { useAuth } from '../api/authState'
import { FormMessage } from '../components/FormMessage'
import { TextField } from '../components/TextField'
import { apiErrorMessage } from '../lib/apiErrorMessage'
import { isEmail, passwordProblem } from '../lib/validation'
import { pageTitle, primaryButton } from '../lib/ui'

export function SignUpPage() {
  const { t } = useTranslation()
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ name?: string; email?: string; password?: string }>({})

  // Creating the account and signing in is one step for the user.
  const mutation = useMutation({
    mutationFn: async (data: { name: string; email: string; password: string }) => {
      await register(data)
      return login({ email: data.email, password: data.password })
    },
    onSuccess: (result) => {
      signIn(result.token, result.expiresInSeconds)
      navigate('/', { replace: true })
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const problem = passwordProblem(password)
    const next = {
      name: name.trim() ? undefined : t('auth.errors.nameRequired'),
      email: isEmail(email) ? undefined : t('auth.errors.email'),
      password: problem ? t(`auth.errors.password.${problem}`) : undefined,
    }
    setErrors(next)
    if (next.name || next.email || next.password) return
    mutation.mutate({ name: name.trim(), email: email.trim(), password })
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <h1 className={pageTitle}>{t('auth.signUp.title')}</h1>
      {mutation.isError && <FormMessage kind="error">{apiErrorMessage(t, mutation.error)}</FormMessage>}
      <TextField
        label={t('auth.fields.name')}
        autoComplete="name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={errors.name}
      />
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
        autoComplete="new-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.password}
      />
      <button
        type="submit"
        disabled={mutation.isPending}
        className={primaryButton}
      >
        {t('auth.signUp.submit')}
      </button>
      <Link to="/sign-in" className="text-sm text-brand-text underline">
        {t('auth.signUp.toSignIn')}
      </Link>
    </form>
  )
}
