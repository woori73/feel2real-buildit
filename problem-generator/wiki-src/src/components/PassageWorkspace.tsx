import { ChangeEvent, useState } from 'react'
import { ApiSettings } from './ApiSettingsModal'
import { acceptedFileTypes, extractFiles, joinExtractedPages } from '../lib/ocrExtract'
import { PassageAnalysis, PassageMemory, generatePassageAnalysis, generatePassageMemory } from '../lib/types'

type Props = { mode: 'memory' | 'analysis'; apiSettings: ApiSettings | null }

const storedGeminiKey = (settings: ApiSettings | null) => {
  try { return sessionStorage.getItem('f2r_msc_gemini_key')?.trim() || settings?.geminiKey.trim() || '' }
  catch { return settings?.geminiKey.trim() || '' }
}

const downloadHtml = (title: string, body: string) => {
  const escapedTitle = title.replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character] || character))
  const html = `<!doctype html><html lang="ko"><meta charset="utf-8"><title>${escapedTitle}</title><style>@page{size:A4;margin:16mm}body{font-family:Arial,'Malgun Gothic',sans-serif;max-width:900px;margin:30px auto;line-height:1.65;color:#171717}.toolbar{position:sticky;top:0;padding:8px;background:#fff2c8;text-align:center}.sentence{break-inside:avoid;border-bottom:1px solid #bbb;padding:12px 0}.english{font-family:Georgia,serif;font-weight:700}.label{display:inline-block;min-width:90px;color:#315843;font-weight:700}table{border-collapse:collapse;width:100%}td,th{border:1px solid #bbb;padding:10px;vertical-align:top}[contenteditable=true]:focus{outline:2px solid #e5b94f;background:#fff9e8}@media print{.toolbar{display:none}}</style><body><div class="toolbar">문구를 클릭해 수정할 수 있습니다. <button onclick="print()">인쇄</button> <button onclick="saveEdited()">편집 HTML 저장</button></div><main contenteditable="true"><h1>${escapedTitle}</h1>${body}</main><script>function saveEdited(){const blob=new Blob(['<!doctype html>'+document.documentElement.outerHTML],{type:'text/html;charset=utf-8'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='buildit-passage-edited.html';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),500)}</script></body></html>`
  const url = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = `${title.replace(/[\\/:*?"<>|]/g, '_')}.html`; link.click(); URL.revokeObjectURL(url)
}

const safe = (value: string) => value.replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character] || character))

