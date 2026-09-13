const REQUEST_TIMEOUT_MS = 300_000
const WORKSPACE_SELECTION_KEY = 'buildit_admin_wiki_workspace'

export function selectedWorkspace(): string {
  return sessionStorage.getItem(WORKSPACE_SELECTION_KEY) || ''
}

export function selectWorkspace(workspaceId: string): void {
  if (workspaceId) sessionStorage.setItem(WORKSPACE_SELECTION_KEY, workspaceId)
  else sessionStorage.removeItem(WORKSPACE_SELECTION_KEY)
}

// 저장된 원본(blob)을 여는 등 href로 직접 링크할 때 사용한다(api와 같은 base 계약).
export const apiUrl = (path: string) => '/problem-generator/wiki/api/' + path.replace(/^\/+/, '')

export const api = (path: string, init: RequestInit = {}) => {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  const headers = new Headers(init.headers)
  const workspaceId = selectedWorkspace()
  if (workspaceId) headers.set('X-BuildIt-Wiki-Workspace', workspaceId)
  return fetch('/problem-generator/wiki/api/' + path.replace(/^\/+/, ''), { ...init, headers, signal: init.signal || controller.signal })
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
  if (status === 504) message = `${label} 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.`
  else if (status === 429) message = `${label} 요청이 많아 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.`
  else if (status === 502 || !message) message = `${label} 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.`
  return `${message} · provider ${errorProvider} · status ${status}`
}

