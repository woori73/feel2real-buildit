import { useEffect, useState } from 'react'
export type ApiProvider = 'openrouter' | 'gemini' | 'openai'
export type ApiSettings = { provider: ApiProvider; openRouterKey: string; geminiKey: string; openaiKey: string }
type Props = { open: boolean; onClose: () => void; onSaved: (settings: ApiSettings) => void }
const storageKey = 'f2r_api_settings'
export default function ApiSettingsModal({ open, onClose, onSaved }: Props) {
  const [provider, setProvider] = useState<ApiProvider>('openrouter')
  const [openRouterKey, setOpenRouterKey] = useState('')
  const [geminiKey, setGeminiKey] = useState('')
  const [openaiKey, setOpenaiKey] = useState('')
  const [notice, setNotice] = useState('')
  const [checking, setChecking] = useState(false)
  useEffect(() => {
    if (!open) return
    try {
      const saved = JSON.parse(sessionStorage.getItem(storageKey) || '{}') as Partial<ApiSettings>
      setProvider(saved.provider || 'openrouter')
      setOpenRouterKey(saved.openRouterKey || '')
      setGeminiKey(saved.geminiKey || '')
      setOpenaiKey(saved.openaiKey || '')
    } catch {
      setNotice('저장된 설정을 불러오지 못했습니다.')
    }
  }, [open])
  if (!open) return null
  const providerLabel = provider === 'openrouter' ? 'OpenRouter' : provider === 'gemini' ? 'Google Gemini' : 'OpenAI'
  const getActiveKey = () => provider === 'openrouter' ? openRouterKey.trim() : provider === 'gemini' ? geminiKey.trim() : openaiKey.trim()
  const checkConnection = async () => {
    const activeKey = getActiveKey()
    if (!activeKey) {
      setNotice(`${providerLabel} API 키를 입력해 주세요.`)
      return
    }
    setChecking(true)
    setNotice('API 연결을 확인하고 있습니다…')
    try {
      const response = provider === 'gemini'
        ? await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(activeKey)}`)
        : await fetch(provider === 'openrouter' ? 'https://openrouter.ai/api/v1/models' : 'https://api.openai.com/v1/models', { headers: { Authorization: `Bearer ${activeKey}` } })
      if (response.ok) setNotice('연결 성공 · API 키를 정상적으로 확인했습니다.')
      else if (response.status === 401 || response.status === 403) setNotice('잘못된 API Key · 키를 다시 확인해 주세요.')
      else setNotice('연결을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } catch {
      setNotice('연결을 확인하지 못했습니다. 네트워크 상태를 확인해 주세요.')
    } finally {
      setChecking(false)
    }
  }
  const save = () => {
    const activeKey = getActiveKey()
    if (!activeKey) {
      setNotice(`${providerLabel} API 키를 입력해 주세요.`)
      return
    }
    try {
      const settings: ApiSettings = { provider, openRouterKey: openRouterKey.trim(), geminiKey: geminiKey.trim(), openaiKey: openaiKey.trim() }
      sessionStorage.setItem(storageKey, JSON.stringify(settings))
      setNotice('이 브라우저 탭에서 API 키 설정을 저장했습니다.')
      onSaved(settings)
      onClose()
    } catch {
      setNotice('설정을 저장하지 못했습니다. 브라우저 상태를 확인해 주세요.')
    }
  }
  return <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
    <section className="settings-modal" role="dialog" aria-modal="true" aria-labelledby="api-settings-title" onMouseDown={event => event.stopPropagation()}>
      <header><div><p className="kicker">AI CONNECTION SETTINGS</p><h2 id="api-settings-title">API Key 설정</h2><p>실제 AI 분석 모드에서 사용할 제공자와 키를 설정합니다.</p></div><button className="modal-close" onClick={onClose} aria-label="설정 닫기">×</button></header>
      <div className="provider-tabs" aria-label="AI 제공자 선택"><button className={provider === 'openrouter' ? 'active' : ''} onClick={() => setProvider('openrouter')}>OpenRouter API</button><button className={provider === 'gemini' ? 'active' : ''} onClick={() => setProvider('gemini')}>Google Gemini API</button><button className={provider === 'openai' ? 'active' : ''} onClick={() => setProvider('openai')}>OpenAI API</button></div>
      <label className="api-key-field">OpenRouter API Key<input type="password" value={openRouterKey} onChange={event => setOpenRouterKey(event.target.value)} placeholder="OpenRouter API 키를 입력해 주세요" autoComplete="off" /></label>
      <label className="api-key-field">Google Gemini API Key<input type="password" value={geminiKey} onChange={event => setGeminiKey(event.target.value)} placeholder="Google Gemini API 키를 입력해 주세요" autoComplete="off" /></label>
      <label className="api-key-field">OpenAI API Key<input type="password" value={openaiKey} onChange={event => setOpenaiKey(event.target.value)} placeholder="OpenAI API 키를 입력해 주세요" autoComplete="off" /></label>
      <p className="settings-help">입력한 키는 이 브라우저 탭에서만 유지되며, 화면에 표시되지 않습니다. 실제 AI 호출 연동 전에는 키를 안전한 서버 환경 변수로 옮겨 주세요.</p>
      {notice && <p className="tool-notice settings-notice" role="status">{notice}</p>}
      <footer><button className="secondary-button" onClick={onClose}>취소</button><button className="secondary-button" onClick={checkConnection} disabled={checking}>{checking ? '연결 확인 중…' : 'API 연결 확인'}</button><button className="primary-action" onClick={save}>설정 저장</button></footer>
    </section>
  </div>
}