export default function PassageWorkspace({ mode, apiSettings }: Props) {
  const [source, setSource] = useState('')
  const [status, setStatus] = useState('')
  const [busy, setBusy] = useState(false)
  const [memory, setMemory] = useState<PassageMemory | null>(null)
  const [analysis, setAnalysis] = useState<PassageAnalysis | null>(null)
  const key = storedGeminiKey(apiSettings)

  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []); event.target.value = ''
    if (!files.length) return
    setBusy(true); setStatus('파일에서 지문을 읽는 중…')
    try { const pages = await extractFiles(files, setStatus); setSource(joinExtractedPages(pages)); setStatus(`지문 ${pages.length}개 항목을 불러왔습니다.`) }
    catch (error) { setStatus(error instanceof Error ? error.message : '파일을 읽지 못했습니다.') }
    finally { setBusy(false) }
  }

  const generate = async () => {
    if (!source.trim()) { setStatus('영어 지문을 붙여넣거나 파일을 올려 주세요.'); return }
    if (!key) { setStatus('왼쪽 하단 설정에서 Gemini API 키를 등록해 주세요.'); return }
    setBusy(true); setStatus(mode === 'memory' ? '문장을 나누고 자연스러운 해석을 만드는 중…' : '개요·문장 구조·문법·T/F 문제를 만드는 중…')
    try {
      if (mode === 'memory') setMemory(await generatePassageMemory({ api_key: key, source_text: source }))
      else setAnalysis(await generatePassageAnalysis({ api_key: key, source_text: source }))
      setStatus('완료했습니다. 결과를 확인한 뒤 인쇄하거나 내려받을 수 있습니다.')
    } catch (error) { setStatus(error instanceof Error ? error.message : '지문 작업을 완료하지 못했습니다.') }
    finally { setBusy(false) }
  }

  const exportResult = () => {
    if (memory) downloadHtml(memory.title || '지문 암기', `<table><tr><th>English</th><th>한국어 해석</th></tr>${memory.sentences.map(row => `<tr><td>${safe(row.english)}</td><td>${safe(row.korean)}</td></tr>`).join('')}</table>`)
    if (analysis) downloadHtml(analysis.title || '지문 정리', `<p><b>주제</b> ${safe(analysis.topic)}<br><b>요지</b> ${safe(analysis.mainIdea)}<br><b>요약</b> ${safe(analysis.summary)}</p><h2>문장별 정리</h2>${analysis.sentences.map((row, i) => `<section class="sentence"><div class="english">${i + 1}. ${safe(row.slashed)}</div><div><span class="label">① 직독직해</span>${safe(row.directTranslation)}</div><div><span class="label">② 정식 해석</span>${safe(row.translation)}</div><div><span class="label">③ 문법 가이드</span>${safe(row.grammarGuide)}</div></section>`).join('')}<h2>내용일치 문제</h2>${analysis.tfQuestions.map((q, i) => `<p>${i + 1}. [${q.language}] ${safe(q.statement)} ( T / F )</p>`).join('')}<p><b>정답: ${analysis.tfQuestions.map((q, i) => `${i + 1}.${q.answer}`).join(' ')}</b></p>`)
  }

  const resultReady = Boolean(memory || analysis)
  return <section className="twin-dashboard passage-workspace">
    <article className="work-panel passage-input-panel"><div className="panel-heading"><div><p className="kicker">EXTERNAL PASSAGE</p><h2>{mode === 'memory' ? '지문 암기' : '지문 정리 + 내용일치 문제'}</h2><p>영어 텍스트를 붙여넣거나 PDF·이미지를 올려 주세요. 업로드 텍스트는 결과 생성 전에 직접 고칠 수 있습니다.</p></div></div><div className="passage-source-grid"><label className="title-label">영어 지문<textarea value={source} onChange={event => setSource(event.target.value)} placeholder="영어 지문을 여기에 붙여넣으세요." /></label><div><label className="wrong-upload-btn">PDF·이미지 업로드<input type="file" accept={`${acceptedFileTypes},text/plain,.txt`} multiple onChange={event => void upload(event)} /></label><p className="field-hint">PDF · JPG · PNG · 텍스트 지원</p><button className="primary-action" disabled={busy || !source.trim()} onClick={() => void generate()}>{busy ? '처리 중…' : mode === 'memory' ? '영한 암기표 만들기' : '지문 정리와 내용일치 만들기'}</button>{status && <div className="pdf-status" role="status">{status}</div>}</div></div></article>
    {resultReady && <div className="passage-controls twin-actions"><button className="secondary-button" onClick={() => window.print()}>인쇄</button><button className="primary-action" style={{ marginTop: 0 }} onClick={exportResult}>HTML 다운로드</button></div>}
    {memory && <article className="work-panel"><div className="panel-heading"><div><p className="kicker">BILINGUAL MEMORY SHEET</p><h2>{memory.title || '지문 암기'}</h2></div></div><div className="passage-pairs">{memory.sentences.flatMap((row, i) => [<div key={`e-${i}`}><b>{i + 1}.</b> {row.english}</div>, <div key={`k-${i}`}>{row.korean}</div>])}</div></article>}
    {analysis && <><article className="work-panel"><div className="panel-heading"><div><p className="kicker">PASSAGE GUIDE</p><h2>{analysis.title}</h2></div></div><div className="analysis-overview"><div className="analysis-card"><b>주제</b><p>{analysis.topic}</p></div><div className="analysis-card"><b>요지</b><p>{analysis.mainIdea}</p></div><div className="analysis-card"><b>요약</b><p>{analysis.summary}</p></div></div><div className="dense-sentence-list">{analysis.sentences.map((row, i) => <section key={i}><h3>{i + 1}. {row.slashed}</h3><p><b>① 직독직해</b> {row.directTranslation}</p><p><b>② 정식 해석</b> {row.translation}</p><p><b>③ 문법/구문 가이드</b> {row.grammarGuide}</p></section>)}</div></article><article className="work-panel"><div className="panel-heading"><div><p className="kicker">CONTENT MATCH</p><h2>내용일치 문제</h2></div></div><div className="tf-list">{analysis.tfQuestions.map((q, i) => <div className="analysis-card" key={i}>{i + 1}. <span className="context-chip">{q.language}</span> {q.statement} ( T / F )</div>)}</div><p><b>정답 · {analysis.tfQuestions.map((q, i) => `${i + 1}.${q.answer}`).join(' ')}</b></p></article></>}
  </section>
}
