import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from './api/AuthContext'
import { queryClient } from './api/queryClient'
import './i18n'
import { applyTheme, storedTheme } from './lib/theme'
import { router } from './router'
import './styles/index.css'

// Theme first, so the page does not flash in the wrong colors (D-20).
applyTheme(storedTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
