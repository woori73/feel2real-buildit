const REQUEST_TIMEOUT_MS = 300_000

export const api = (path: string, init: RequestInit = {}) => {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  return fetch('/problem-generator/wiki/api/' + path.replace(/^\/+/, ''), { ...init, signal: init.signal || controller.signal })
    .finally(() => window.clearTimeout(timeout))
}

export async function normalizedApiError(response: Response, provider = 'ai-proxy'): Promise<string> {
  const label = provider === 'ox-alpha' ? 'Ox Alpha' : provider === 'gemini' ? 'Gemini' : 'AI'
  let message = ''
  let errorProvider = provider
  let status = response.status
  try {
    const body = await response.clone().json()
    const detail = body?.detail
    message = typeof detail === 'string' ? detail : detail?.message || body?.message || ''
    errorProvider = detail?.provider || body?.provider || provider
    status = detail?.status || body?.status || status
  } catch { /* nginx/게이트웨이 HTML은 사용자에게 노출하지 않는다. */ }
  if (status === 504) message = `${label} 응답 시간이 초과되었습니다. 다시 시도하거나 Gemini로 전환해 주세요.`
  else if (status === 429) message = `${label} 요청이 많아 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.`
  else if (status === 502 || !message) message = `${label} 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.`
  return `${message} · provider ${errorProvider} · status ${status}`
}

