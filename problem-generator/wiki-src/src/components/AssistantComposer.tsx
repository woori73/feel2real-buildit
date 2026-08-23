import { FormEvent, KeyboardEvent, useRef, useState } from 'react'
import type { Page } from '../App'

export type AssistantReply = { text: string; page: Page; action: string }
type Props = { reply: AssistantReply | null; onRun: (text: string, files: File[]) => void; onNavigate: (page: Page) => void; onDismiss: () => void }

export default function AssistantComposer({ reply, onRun, onNavigate, onDismiss }: Props) {
  const [value, setValue] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const textarea = useRef<HTMLTextAreaElement>(null)
  const resize = () => { const node = textarea.current; if (!node) return; node.style.height = '0'; node.style.height = `${Math.min(node.scrollHeight, 116)}px` }
  const submit = (event?: FormEvent) => { event?.preventDefault(); if (!value.trim()) return; onRun(value, files); setValue(''); setFiles([]); requestAnimationFrame(resize) }
  const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); submit() } }
  return <form className="assistant-dock" onSubmit={submit}>
    {files.length > 0 && <div className="attachment-chips">{files.map((file, index) => <span key={`${file.name}-${index}`}>{file.name}<button type="button" aria-label={`${file.name} 제거`} onClick={() => setFiles(current => current.filter((_, itemIndex) => itemIndex !== index))}>×</button></span>)}</div>}
    <div className="assistant-composer"><label className="attach-button" title="자료 첨부"><span aria-hidden="true">＋</span><input type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.html,.json,.txt,.hwp,.hwpx,.docx" onChange={event => setFiles(current => [...current, ...Array.from(event.target.files || [])])} /></label><textarea ref={textarea} rows={1} value={value} onInput={resize} onChange={event => setValue(event.target.value)} onKeyDown={keyDown} aria-label="BuildIt Assistant 요청" placeholder="무엇을 만들거나 기록할까요?" /><button className="send-button" type="submit" aria-label="요청 보내기" disabled={!value.trim()}><span aria-hidden="true">↑</span></button></div>
    {reply && <div className="assistant-reply" role="status"><p>{reply.text}</p><div><button type="button" className="reply-dismiss" onClick={onDismiss}>닫기</button><button type="button" onClick={() => { onNavigate(reply.page); onDismiss() }}>{reply.action} →</button></div></div>}
  </form>
}
