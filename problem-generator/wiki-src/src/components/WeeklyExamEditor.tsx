import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../lib/api'
import {
  assignStableIds,
  moveCluster,
  moveQuestion,
  renumberQuestions,
} from '../lib/weeklyExamOps'
import {
  checkViewBoxes,
  describeIssues,
  fixViewBoxes,
  fixViewItems,
  measureColumns,
  runFullCheck,
  type LayoutIssue,
} from '../lib/weeklyExamValidate'

export type WeeklyEditorArtifact = 'student-paper' | 'answer-explanation'

type Props = {
  examId: number
  kind: WeeklyEditorArtifact
  onSaved: () => void
  onClose: () => void
}

/* Weekly artifact 편집 operation. 1차는 AI 호출 없이 사람이 직접 만드는 편집만 담고,
   op 로그를 구조화해 둔다(향후 AI가 같은 op를 재생/생성할 수 있다). */
export type WeeklyEditOp = {
  op: 'text' | 'move' | 'sort'
  question: number
  detail: string
}

/* iframe 안에서 실행되는 편집 부트스트랩. 편집 가능 요소 지정·문항 이동/보기 정렬 툴바·
   키보드 단축키를 담당하고, 실제 히스토리/undo/redo는 부모 컴포넌트가 소유한다. */
export const EDITOR_BOOTSTRAP_SNIPPET = [
  ";(function(){",
  "  var INLINE=/^(BR|U|SPAN|B|I|EM|STRONG|SUB|SUP|A)$/i",
  "  function getQ(el){var n=el.closest('.qblock');if(n&&n.dataset.qnum)return parseInt(n.dataset.qnum,10);return 0}",
  "  function leafish(el){var kids=el.children;for(var i=0;i<kids.length;i++){if(!INLINE.test(kids[i].tagName))return false}return true}",
  "  var doc=document",
  "  doc.querySelectorAll('body *:not(script):not(style)').forEach(function(el){",
  "    if(el.isContentEditable||el.closest('.qe-no-edit'))return",
  "    if(el.classList.contains('choices')||el.classList.contains('choice-item')||el.classList.contains('choice-table')){el.setAttribute('data-qe-sortable','1')}",
  "    if(!leafish(el))return",
  "    if(!el.textContent.trim())return",
  "    el.setAttribute('contenteditable','true')",
  "    el.setAttribute('spellcheck','false')",
  "  })",
  "  doc.querySelectorAll('[data-qe-sortable]').forEach(function(el){",
  "    if(el.closest('.qe-no-edit'))return",
  "    el.setAttribute('contenteditable','false')",
  "  })",
  "  doc.querySelectorAll('.qblock').forEach(function(block){",
  "    if(block.querySelector('.qe-toolbar'))return",
  "    var bar=doc.createElement('div')",
  "    bar.className='qe-toolbar qe-no-edit'",
  "    bar.setAttribute('contenteditable','false')",
  "    bar.innerHTML='<span class=\"qe-qnum\">Q'+(block.dataset.qnum||'?')+'</span>'",
  "      +'<button type=\"button\" data-qe-act=\"up\" title=\"위로\">↑</button>'",
  "      +'<button type=\"button\" data-qe-act=\"down\" title=\"아래로\">↓</button>'",
  "      +'<button type=\"button\" data-qe-act=\"sort\" title=\"보기 자동정렬\">↕</button>'",
  "    block.insertBefore(bar,block.firstChild)",
  "  })",
  "  var inputTimer=null",
  "  doc.addEventListener('input',function(e){",
  "    if(!e.target.closest('.qblock'))return",
  "    clearTimeout(inputTimer)",
  "    inputTimer=setTimeout(function(){parent.postMessage({type:'qe:input'},'*')},500)",
  "  })",
  "  doc.addEventListener('click',function(e){",
  "    var btn=e.target.closest('[data-qe-act]')",
  "    if(!btn)return",
  "    e.preventDefault()",
  "    var block=btn.closest('.qblock')",
  "    var q=getQ(btn)",
  "    if(btn.dataset.qeAct==='up'&&block.previousElementSibling){",
  "      block.parentNode.insertBefore(block,block.previousElementSibling)",
  "      parent.postMessage({type:'qe:op',op:'move',dir:'up',question:q},'*')",
  "    }else if(btn.dataset.qeAct==='down'&&block.nextElementSibling){",
  "      block.parentNode.insertBefore(block.nextElementSibling,block)",
  "      parent.postMessage({type:'qe:op',op:'move',dir:'down',question:q},'*')",
  "    }else if(btn.dataset.qeAct==='sort'){",
  "      var items=[].slice.call(block.querySelectorAll('.choice-item'))",
  "      if(!items.length)return",
  "      var max=0",
  "      items.forEach(function(i){max=Math.max(max,i.getBoundingClientRect().width)})",
  "      items.forEach(function(i){i.style.display='inline-block';i.style.minWidth=Math.ceil(max+2)+'px'})",
  "      parent.postMessage({type:'qe:op',op:'sort',question:q},'*')",
  "    }",
  "  })",
  "  doc.addEventListener('keydown',function(e){",
  "    if((e.metaKey||e.ctrlKey)&&(e.key==='z'||e.key==='Z')){",
  "      e.preventDefault()",
  "      parent.postMessage({type:'qe:undo',shift:e.shiftKey},'*')",
  "      return",
  "    }",
  "    var q=getQ(e.target)",
  "    if(!q)return",
  "    if((e.metaKey||e.ctrlKey)&&e.altKey&&(e.key==='ArrowUp'||e.key==='ArrowDown')){",
  "      e.preventDefault()",
  "      parent.postMessage({type:'qe:op',op:'move',dir:e.key==='ArrowUp'?'up':'down',question:q},'*')",
  "    }",
  "  })",
  "  parent.postMessage({type:'qe:ready'},'*')",
  "})()",
].join('\n')

