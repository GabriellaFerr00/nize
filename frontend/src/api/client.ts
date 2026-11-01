import { getStoredToken, notifyUnauthorized } from './session'

/** An API error with the stable code the back-end sends (translated in the UI, D-11). */
export class ApiError extends Error {
  status: number
  code: string
  fields: Record<string, string>

  constructor(status: number, code: string, fields: Record<string, string> = {}) {
    super(code)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

type RequestOptions = { method?: string; body?: unknown; auth?: boolean }

export async function apiRequest<T = void>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = auth ? getStoredToken() : null
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401 && token) notifyUnauthorized()

  const text = await response.text()
  const data: unknown = text ? JSON.parse(text) : undefined

  if (!response.ok) {
    const error = (data ?? {}) as { code?: string; fields?: Record<string, string> }
    throw new ApiError(response.status, error.code ?? 'UNKNOWN', error.fields ?? {})
  }
  return data as T
}
