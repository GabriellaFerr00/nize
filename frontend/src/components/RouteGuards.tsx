import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../api/authState'

/** Everything inside needs a signed-in user; others go to /sign-in and come back afterwards. */
export function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}

/**
 * Sign-in and sign-up make no sense for someone already signed in. Right after signing in the
 * session flips first, so this is also where the visitor is sent back to the page they wanted.
 */
export function PublicOnly() {
  const { isAuthenticated } = useAuth()
  const from = (useLocation().state as { from?: string } | null)?.from
  return isAuthenticated ? <Navigate to={from ?? '/'} replace /> : <Outlet />
}
