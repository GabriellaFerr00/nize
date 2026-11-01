import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { vi } from 'vitest'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from '../api/AuthContext'
import { storeSession } from '../api/session'
import { createTestRouter } from '../router'

/** Renders the whole app at a path with fresh providers; optionally already signed in. */
export function renderApp(path: string, options: { signedIn?: boolean } = {}) {
  if (options.signedIn) storeSession('test-token', 3600)
  const router = createTestRouter(path)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

type Reply = { status?: number; body?: unknown }

/** Replaces fetch with a stub that answers by "METHOD /path"; returns the recorded calls. */
export function stubApi(replies: Record<string, Reply>) {
  const calls: { key: string; body: unknown; headers: Record<string, string> }[] = []
  const stub = async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url}`
    calls.push({
      key,
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
      headers: (init?.headers ?? {}) as Record<string, string>,
    })
    const reply = replies[key] ?? { status: 404, body: { code: 'NOT_STUBBED' } }
    const text = reply.body === undefined ? '' : JSON.stringify(reply.body)
    // 204 (and other null-body statuses) must not carry a body, not even an empty string
    return new Response(text || null, { status: reply.status ?? 200 })
  }
  vi.stubGlobal('fetch', vi.fn(stub))
  return calls
}
