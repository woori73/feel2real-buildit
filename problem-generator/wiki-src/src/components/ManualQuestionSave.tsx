import { DragEvent, useMemo, useRef, useState } from 'react'
import { saveManualQuestions } from '../lib/types'
import { ParsedQuestion, toContractQuestion } from '../lib/parseQuestions'
import { ImportMeta, ImportResult, importQuestionFile } from '../lib/importQuestions'
import { api } from '../lib/api'
import { isSelfContained, referenceLabel, resolveReferencedPassage } from '../lib/wikiQuestion'

// 강사 문제 DB — 외부(Claude/ChatGPT/MCP)에서 만든 문제 파일을 올려 그대로 저장한다.
// AI 재생성·Digital Twin 분석 없음. 파일 업로드 → N문항 발견 → 문제 확인 → Wiki에 저장.
const YEARS = [new Date().getFullYear(), new Date().getFullYear() - 1, new Date().getFullYear() - 2]

const readContext = () => {
  try {
    const s = JSON.parse(sessionStorage.getItem('buildit_assistant_state') || '{}')
    return { school: s.school || '', grade: s.grade || '', year: Number(s.year) || YEARS[0], semester: s.semester || '2학기', exam: s.exam || '전체' }
  } catch { return { school: '', grade: '', year: YEARS[0], semester: '2학기', exam: '전체' } }
}

type Row = ParsedQuestion & { _include: boolean }