const ARTIFACT_LABEL = { 'student-paper': '문제지', 'answer-explanation': '정답해설' } as const

export const opsToText = (ops: WeeklyEditOp[]) =>
  ops.map(op => `${op.question ? `Q${op.question} ` : ''}${op.op}: ${op.detail}`.trimEnd()).join('\n')

export const textToOps = (text: string): WeeklyEditOp[] =>
  text.split('\n').filter(Boolean).map(line => {
    const match = /^Q(\d+)\s+(text|move|sort):\s*(.*)$/.exec(line)
    if (match) return { op: match[2] as WeeklyEditOp['op'], question: Number(match[1]), detail: match[3] }
    return { op: 'text' as const, question: 0, detail: line }
  })

export default function WeeklyExamEditor({ examId, kind, onSaved, onClose }: Props) {
  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [history, setHistory] = useState<string[]>([])
  const [cursor, setCursor] = useState(-1)
  const [ops, setOps] = useState<WeeklyEditOp[]>([])
  const [opsOpen, setOpsOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [layout, setLayout] = useState<string[] | null>(null)
  const [layoutOpen, setLayoutOpen] = useState(false)
  const [dirty, setDirty] = useState(false)

  const pushHistory = useCallback((html: string) => {
    setHistory(prev => {
      const next = prev.slice(0, cursor + 1)
      next.push(html)
      if (next.length > 40) next.shift()
      setCursor(next.length - 1)
      return next
    })
    setDirty(true)
  }, [cursor])

  const applyUndoRedo = useCallback((target: number) => {
    const doc = iframeRef.current?.contentDocument
    if (!doc?.body || target < 0 || target >= history.length) return
    doc.body.innerHTML = history[target]
    setCursor(target)
    setDirty(true)
  }, [history])

  const opLog = useCallback((op: WeeklyEditOp) => {
    setOps(prev => {
      const existing = prev.find(item => item.question === op.question && item.op === op.op)
      if (existing) return prev.map(item => item === existing ? { ...item, detail: op.detail || item.detail } : item)
      return [...prev, op]
    })
  }, [])

  const handleMessage = useCallback((event: MessageEvent) => {
    const frame = iframeRef.current
    if (!frame || event.source !== frame.contentWindow) return
    const data = event.data as { type?: string; op?: string; dir?: string; question?: number; shift?: boolean }
    const doc = frame.contentDocument
    if (data.type === 'qe:ready') {
      if (doc?.body) { setHistory([doc.body.innerHTML]); setCursor(0) }
      setLoading(false)
    } else if (data.type === 'qe:input') {
      if (doc?.body) pushHistory(doc.body.innerHTML)
    } else if (data.type === 'qe:op') {
      const question = Number(data.question) || 0
      if (data.op === 'move' && doc?.body) {
        // 이동 즉시 표시 번호를 문서 순서로 재번호화한다(요구사항 4: 이동 → 번호 자동 동기화).
        // 정답/해설은 stable questionId 기준이므로 questionId는 불변, displayNumber만 바뀐다.
        assignStableIds(doc)
        renumberQuestions(doc)
        // 툴바 Q라벨도 새 번호로 갱신
        doc.querySelectorAll('.qe-toolbar .qe-qnum').forEach((label, index) => { label.textContent = `Q${index + 1}` })
      }
      if (doc?.body) pushHistory(doc.body.innerHTML)
      if (data.op === 'move') opLog({ op: 'move', question, detail: `${data.dir === 'up' ? '위로' : '아래로'} 이동(재번호화)` })
      else if (data.op === 'sort') opLog({ op: 'sort', question, detail: '보기 자동정렬' })
    } else if (data.type === 'qe:undo') {
      applyUndoRedo(cursor + (data.shift ? 1 : -1))
    }
  }, [applyUndoRedo, cursor, opLog, pushHistory])

  useEffect(() => {
    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [handleMessage])

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const response = await api(`weekly-exams/${examId}/artifacts/${kind}`)
        if (!response.ok) { setError((await response.json().catch(() => ({}))).detail || 'artifact를 불러오지 못했습니다.'); setLoading(false); return }
        const html = await response.text()
        if (cancelled) return
        const doc = iframeRef.current?.contentDocument
        if (!doc) return
        // 재마운트(닫았다 다시 열기) 시 이전 문서가 남지 않도록 about:blank로 리셋 후 write한다.
        const frame = iframeRef.current
        frame.src = 'about:blank'
        await new Promise(resolve => frame.addEventListener('load', resolve, { once: true }))
        if (cancelled) return
        const fresh = iframeRef.current?.contentDocument
        if (!fresh) return
        fresh.open(); fresh.write(html); fresh.close()
        const script = fresh.createElement('script')
        script.textContent = EDITOR_BOOTSTRAP_SNIPPET
        fresh.body.appendChild(script)
      } catch {
        if (!cancelled) { setError('artifact를 불러오지 못했습니다.'); setLoading(false) }
      }
    }
    void load()
    return () => { cancelled = true }
  }, [examId, kind])

  const [fullCheck, setFullCheck] = useState<LayoutIssue[] | null>(null)
  const [autoFix, setAutoFix] = useState<string[] | null>(null)
  const [autoFixOpen, setAutoFixOpen] = useState(false)
  const [columnReport, setColumnReport] = useState<string | null>(null)

  const runLayoutCheck = useCallback(() => {
    const doc = iframeRef.current?.contentDocument
    if (!doc?.body) return
    setFullCheck(runFullCheck(doc))
    setLayoutOpen(true)
  }, [])

  const save = useCallback(async () => {
    const doc = iframeRef.current?.contentDocument
    if (!doc?.documentElement) return
    setBusy(true); setError('')
    try {
      // 저장 canonical에는 편집 UI(툴바/부트스트랩 script/하이라이트)를 남기지 않는다.
      doc.querySelectorAll('.qe-toolbar').forEach(node => node.remove())
      doc.querySelectorAll('script').forEach(node => node.remove())
      doc.querySelectorAll('[contenteditable]').forEach(node => {
        node.removeAttribute('contenteditable')
        node.removeAttribute('spellcheck')
      })
      const body = kind === 'student-paper'
        ? { studentPaper: doc.documentElement.outerHTML }
        : { answerExplanation: doc.documentElement.outerHTML }
      const response = await api(`weekly-exams/${examId}/artifacts`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      })
      if (!response.ok) { setError((await response.json().catch(() => ({}))).detail || '저장하지 못했습니다.'); return }
      setDirty(false)
      onSaved()
    } catch {
      setError('저장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }, [examId, kind, onSaved])

  const cancel = useCallback(() => {
    if (dirty && !window.confirm('수정 내용이 저장되지 않습니다. 창을 닫을까요?')) return
    onClose()
  }, [dirty, onClose])

  /* 자동 교정: 보기 이탈/줄바꿈 등 deterministic rule만 적용. 내용·정답은 건드리지 않는다. */
  const runAutoFix = useCallback(() => {
    const doc = iframeRef.current?.contentDocument
    if (!doc?.body) return
    const fixed = [...fixViewBoxes(doc), ...fixViewItems(doc)]
    if (fixed.length) {
      pushHistory(doc.body.innerHTML)
      opLog({ op: 'sort', question: 0, detail: `자동 교정 ${fixed.length}건` })
    }
    setAutoFix(fixed)
    setAutoFixOpen(true)
  }, [opLog, pushHistory])

  /* 문항 자동 정렬: DOM 순서대로 1..N 재번호화(qnum 텍스트 포함). questionId는 불변. */
  const runAutoSort = useCallback(() => {
    const doc = iframeRef.current?.contentDocument
    if (!doc?.body) return
    assignStableIds(doc)
    renumberQuestions(doc)
    pushHistory(doc.body.innerHTML)
    opLog({ op: 'move', question: 0, detail: '문항 자동 정렬(재번호화)' })
  }, [opLog, pushHistory])

  /* 페이지 자동 배열: 현재 12열 사용률 리포트. (재배치는 인쇄 CSS 측정 기반 2차 확장 지점) */
  const runPaginateReport = useCallback(() => {
    const doc = iframeRef.current?.contentDocument
    if (!doc?.body) return
    const report = measureColumns(doc)
    setColumnReport(report.length
      ? report.map(item => `${item.label} ${item.usedPercent}%`).join(' · ')
      : 'page/col 구조가 없어 측정 대상 없음 — 단일 흐름 문서')
    setLayoutOpen(true)
  }, [])

  return (
    <div className="weekly-editor-overlay" role="dialog" aria-modal="true" aria-label={`주간 실전 모의고사 ${ARTIFACT_LABEL[kind]} 편집`}>
      <div className="weekly-editor-header qe-no-edit">
        <div className="weekly-editor-title">
          <strong>{ARTIFACT_LABEL[kind]} 편집</strong>
          <span className="weekly-editor-hint">문장 클릭 후 직접 수정 · 문항 ↑↓ 이동 · 보기 자동정렬 · Undo/Redo</span>
        </div>
        <div className="weekly-editor-actions">
          {loading && <span className="weekly-editor-busy">불러오는 중…</span>}
          {busy && <span className="weekly-editor-busy">저장 중…</span>}
          {error && <span className="weekly-editor-error" role="alert">{error}</span>}
          <button type="button" className="btn-undo" onClick={() => applyUndoRedo(cursor - 1)} disabled={loading || busy || cursor <= 0}>실행 취소</button>
          <button type="button" className="btn-redo" onClick={() => applyUndoRedo(cursor + 1)} disabled={loading || busy || cursor < 0 || cursor >= history.length - 1}>다시 실행</button>
          <button type="button" className="btn-layout-check" onClick={runLayoutCheck} disabled={busy || loading}>전체 검사</button>
          <button type="button" className="btn-autofix" onClick={runAutoFix} disabled={busy || loading}>자동 교정</button>
          <button type="button" className="btn-autosort" onClick={runAutoSort} disabled={busy || loading}>문항 자동 정렬</button>
          <button type="button" className="btn-paginate" onClick={runPaginateReport} disabled={busy || loading}>페이지 자동 배열</button>
          <button type="button" className="btn-print" onClick={() => iframeRef.current?.contentWindow?.print()} disabled={busy || loading}>인쇄 미리보기</button>
          <button type="button" className="btn-ops" onClick={() => setOpsOpen(value => !value)} disabled={loading || busy}>편집 로그</button>
          <button type="button" className="btn-save primary-action" onClick={() => void save()} disabled={busy || loading}>저장</button>
          <button type="button" className="btn-cancel" onClick={cancel} disabled={busy}>취소</button>
        </div>
      </div>
      {layoutOpen && (
        <div className="weekly-editor-layout qe-no-edit" role="status">
          <strong>전체 검사 결과</strong>
          <ul className="layout-check-summary">
            {fullCheck && fullCheck.length === 0 && <li>검사 완료 — 17종 항목 이상 없음</li>}
            {fullCheck && describeIssues(fullCheck).map(item => <li key={item}>{item}</li>)}
            {columnReport && <li>열 사용률 — {columnReport}</li>}
          </ul>
          <button type="button" onClick={() => { setLayoutOpen(false); setColumnReport(null) }}>닫기</button>
        </div>
      )}
      {autoFixOpen && (
        <div className="weekly-editor-autofix qe-no-edit" role="status">
          <strong>자동 교정 {autoFix?.length ? `${autoFix.length}건` : '대상 없음'}</strong>
          <ul>
            {autoFix?.map(item => <li key={item}>{item}</li>)}
            {autoFix && autoFix.length === 0 && <li>자동 교정 대상 구조 오류가 없다</li>}
          </ul>
          <div>
            <button type="button" onClick={() => setAutoFixOpen(false)}>적용</button>
            <button type="button" onClick={() => { setAutoFix(null); setAutoFixOpen(false) }}>되돌리기</button>
          </div>
        </div>
      )}
      {opsOpen && (
        <div className="weekly-editor-ops qe-no-edit">
          <strong>편집 op 로그 (구조화)</strong>
          <textarea
            value={opsToText(ops)}
            rows={4}
            readOnly
            onChange={() => undefined}
          />
          <p className="field-hint">1차는 AI 호출 없음. op 로그는 향후 AI 자연어 편집이 같은 op를 재생할 수 있도록 남긴다.</p>
        </div>
      )}
      <iframe ref={iframeRef} title="주간 실전 모의고사 편집" className="weekly-editor-frame" />
    </div>
  )
}
