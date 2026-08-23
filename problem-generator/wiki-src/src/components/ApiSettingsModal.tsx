import { useEffect, useState } from 'react'
import { api } from '../lib/api'
export type ApiProvider = 'auto' | 'gemini' | 'ox-alpha'
export type ApiSettings = { provider: ApiProvider; geminiKey: string; openrouterKey: string }
type Props = { open: boolean; onClose: () => void; onSaved: (settings: ApiSettings) => void }
const storageKey = 'f2r_api_settings'
export default function ApiSettingsModal({ open, onClose, onSaved }: Props) {
  const [provider,setProvider]=useState<ApiProvider>('auto'),[geminiKey,setGeminiKey]=useState(''),[openrouterKey,setOpenrouterKey]=useState(''),[notice,setNotice]=useState('')
  const [availability,setAvailability]=useState<any>(null)
  useEffect(()=>{if(!open)return;try{const s=JSON.parse(sessionStorage.getItem(storageKey)||'{}') as Partial<ApiSettings>;setProvider(s.provider||'auto');setGeminiKey(s.geminiKey||'');setOpenrouterKey(s.openrouterKey||'')}catch{setNotice('저장된 설정을 불러오지 못했습니다.')}api('ai/providers').then(r=>r.json()).then(setAvailability).catch(()=>{})},[open])
  if(!open)return null
  const save=()=>{const settings={provider,geminiKey:geminiKey.trim(),openrouterKey:openrouterKey.trim()};sessionStorage.setItem(storageKey,JSON.stringify(settings));onSaved(settings);onClose()}
  const clearKey=(kind:'gemini'|'openrouter')=>{if(kind==='gemini')setGeminiKey('');else setOpenrouterKey('');const saved=JSON.parse(sessionStorage.getItem(storageKey)||'{}');const settings={provider,geminiKey:kind==='gemini'?'':(saved.geminiKey||''),openrouterKey:kind==='openrouter'?'':(saved.openrouterKey||'')};sessionStorage.setItem(storageKey,JSON.stringify(settings));onSaved(settings);setNotice(`${kind==='gemini'?'Gemini':'OpenRouter'} 브라우저 키를 삭제했습니다.`)}
  const oxServer=availability?.providers?.['ox-alpha']?.serverKeyConfigured, geminiServer=availability?.providers?.gemini?.serverKeyConfigured
  return <div className="modal-backdrop" role="presentation" onMouseDown={onClose}><section className="settings-modal" role="dialog" aria-modal="true" onMouseDown={e=>e.stopPropagation()}>
    <header><div><p className="kicker">AI ROUTER SETTINGS</p><h2>기본 AI 설정</h2><p>브라우저 키와 서버 키는 분리해 판정하며 키 값은 서버에서 다시 표시하지 않습니다.</p></div><button className="modal-close" onClick={onClose}>×</button></header>
    <label className="api-key-field">기본 AI<select value={provider} onChange={e=>setProvider(e.target.value as ApiProvider)}><option value="auto">자동 (Gemini 우선)</option><option value="gemini">Gemini Flash</option><option value="ox-alpha">Ox Alpha (Experimental)</option></select></label>
    <label className="api-key-field">Google Gemini API Key · 브라우저 {geminiKey?'설정됨':'미설정'}<div><input type="password" value={geminiKey} onChange={e=>setGeminiKey(e.target.value)} placeholder={geminiServer?'서버 키 사용 가능 · 브라우저 키 선택':'브라우저 키 선택 입력'} autoComplete="off" /><button className="secondary-button" type="button" onClick={()=>clearKey('gemini')}>키 삭제</button></div></label>
    <label className="api-key-field">OpenRouter API Key · 브라우저 {openrouterKey?'설정됨':'미설정'}<div><input type="password" value={openrouterKey} onChange={e=>setOpenrouterKey(e.target.value)} placeholder={oxServer?'서버 키 사용 가능 · 브라우저 키 선택':'브라우저 키 선택 입력'} autoComplete="off" /><button className="secondary-button" type="button" onClick={()=>clearKey('openrouter')}>키 삭제</button></div></label>
    <p className="settings-help">브라우저 키는 이 탭의 sessionStorage에만 저장되고 요청 때만 FastAPI AI Router로 전달됩니다. 서버 키 상태: Gemini {geminiServer?'설정됨':'미설정'} · Ox Alpha {oxServer?'설정됨':'미설정'}. 키 원문은 DB·로그·응답에 저장하지 않습니다.</p>
    {notice&&<p className="tool-notice settings-notice">{notice}</p>}<footer><button className="secondary-button" onClick={onClose}>취소</button><button className="primary-action" onClick={save}>설정 저장</button></footer>
  </section></div>
}