export default function ManualQuestionSave({ onSaved, onClose }: { onSaved: () => void; onClose: () => void }) {
  const [ctx, setCtx] = useState(readContext)
  const [meta, setMeta] = useState<ImportMeta>({})
  const [fileName, setFileName] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [rows, setRows] = useState<Row[]>([])
  const [stage, setStage] = useState<'upload' | 'found' | 'review'>('upload')
  const [pasteOpen, setPasteOpen] = useState(false)
  const [paste, setPaste] = useState('')
  const [advOpen, setAdvOpen] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)
  const [lowConfidence, setLowConfidence] = useState(false)
  const [importNotes, setImportNotes] = useState<string[]>([])
  const fileInput = useRef<HTMLInputElement>(null)

  const included = useMemo(() => rows.filter(r => r._include), [rows])
  const ctxLine = [ctx.school, ctx.grade, ctx.year, ctx.semester, ctx.exam].filter(Boolean).join(' > ') || '현재 Context 없음'
  const missingAnswers = useMemo(() => included.filter(r => r._missingAnswer).length, [included])

  const ingest = (res: ImportResult, name: string) => {
    if (!res.questions.length && !res.lowConfidence) {
      setNotice('파일에서 문항을 찾지 못했습니다. 형식을 확인하거나 "직접 텍스트 붙여넣기"를 이용해 주세요.'); return
    }
    setRows(res.questions.map(q => ({ ...q, _include: true })))
    setMeta(res.meta)
    setLowConfidence(res.lowConfidence)
    setImportNotes(res.notes)
    if (res.meta.title || res.meta.sourceMaterial || res.meta.digitalTwin) setAdvOpen(true)
    setFileName(name)
    setStage(res.lowConfidence ? 'review' : 'found')
    setNotice('')
  }

  const handleFile = async (file: File) => {
    setAnalyzing(true); setStage('upload'); setNotice('')
    try {
      const text = await file.text()
      ingest(importQuestionFile(file.name, text), file.name)
    } catch (e) {
      setNotice(e instanceof Error ? `파일을 읽지 못했습니다: ${e.message}` : '파일을 읽지 못했습니다.')
    } finally { setAnalyzing(false) }
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault(); setDragOver(false)
    const f = e.dataTransfer.files?.[0]
    if (f) void handleFile(f)
  }

  const analyzePaste = () => {
    const res = importQuestionFile('paste.txt', paste)
    if (!res.questions.length) { setNotice('붙여넣은 내용에서 문항을 분리하지 못했습니다.'); return }
    ingest(res, '(붙여넣기)')
  }

  const patch = (i: number, next: Partial<Row>) => setRows(rs => rs.map((r, idx) => idx === i ? { ...r, ...next } : r))

  const save = async () => {
    if (!included.length) { setNotice('저장할 문항을 선택해 주세요.'); return }
    if (lowConfidence) { setNotice('문항 구조를 정확히 인식하지 못해 저장할 수 없습니다. 파일 형식을 확인해 주세요.'); return }
    setSaving(true); setNotice('저장하는 중입니다.')
    try {
      const query=new URLSearchParams({school:ctx.school,grade:ctx.grade,year:String(ctx.year),semester:ctx.semester,exam:ctx.exam}).toString()
      const contextResponse=await api(`workspaces/final/context?${query}`)
      const workspace=contextResponse.ok?await contextResponse.json():{school_prints:[]}
      const prepared=included.map(row=>{
        const q={...row,canonical:row.canonical?structuredClone(row.canonical):undefined}
        if(q.canonical){
          q.canonical.source={...q.canonical.source,...ctx,source_page:meta.sourcePage}
          if(q.canonical.passage.status==='missing'){
            const resolved=resolveReferencedPassage(referenceLabel(q.canonical.instruction.text),workspace.school_prints||[])
            if(resolved){q.canonical.passage={...resolved.value,status:'recovered'};q.canonical.source.source_material_id=resolved.materialId;q.sourceText=resolved.value.text}
          }
          if(!isSelfContained(q.canonical))q._warnings=[...(q._warnings||[]),'passage_missing · REVIEW 저장']
        }
        return q
      })
      const res = await saveManualQuestions({
        school: ctx.school, grade: ctx.grade, year: ctx.year, semester: ctx.semester, exam: ctx.exam,
        exam_title: meta.title?.trim() || '', source_material: meta.sourceMaterial?.trim() || '',
        source_page: meta.sourcePage?.trim() || '', digital_twin: meta.digitalTwin?.trim() || '',
        questions: prepared.map(({ _include, ...q }) => { void _include; return toContractQuestion(q) }),
      })
      setNotice(`✓ ${res.question_count}문항을 강사 문제 DB에 저장했습니다.`)
      onSaved()
      setTimeout(onClose, 700)
    } catch (e) {
      setNotice(e instanceof Error ? e.message : '저장하지 못했습니다.')
    } finally { setSaving(false) }
  }

  return <div className="manual-overlay" role="dialog" aria-modal="true" aria-label="문제 파일 업로드">
    <div className="manual-overlay-backdrop" onClick={onClose} />
    <section className="work-panel manual-modal">
      <div className="panel-heading"><div><p className="kicker">SAVE EXTERNAL QUESTIONS</p><h2>문제 파일 업로드</h2>
        <p>Claude / ChatGPT / MCP 등에서 만든 문제 파일을 올려주세요. AI가 다시 생성하지 않습니다.</p></div>
        <button type="button" className="secondary-button" onClick={onClose}>닫기</button></div>
      <p className="manual-ctx-line">저장 위치 · <b>{ctxLine}</b></p>
      {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}

      {stage === 'upload' && <>
        <div className={`manual-dropzone${dragOver ? ' is-drag' : ''}`}
          onDragOver={e => { e.preventDefault(); setDragOver(true) }}
          onDragLeave={() => setDragOver(false)} onDrop={onDrop}>
          {analyzing ? <p><b>문항 분석 중…</b></p> : <>
            <p><b>문제 파일을 여기에 끌어다 놓으세요</b></p>
            <button type="button" className="primary-action" style={{ marginTop: 0 }} onClick={() => fileInput.current?.click()}>파일 선택</button>
            <p className="field-hint">HTML · JSON · TXT 지원</p>
          </>}
          <input ref={fileInput} type="file" accept=".html,.htm,.json,.txt,text/html,application/json,text/plain"
            style={{ display: 'none' }} onChange={e => { const f = e.target.files?.[0]; e.target.value = ''; if (f) void handleFile(f) }} />
        </div>
        <button type="button" className="link-button manual-paste-toggle" onClick={() => setPasteOpen(v => !v)}>
          {pasteOpen ? '텍스트 붙여넣기 접기' : '직접 텍스트 붙여넣기'}</button>
        {pasteOpen && <>
          <textarea className="manual-paste-area" rows={8} value={paste} onChange={e => setPaste(e.target.value)}
            placeholder={'Q1. 다음 빈칸에 알맞은 단어를 쓰시오.\nThe temperature increased ______ over time.\n정답: gradually\n해설: ...'} />
          <button type="button" className="secondary-button" onClick={analyzePaste} disabled={!paste.trim()}>문항 분석</button>
        </>}
      </>}

      {stage !== 'upload' && <>
        <p className="manual-found">{fileName && <span className="field-hint">파일명: {fileName}</span>}<br />
          <b>{rows.length}문항을 찾았습니다.</b>{included.length !== rows.length && <span> (선택 {included.length})</span>}</p>
        {importNotes.filter(n => !(stage === 'review' && n.includes('정답이 없는 문항')))
          .map((n, i) => <p key={i} className={`manual-import-note${lowConfidence ? ' is-error' : ''}`}>⚠ {n}</p>)}
        {stage === 'review' && missingAnswers > 0 && !lowConfidence &&
          <p className="manual-import-note">⚠ 정답이 없는 문항 {missingAnswers}개 — 그대로 저장하면 정답/해설은 비어 있습니다(학생용 시험지).</p>}
        {lowConfidence && <p className="manual-import-note is-error">문항 구조 인식 실패로 <b>저장이 차단</b>되었습니다. 아래에서 수정하거나 다른 파일을 올려 주세요.</p>}

        <button type="button" className="link-button" onClick={() => setAdvOpen(v => !v)}>{advOpen ? '상세 설정 접기' : '상세 설정'}</button>
        {advOpen && <div className="metadata-grid manual-adv">
          <label>학교<input value={ctx.school} onChange={e => setCtx({ ...ctx, school: e.target.value })} /></label>
          <label>학년<input value={ctx.grade} onChange={e => setCtx({ ...ctx, grade: e.target.value })} /></label>
          <label>시험<input value={ctx.exam} onChange={e => setCtx({ ...ctx, exam: e.target.value })} /></label>
          <label>시험/세트 제목<input value={meta.title || ''} onChange={e => setMeta({ ...meta, title: e.target.value })} /></label>
          <label>출처 학습자료<input value={meta.sourceMaterial || ''} onChange={e => setMeta({ ...meta, sourceMaterial: e.target.value })} /></label>
          <label>출처 페이지<input value={meta.sourcePage || ''} onChange={e => setMeta({ ...meta, sourcePage: e.target.value })} /></label>
          <label>출제자 Digital Twin<input value={meta.digitalTwin || ''} onChange={e => setMeta({ ...meta, digitalTwin: e.target.value })} /></label>
        </div>}

        {stage === 'found' && <div className="twin-actions">
          <button type="button" className="secondary-button" onClick={() => setStage('review')}>문제 확인</button>
          <button type="button" className="primary-action" style={{ marginTop: 0 }} disabled={saving || !included.length || lowConfidence} onClick={() => void save()}>
            {saving ? '저장하는 중…' : `Wiki에 저장 (${included.length}문항)`}</button>
          <button type="button" className="link-button" onClick={() => { setStage('upload'); setRows([]); setFileName(''); setLowConfidence(false); setImportNotes([]) }}>다른 파일</button>
        </div>}

        {stage === 'review' && <>
          <div className="db-list manual-preview">
            {rows.map((r, i) => <article key={i} style={{ flexWrap: 'wrap' }}>
              <input type="checkbox" aria-label={`Q${i + 1} 포함`} checked={r._include} onChange={e => patch(i, { _include: e.target.checked })} />
              <div style={{ flex: 1, minWidth: 260 }}>
                <b>Q{i + 1}. {r.type === 'mc' ? '객관식' : '서답형'} · {r.questionType || '유형 미상'} · {r.score}점
                  {r._warnings && r._warnings.length > 0 && <span className="tag-multi" style={{ marginLeft: 6 }}>{r._warnings.join(', ')}</span>}</b>
                <label className="manual-edit">문제<textarea rows={2} value={r.stem} onChange={e => patch(i, { stem: e.target.value })} /></label>
                {r.options.length > 0 && <label className="manual-edit">선택지 (줄바꿈 구분)
                  <textarea rows={Math.min(6, r.options.length + 1)} value={r.options.join('\n')}
                    onChange={e => patch(i, { options: e.target.value.split('\n').map(x => x.trim()).filter(Boolean) })} /></label>}
                <div className="manual-edit-row">
                  <label>유형<input value={r.questionType} onChange={e => patch(i, { questionType: e.target.value })} /></label>
                  <label>배점<input type="number" min={0} value={r.score} onChange={e => patch(i, { score: Number(e.target.value) || 0 })} /></label>
                  <label>정답{r.type === 'mc'
                    ? <input value={r.answer.join(', ')} placeholder={r._missingAnswer ? '미등록' : '예: 2 또는 2,4'}
                        onChange={e => { const answer = e.target.value.split(/[^\d]+/).map(Number).filter(n => n >= 1 && n <= r.options.length); patch(i, { answer, multi: answer.length > 1, _missingAnswer: answer.length === 0 }) }} />
                    : <input value={r.sampleAnswer} placeholder={r._missingAnswer ? '미등록' : ''}
                        onChange={e => patch(i, { sampleAnswer: e.target.value, acceptedAnswers: e.target.value ? [e.target.value] : [], _missingAnswer: !e.target.value })} />}</label>
                </div>
                <label className="manual-edit">해설 / 채점 기준<textarea rows={2} value={r.gradingRule} placeholder="비어 있음(학생용 파일)" onChange={e => patch(i, { gradingRule: e.target.value })} /></label>
              </div>
            </article>)}
          </div>
          <button className="primary-action" disabled={saving || !included.length || lowConfidence} onClick={() => void save()}>
            {saving ? '저장하는 중…' : lowConfidence ? '저장 불가 (구조 인식 실패)' : `Wiki에 저장 (${included.length}문항)`}</button>
        </>}
      </>}
    </section>
  </div>
}
