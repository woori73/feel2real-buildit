import { useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api'
import { extractFiles, extractionSummary, joinExtractedPages } from '../lib/ocrExtract'
import { htmlToText, parsePastedQuestions, toContractQuestion, type ParsedQuestion } from '../lib/parseQuestions'
import { normalizeChromeQuestion, type WikiQuestionV1 } from '../lib/wikiQuestion'
import type { StoredQuestion } from '../lib/types'
import QuestionCard from './QuestionCard'

// 교과서 PDF Material 하나를 문항 단위 Question DB로 만든다. PDF→페이지 추출(PR #142)은 이미 됐고,
// 여기서는 page extraction → question segmentation → canonical normalization → Preview → 검수 → 저장.
export type TextbookMaterialRef = { id: number; title: string; publisher: string; lesson: string; grade: string; original_name: string; page_total: number }
type Status = 'ok' | 'review' | 'failed'
type Row = { canonical: WikiQuestionV1; parsed: ParsedQuestion; page: number; question_number: string; status: Status; include: boolean }
type SavedQuestion = StoredQuestion & { source_kind: 'textbook'; page: number; question_number: string; extraction_status: Status; textbook_material_id: number }

const toRow = (parsed: ParsedQuestion, index: number): Row => {
  const canonical = normalizeChromeQuestion(parsed)
  const weak = !parsed.stem.trim() || (parsed.type === 'mc' && parsed.options.length < 2) || Boolean(parsed._structuralIssue)
  return { canonical, parsed, page: 0, question_number: parsed.sourceQuestionNumber || String(index + 1), status: weak ? 'review' : 'ok', include: true }
}
const rowToStored = (row: Row): StoredQuestion => ({
  id: 0, twin_id: null, school: '', grade: '', exam: '', mode: 'textbook' as const, q_kind: row.canonical.kind,
  question_type: row.canonical.question_type, stem: row.canonical.instruction.text, source_text: row.canonical.passage.text,
  options: row.canonical.choices.map(c => c.text), answer: row.canonical.answer.choice_numbers, multi: row.canonical.scoring.multi ? 1 : 0,
  score: row.canonical.scoring.score, conditions: row.canonical.conditions.map(c => c.text), answer_format: row.canonical.scoring.answer_format,
  accepted_answers: row.canonical.answer.accepted_answers, sample_answer: row.canonical.answer.sample_answer,
  grading_rule: row.canonical.explanation.text, exam_title: '', exam_group_id: '', favorite: 0, created_at: new Date().toISOString(),
  canonical: row.canonical, schema_version: 'wiki-question-v1', passage_status: row.canonical.passage.status,
})

export default function TextbookQuestionManager({ material, onClose }: { material: TextbookMaterialRef; onClose: () => void }) {
  const [saved, setSaved] = useState<SavedQuestion[]>([])
  const [rows, setRows] = useState<Row[]>([])
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [stage, setStage] = useState<'list' | 'review'>('list')
  const [editId, setEditId] = useState<number | null>(null)

  const included = useMemo(() => rows.filter(r => r.include), [rows])
  const reviewCount = useMemo(() => included.filter(r => r.status === 'review').length, [included])

  const loadSaved = async () => {
    const r = await api(`textbook-materials/${material.id}/questions`)
    setSaved(r.ok ? (await r.json() as SavedQuestion[]) : [])
  }
  useEffect(() => { void loadSaved() }, [material.id])

  const segment = async (fromOriginal: boolean) => {
    setBusy(true); setNotice('교과서 PDF에서 문항을 분리하는 중입니다…')
    try {
      let text = ''
      let total = material.page_total
      let failedPages = 0
      if (fromOriginal) {
        const orig = await api(`textbook-materials/${material.id}/original`)
        if (!orig.ok) { setNotice('원본 PDF를 불러오지 못했습니다.'); return }
        const file = new File([await orig.blob()], material.original_name, { type: 'application/pdf' })
        const pages = await extractFiles([file], m => setNotice(m))
        const s = extractionSummary(pages)
        total = s.total; failedPages = s.failed
        text = joinExtractedPages(pages)
      } else {
        const content = await api(`textbook-materials/${material.id}/content`)
        text = htmlToText(content.ok ? await content.text() : '')
        if (!text.trim()) { setNotice('저장된 추출 내용이 없습니다. "원본 PDF에서 다시 분리"를 눌러 주세요.'); return }
      }
      const parsed = parsePastedQuestions(text)
      if (!parsed.length) { setNotice('문항 경계를 찾지 못했습니다. 원본 PDF에서 다시 시도하거나 수동으로 나눠 주세요.'); return }
      const next = parsed.map(toRow)
      setRows(next); setStage('review')
      const review = next.filter(r => r.status === 'review').length
      setNotice(`전체 ${total || '?'}쪽${failedPages ? ` (추출 실패 ${failedPages}쪽)` : ''} · 분리된 문항 ${next.length}개 (검수 필요 ${review}개). 확인 후 저장하세요.`)
    } catch (error) {
      setNotice(error instanceof Error ? `분리 실패: ${error.message}` : '문항 분리에 실패했습니다.')
    } finally { setBusy(false) }
  }

  const patch = (i: number, next: Partial<Row>) => setRows(rs => rs.map((r, idx) => idx === i ? { ...r, ...next } : r))
  const patchCanonical = (i: number, mut: (c: WikiQuestionV1) => WikiQuestionV1) => setRows(rs => rs.map((r, idx) => idx === i ? { ...r, canonical: mut(structuredClone(r.canonical)) } : r))
  const mergeUp = (i: number) => setRows(rs => {
    if (i <= 0 || i >= rs.length) return rs
    const merged = normalizeChromeQuestion({
      stem: `${rs[i - 1].parsed.stem}\n${rs[i].parsed.stem}`.trim(),
      passage: `${rs[i - 1].canonical.passage.text}\n${rs[i].canonical.passage.text}`.trim(),
      options: [...rs[i - 1].parsed.options, ...rs[i].parsed.options],
      question_type: rs[i - 1].canonical.question_type, score: rs[i - 1].canonical.scoring.score,
    })
    const next = [...rs]
    next[i - 1] = { ...rs[i - 1], canonical: merged, status: 'review', parsed: { ...rs[i - 1].parsed, stem: merged.instruction.text, options: merged.choices.map(c => c.text) } }
    next.splice(i, 1)
    return next
  })
  const addBlank = () => setRows(rs => [...rs, toRow({ ...rs[0]?.parsed, type: 'sa', stem: '', options: [], answer: [], sourceText: '', sampleAnswer: '', gradingRule: '', questionType: '', conditions: [], answerFormat: '', acceptedAnswers: [], multi: false, score: 0 } as ParsedQuestion, rs.length)])

  const save = async () => {
    if (!included.length) { setNotice('저장할 문항을 선택해 주세요.'); return }
    setBusy(true); setNotice('교과서 문항 DB에 저장하는 중입니다…')
    try {
      const r = await api(`textbook-materials/${material.id}/questions`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          replace: true,
          items: included.map(row => ({ page: row.page, question_number: row.question_number, extraction_status: row.status, question: row.canonical })),
        }),
      })
      const body = await r.json().catch(() => ({}))
      if (!r.ok) { setNotice(body.detail || '저장하지 못했습니다.'); return }
      setNotice(`저장 완료 — 전체 ${body.total} · 정상 ${body.ok} · 검수 ${body.review} · 실패 ${body.failed}`)
      setRows([]); setStage('list'); await loadSaved()
    } catch (error) {
      setNotice(error instanceof Error ? `저장 실패: ${error.message}` : '저장에 실패했습니다.')
    } finally { setBusy(false) }
  }

  const deleteSaved = async (q: SavedQuestion) => {
    if (!window.confirm('이 교과서 문항을 삭제할까요? 되돌릴 수 없습니다.')) return
    const r = await api(`textbook-questions/${q.id}`, { method: 'DELETE' })
    if (!r.ok && r.status !== 204) { setNotice('삭제하지 못했습니다.'); return }
    await loadSaved(); setNotice('문항을 삭제했습니다.')
  }
  const saveEdit = async (q: SavedQuestion, canonical: WikiQuestionV1) => {
    const r = await api(`textbook-questions/${q.id}`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: canonical, extraction_status: 'ok' }),
    })
    if (!r.ok) { setNotice('수정을 저장하지 못했습니다.'); return }
    setEditId(null); await loadSaved(); setNotice('문항을 수정했습니다.')
  }

  return <article className="work-panel textbook-question-manager" aria-label="교과서 문항 DB">
    <div className="panel-heading"><div><p className="kicker">TEXTBOOK QUESTION DB</p><h2>{material.title} · 문항 관리</h2>
      <p>{material.publisher} · {material.lesson} · {material.grade} · 원본 {material.page_total || '?'}쪽</p></div>
      <button className="secondary-button" onClick={onClose}>닫기</button></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')}>×</button></div>}

    {stage === 'list' && <>
      <div className="twin-actions">
        <button className="primary-action" disabled={busy} onClick={() => void segment(false)}>추출 내용에서 문항 분리</button>
        <button className="secondary-button" disabled={busy} onClick={() => void segment(true)}>원본 PDF에서 다시 분리</button>
      </div>
      <h3>저장된 교과서 문항 {saved.length}개{saved.some(q => q.extraction_status === 'review') ? ` · 검수 필요 ${saved.filter(q => q.extraction_status === 'review').length}개` : ''}</h3>
      {saved.length ? <div className="db-list">{saved.map((q, i) => <article key={q.id} style={{ flexWrap: 'wrap' }}>
        <div style={{ flexBasis: '100%' }}>
          <b>p.{q.page || '?'} · {q.question_number || '-'}번 · {q.question_type || (q.q_kind === 'sa' ? '서답형' : '객관식')}
            {q.extraction_status !== 'ok' && <span className="tag-multi" style={{ marginLeft: 6 }}>{q.extraction_status === 'review' ? '검수 필요' : '분리 실패'}</span>}</b>
        </div>
        <div className="f2r-gen embedded-question-view" style={{ flexBasis: '100%' }}><div className="exam-sheet show-answers"><div className="question-list"><QuestionCard q={q} index={i + 1} /></div></div></div>
        <button onClick={() => setEditId(editId === q.id ? null : q.id)}>{editId === q.id ? '편집 닫기' : '수정'}</button>
        <button onClick={() => void deleteSaved(q)}>삭제</button>
        {editId === q.id && <InlineEditor question={q} onSave={c => void saveEdit(q, c)} />}
      </article>)}</div> : <div className="empty-state"><b>아직 분리된 문항이 없습니다.</b><p>위 버튼으로 PDF에서 문항을 분리하세요.</p></div>}
    </>}

    {stage === 'review' && <>
      <p className="manual-found"><b>{rows.length}개 문항으로 분리했습니다.</b> {included.length !== rows.length && <span>(선택 {included.length})</span>} · 검수 필요 {reviewCount}개</p>
      <div className="twin-actions">
        <button className="primary-action" disabled={busy || !included.length} onClick={() => void save()}>{busy ? '저장 중…' : `교과서 문항 DB에 저장 (${included.length}개)`}</button>
        <button className="secondary-button" onClick={addBlank}>빈 문항 추가(잘못 나뉜 문항용)</button>
        <button className="link-button" onClick={() => { setRows([]); setStage('list') }}>취소</button>
      </div>
      <div className="db-list manual-preview">{rows.map((row, i) => <article key={i} style={{ flexWrap: 'wrap' }}>
        <input type="checkbox" aria-label={`문항 ${i + 1} 포함`} checked={row.include} onChange={e => patch(i, { include: e.target.checked })} />
        <div style={{ flex: 1, minWidth: 280 }}>
          <b>문항 {i + 1} · {row.canonical.kind === 'sa' ? '서답형' : '객관식'}
            {row.status === 'review' && <span className="tag-multi" style={{ marginLeft: 6 }}>검수 필요</span>}</b>
          <div className="manual-edit-row">
            <label>페이지<input type="number" min={0} value={row.page} onChange={e => patch(i, { page: Number(e.target.value) || 0 })} /></label>
            <label>문항번호<input value={row.question_number} onChange={e => patch(i, { question_number: e.target.value })} /></label>
            <label>상태<select value={row.status} onChange={e => patch(i, { status: e.target.value as Status })}><option value="ok">정상</option><option value="review">검수 필요</option><option value="failed">분리 실패</option></select></label>
            {i > 0 && <button type="button" onClick={() => mergeUp(i)}>↑ 앞 문항과 합치기</button>}
          </div>
          <label className="manual-edit">발문<textarea rows={2} value={row.canonical.instruction.text} onChange={e => patchCanonical(i, c => ({ ...c, instruction: { text: e.target.value, html: `<p>${e.target.value}</p>` } }))} /></label>
          <label className="manual-edit">지문<textarea rows={3} value={row.canonical.passage.text} onChange={e => patchCanonical(i, c => ({ ...c, passage: { ...c.passage, text: e.target.value, html: `<p>${e.target.value}</p>`, status: e.target.value ? 'complete' : c.passage.status } }))} /></label>
          {row.canonical.kind === 'mc' && <label className="manual-edit">선택지 (줄바꿈 구분)<textarea rows={Math.min(6, row.canonical.choices.length + 1)} value={row.canonical.choices.map(c => c.text).join('\n')} onChange={e => patchCanonical(i, c => ({ ...c, choices: e.target.value.split('\n').map((t, n) => ({ number: n + 1, text: t.trim(), html: t.trim() })).filter(x => x.text) }))} /></label>}
          <div className="manual-edit-row">
            <label>유형<input value={row.canonical.question_type} onChange={e => patchCanonical(i, c => ({ ...c, question_type: e.target.value }))} /></label>
            <label>배점<input type="number" min={0} value={row.canonical.scoring.score} onChange={e => patchCanonical(i, c => ({ ...c, scoring: { ...c.scoring, score: Number(e.target.value) || 0 } }))} /></label>
            <label>정답{row.canonical.kind === 'mc'
              ? <input value={row.canonical.answer.choice_numbers.join(', ')} onChange={e => { const nums = e.target.value.split(/[^\d]+/).map(Number).filter(n => n >= 1); patchCanonical(i, c => ({ ...c, answer: { ...c.answer, choice_numbers: nums }, scoring: { ...c.scoring, multi: nums.length > 1 } })) }} />
              : <input value={row.canonical.answer.sample_answer} onChange={e => patchCanonical(i, c => ({ ...c, answer: { ...c.answer, sample_answer: e.target.value, accepted_answers: e.target.value ? [e.target.value] : [] } }))} />}</label>
          </div>
          <label className="manual-edit">해설<textarea rows={2} value={row.canonical.explanation.text} onChange={e => patchCanonical(i, c => ({ ...c, explanation: { text: e.target.value, html: e.target.value } }))} /></label>
        </div>
        <div className="f2r-gen embedded-question-view" style={{ flexBasis: '100%' }}><div className="exam-sheet show-answers"><div className="question-list"><QuestionCard q={rowToStored(row)} index={i + 1} /></div></div></div>
      </article>)}</div>
    </>}
  </article>
}

