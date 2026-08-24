import { useEffect, useState } from 'react'
import { api } from '../lib/api'
export type ApiProvider = string
export type ApiSettings = { provider: ApiProvider }
type Props = { open:boolean; onClose:()=>void; onSaved:(settings:ApiSettings)=>void }
const storageKey='f2r_ai_preference'

export default function ApiSettingsModal({open,onClose,onSaved}:Props){
  const [provider,setProvider]=useState<ApiProvider>('auto')
  const [availability,setAvailability]=useState<any>(null)
  const [models,setModels]=useState<any[]>([])
  useEffect(()=>{
    if(!open)return
    try{setProvider(JSON.parse(localStorage.getItem(storageKey)||'{}').provider||'auto')}catch{/* use auto */}
    Promise.all([api('ai/providers').then(r=>r.json()),api('ai/openrouter/free-models').then(r=>r.ok?r.json():{models:[]})])
      .then(([a,m])=>{setAvailability(a);setModels(m.models||[])}).catch(()=>{})
  },[open])
  if(!open)return null
  const save=()=>{const settings={provider};localStorage.setItem(storageKey,JSON.stringify(settings));onSaved(settings);onClose()}
  const gemini=availability?.providers?.gemini?.serverKeyConfigured
  const openrouter=availability?.providers?.openrouter?.serverKeyConfigured
  return <div className="modal-backdrop" role="presentation" onMouseDown={onClose}><section className="settings-modal" role="dialog" aria-modal="true" onMouseDown={e=>e.stopPropagation()}>
    <header><div><p className="kicker">AI ROUTER SETTINGS</p><h2>기본 AI 설정</h2><p>API 키는 서버에서 안전하게 관리됩니다.</p></div><button className="modal-close" onClick={onClose}>×</button></header>
    <p className="settings-help">Gemini 서버 {gemini?'연결됨':'미연결'} · OpenRouter 서버 {openrouter?'연결됨':'미연결'}</p>
    <label className="api-key-field">기본 AI<select value={provider} onChange={e=>setProvider(e.target.value)}>
      <option value="auto">자동</option><option value="gemini">Gemini Flash</option>
      <option value="openrouter-free">OpenRouter 무료 자동</option><option value="ox-alpha">Ox Alpha Experimental</option>
      {models.map(m=><option key={m.id} value={'openrouter:'+m.id}>{m.name}{m.structuredOutput?' · JSON':''}{m.contextLength?' · '+Math.round(m.contextLength/1000)+'k':''}</option>)}
    </select></label>
    <footer><button className="secondary-button" onClick={onClose}>취소</button><button className="primary-action" onClick={save}>설정 저장</button></footer>
  </section></div>
}

