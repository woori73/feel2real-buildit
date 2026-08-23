import { FormEvent, KeyboardEvent, useRef, useState } from 'react'
import type { Page } from '../App'

export type AssistantMessage = { role: 'user' | 'assistant'; content: string; page?: Page; action?: string }
type Props = { messages: AssistantMessage[]; busy: boolean; onRun: (text: string, files: File[]) => void; onNavigate: (page: Page) => void; onDismiss: () => void }

export default function AssistantComposer({ messages, busy, onRun, onNavigate, onDismiss }: Props) {
  const [value, setValue] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const form = useRef<HTMLFormElement>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const composing = useRef(false)
  const resize = () => { const node = textarea.current; if (!node) return; node.style.height = '0'; node.style.height = `${Math.min(node.scrollHeight, 116)}px` }
  const submit = (event?: FormEvent) => { event?.preventDefault(); if ((!value.trim() && !files.length) || busy) return; onRun(value, files); setValue(''); setFiles([]); requestAnimationFrame(resize) }
  const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => { if (event.key === 'Enter' && !event.shiftKey && !composing.current && !event.nativeEvent.isComposing && event.keyCode !== 229) { event.preventDefault(); form.current?.requestSubmit() } }
  return <form ref={form} className="assistant-dock" onSubmit={submit}>
    {files.length > 0 && <div className="attachment-chips">{files.map((file, index) => <span key={`${file.name}-${index}`}>{file.name}<button type="button" aria-label={`${file.name} 제거`} onClick={() => setFiles(current => current.filter((_, itemIndex) => itemIndex !== index))}>×</button></span>)}</div>}
    <div className="assistant-composer"><label className="attach-button" title="자료 첨부"><span aria-hidden="true">＋</span><input type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.html,.json,.txt,.hwp,.hwpx,.docx" onChange={event => setFiles(current => [...current, ...Array.from(event.target.files || [])])} /></label><textarea ref={textarea} rows={1} value={value} onInput={resize} onChange={event => setValue(event.target.value)} onCompositionStart={() => { composing.current = true }} onCompositionEnd={event => { composing.current = false; setValue(event.currentTarget.value) }} onKeyDown={keyDown} aria-label="BuildIt Assistant 요청" placeholder="무엇을 만들거나 기록할까요?" /><button className="send-button" type="submit" aria-label="요청 보내기" disabled={busy || (value.trim().length === 0 && files.length === 0)}><svg aria-hidden="true" viewBox="0 0 24 24" focusable="false"><path d="M12 19V5m-6 6 6-6 6 6" /></svg></button></div>
    {messages.length > 0 && <section className="assistant-thread" aria-live="polite"><header><b>BuildIt Assistant</b><button type="button" onClick={onDismiss}>대화 접기</button></header><div className="assistant-messages">{messages.map((message, index) => <article key={index} className={message.role}><p>{message.content}</p>{message.role === 'assistant' && message.page && message.action && <button type="button" onClick={() => onNavigate(message.page!)}>{message.action} →</button>}</article>)}{busy && <article className="assistant"><p>연결된 기억을 확인하고 있습니다…</p></article>}</div></section>}
  </form>
}
