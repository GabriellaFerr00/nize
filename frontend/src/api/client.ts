import { getStoredToken, notifyUnauthorized } from './session'

/** An API error with the stable code the back-end sends (translated in the UI, D-11). */
export class ApiError extends Error {
  status: number
  code: string
  fields: Record<string, string>
  /** How many records block the action (e.g. expenses using a category), when the API says so. */
  count?: number

  constructor(status: number, code: string, fields: Record<string, string> = {}, count?: number) {
    super(code)
    this.status = status
    this.code = code
    this.fields = fields
    this.count = count
  }
}

/** Sends raw bytes (a card logo, REQ-052) with their type; same errors as {@link apiRequest}. */
export async function apiUpload(path: string, file: Blob, method = 'PUT'): Promise<void> {
  const token = getStoredToken()
  const headers: Record<string, string> = { 'Content-Type': file.type || 'application/octet-stream' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`/api${path}`, { method, headers, body: file })
  if (response.status === 401 && token) notifyUnauthorized()
  if (!response.ok) {
    const text = await response.text()
    const error = (text ? JSON.parse(text) : {}) as { code?: string; fields?: Record<string, string> }
    throw new ApiError(response.status, error.code ?? 'UNKNOWN', error.fields ?? {})
  }
}

/**
 * Downloads an image the API serves only to its owner and turns it into a `data:` URL, which the page's CSP already
 * allows (img-src 'self' data:). Null when there is none.
 */
export async function apiImageDataUrl(path: string): Promise<string | null> {
  const token = getStoredToken()
  const response = await fetch(`/api${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (response.status === 401 && token) notifyUnauthorized()
  // only the image types the API stores (REQ-052); anything else is not shown
  const type = (response.headers.get('Content-Type') ?? '').split(';')[0].trim()
  if (!response.ok || !['image/png', 'image/jpeg', 'image/webp'].includes(type)) return null
  const blob = new Blob([await response.arrayBuffer()], { type })
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : null)
    reader.onerror = () => resolve(null)
    reader.readAsDataURL(blob)
  })
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
    const error = (data ?? {}) as { code?: string; fields?: Record<string, string>; count?: number }
    throw new ApiError(response.status, error.code ?? 'UNKNOWN', error.fields ?? {}, error.count)
  }
  return data as T
}
