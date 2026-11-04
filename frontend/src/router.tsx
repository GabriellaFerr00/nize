import { createBrowserRouter, createMemoryRouter, type RouteObject } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { AuthLayout } from './components/AuthLayout'
import { DefaultMonthRedirect, PublicOnly, RequireAuth } from './components/RouteGuards'
import { CardsPage } from './pages/CardsPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { MonthPage } from './pages/MonthPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { OverviewPage } from './pages/OverviewPage'
import { PeoplePage } from './pages/PeoplePage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { SettingsPage } from './pages/SettingsPage'
import { SignInPage } from './pages/SignInPage'
import { SignUpPage } from './pages/SignUpPage'

export const routes: RouteObject[] = [
  {
    element: <PublicOnly />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: '/sign-in', element: <SignInPage /> },
          { path: '/sign-up', element: <SignUpPage /> },
          { path: '/forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
    ],
  },
  // The recovery link works whether or not someone is signed in.
  { element: <AuthLayout />, children: [{ path: '/reset-password', element: <ResetPasswordPage /> }] },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <DefaultMonthRedirect /> },
          { path: '/month/:yyyyMm', element: <MonthPage /> },
          { path: '/overview/:year', element: <OverviewPage /> },
          { path: '/categories', element: <CategoriesPage /> },
          { path: '/cards', element: <CardsPage /> },
          { path: '/people', element: <PeoplePage /> },
          { path: '/settings', element: <SettingsPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)

export function createTestRouter(initialPath: string) {
  return createMemoryRouter(routes, { initialEntries: [initialPath] })
}
