// Cloudflare Pages Function: forwards /api/* to the API on Render (D-15), so the browser only
// talks to its own origin (no CORS, CSP connect-src 'self'). API_ORIGIN is set per environment
// in the Pages project settings, e.g. https://nize-api.onrender.com.

interface Env {
  API_ORIGIN?: string
}

// Headers that describe this hop or the visitor; X-Forwarded-For is rebuilt below so a client
// cannot pick the IP the attempt limits see (REQ-047).
const DROPPED = ['host', 'cookie', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor', 'x-forwarded-for', 'x-forwarded-proto', 'x-real-ip']

export async function onRequest({ request, env }: { request: Request; env: Env }): Promise<Response> {
  const origin = env.API_ORIGIN
  if (!origin || !origin.startsWith('https://')) {
    return Response.json({ code: 'UNKNOWN', fields: {} }, { status: 502 })
  }

  const url = new URL(request.url)
  const target = new URL(url.pathname + url.search, origin)
  const headers = new Headers(request.headers)
  for (const name of DROPPED) headers.delete(name)
  const clientIp = request.headers.get('cf-connecting-ip')
  if (clientIp) headers.set('x-forwarded-for', clientIp)
  headers.set('x-forwarded-proto', 'https')

  const hasBody = request.method !== 'GET' && request.method !== 'HEAD'
  return fetch(target.toString(), {
    method: request.method,
    headers,
    body: hasBody ? request.body : undefined,
    redirect: 'manual',
  })
}