function InlineEditor({ question, onSave }: { question: StoredQuestion; onSave: (c: WikiQuestionV1) => void }) {
  const base = question.canonical as WikiQuestionV1
  const [stem, setStem] = useState(base?.instruction?.text || question.stem)
  const [passage, setPassage] = useState(base?.passage?.text || question.source_text)
  const [options, setOptions] = useState((base?.choices?.map(c => c.text) || question.options).join('\n'))
  const [answer, setAnswer] = useState((base?.answer?.choice_numbers || question.answer).join(', '))
  const [sample, setSample] = useState(base?.answer?.sample_answer || question.sample_answer)
  const [explanation, setExplanation] = useState(base?.explanation?.text || question.grading_rule)
  const [score, setScore] = useState(base?.scoring?.score ?? question.score)
  const build = (): WikiQuestionV1 => normalizeChromeQuestion({
    ...base, stem, passage, options: options.split('\n').map(x => x.trim()).filter(Boolean),
    answer: answer.split(/[^\d]+/).map(Number).filter(n => n >= 1), model_answer: sample, sample_answer: sample,
    explanation, score, question_type: base?.question_type || question.question_type, kind: question.q_kind,
  })
  return <div style={{ flexBasis: '100%', display: 'grid', gap: 8, marginTop: 10 }}>
    <label className="manual-edit">발문<textarea rows={2} value={stem} onChange={e => setStem(e.target.value)} /></label>
    <label className="manual-edit">지문<textarea rows={3} value={passage} onChange={e => setPassage(e.target.value)} /></label>
    {question.q_kind === 'mc' && <label className="manual-edit">선택지<textarea rows={4} value={options} onChange={e => setOptions(e.target.value)} /></label>}
    <div className="manual-edit-row">
      <label>배점<input type="number" value={score} onChange={e => setScore(Number(e.target.value) || 0)} /></label>
      <label>정답<input value={question.q_kind === 'mc' ? answer : sample} onChange={e => question.q_kind === 'mc' ? setAnswer(e.target.value) : setSample(e.target.value)} /></label>
    </div>
    <label className="manual-edit">해설<textarea rows={2} value={explanation} onChange={e => setExplanation(e.target.value)} /></label>
    <button className="primary-action" onClick={() => onSave(build())}>수정 저장</button>
  </div>
}
