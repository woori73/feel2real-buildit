import { ChangeEvent, useEffect, useRef, useState } from 'react'
import { api, apiUrl, normalizedApiError } from '../lib/api'
import { ExtractedPage, acceptedFileTypes, extractFiles, joinExtractedPages, maxUploadFiles } from '../lib/ocrExtract'
import { Student, StudentWrongNote, WrongMaterial, createWrongNote, fetchStudents, fetchWrongMaterials, fetchWrongNotes } from '../lib/types'
import { buildRestorePayload, countReviewMarks, htmlToPlainText } from '../lib/wrongRestore'

type RestoreResponse = { html: string; canonicalJson: Record<string, unknown>; reviewMarkCount: number; reviewMarkText: string; provider: string; model: string }
type Stage = 'idle' | 'extracting' | 'restoring' | 'review' | 'saving'

// 학생 오답 등록 form의 exam_type 값('중간'/'기말')과 exam_material_sets 관례('중간고사'/'기말고사')를 맞춘다.
const examNameOf = (student: Student | null) => {
  const raw = (student?.exam_type || '').trim()
  if (raw === '중간') return '중간고사'
  if (raw === '기말') return '기말고사'
  return raw
}

// 1차 흐름: 학생 선택 → 사진/PDF 업로드(OCR) → AI 복원 → 미리보기·수정 → 승인 저장.
// 자동 생성 직후 확정 저장하지 않는다. 승인 전에는 서버 DB에 아무것도 쓰지 않는다.
export default function WrongPhotoAiRestore() {
  const [students, setStudents] = useState<Student[]>([])
  const [selectedId, setSelectedId] = useState<string>('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [examLabel, setExamLabel] = useState('')
  const [title, setTitle] = useState('')
  const [pages, setPages] = useState<ExtractedPage[]>([])
  const [files, setFiles] = useState<File[]>([])
  const [stage, setStage] = useState<Stage>('idle')
  const [status, setExtractStatus] = useState('')
  const [restoredHtml, setRestoredHtml] = useState('')
  const [canonicalJson, setCanonicalJson] = useState<Record<string, unknown> | null>(null)
  const [model, setModel] = useState('')
  // 승인 저장 시 exam_material_sets Context(school/grade/year/semester/exam)를 채운다.
  // 기본값은 학생 프로필에서 가져오고, 운영자가 학교 Wiki Context 규칙에 맞게 수정한다.
  const [context, setContext] = useState({ school: '', grade: '', semester: '1학기', exam: '' })
  // 승인 저장 후 즉시 확인: 누적된 오답 목록 + 보관 원본(사진/PDF) 메타데이터.
  const [savedNotes, setSavedNotes] = useState<StudentWrongNote[]>([])
  const [savedMaterials, setSavedMaterials] = useState<WrongMaterial[]>([])
  const [savedLoading, setSavedLoading] = useState(false)
  const [freshNoteId, setFreshNoteId] = useState<number | null>(null)
  const [zoomedMaterialId, setZoomedMaterialId] = useState<number | null>(null)
  const [zoomedPreview, setZoomedPreview] = useState('')
  const canvasRef = useRef<HTMLDivElement>(null)

  const selected = students.find(item => String(item.id) === selectedId) || null
  const reviewMarkCount = countReviewMarks(restoredHtml)
  useEffect(() => { fetchStudents().then(setStudents).catch(() => setError('학생 목록을 불러오지 못했습니다.')) }, [])
  const loadSaved = (studentId: number) => {
    setSavedLoading(true)
    fetchWrongNotes(studentId).then(setSavedNotes).catch(() => setError('저장된 오답 자료를 불러오지 못했습니다.')).finally(() => setSavedLoading(false))
    fetchWrongMaterials(studentId).then(setSavedMaterials).catch(() => setSavedMaterials([]))
  }
  useEffect(() => { if (selectedId) loadSaved(Number(selectedId)); else { setSavedNotes([]); setSavedMaterials([]) } }, [selectedId])
  useEffect(() => {
    if (!selected) return
    setContext({ school: selected.school || '', grade: selected.grade || '', semester: selected.semester || '1학기', exam: examNameOf(selected) })
    setExamLabel(`${new Date().getFullYear()} ${selected.semester || ''} ${examNameOf(selected)}`.replace(/\s+/g, ' ').trim())
  }, [selectedId])

  const chooseStudent = (id: string) => {
    setSelectedId(id)
    setRestoredHtml(''); setCanonicalJson(null); setPages([]); setFiles([]); setStage('idle'); setExtractStatus(''); setError(''); setNotice('')
    setFreshNoteId(null)
  }

  const runRestore = async (extracted: ExtractedPage[], originals: File[]) => {
    if (!extracted.some(page => page.ok && page.text.trim())) {
      setStage('idle')
      setError('추출된 텍스트가 없어 AI 복원을 진행하지 않았습니다. 사진이 선명한지 확인하고 다시 올려 주세요.')
      return
    }
    setStage('restoring'); setExtractStatus('AI가 사진 속 원래 문제 구조를 복원하는 중…')
    try {
      const payload = await buildRestorePayload(extracted, { student: selected?.name || '', school: context.school, exam: context.exam })
      const response = await api('students/wrong-restore', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
      const body = await response.json().catch(() => ({})) as RestoreResponse
      if (!response.ok) throw new Error(await normalizedApiError(response))
      setRestoredHtml(body.html || '')
      setCanonicalJson(body.canonicalJson || null)
      setModel(body.model || '')
      setTitle(prev => prev || `${selected?.name || '학생'} 오답 복원 문제`)
      setStage('review'); setExtractStatus('')
      setNotice(body.reviewMarkCount > 0
        ? `복원 완료 · 검토 필요 표시 ${body.reviewMarkCount}곳 — 노란 표시를 확인·수정한 뒤 저장하세요.`
        : '복원 완료 — 미리보기를 확인·수정한 뒤 승인 저장하세요.')
    } catch (err) {
      setStage('idle'); setExtractStatus('')
      setError(err instanceof Error ? err.message : 'AI 복원에 실패했습니다.')
    }
  }

  const handleUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    const chosen = Array.from(event.target.files || []).slice(0, maxUploadFiles)
    event.target.value = ''
    if (!chosen.length) return
    setError(''); setNotice(''); setRestoredHtml(''); setCanonicalJson(null); setStage('extracting')
    setExtractStatus('원본 사진/PDF에서 문자를 읽는 중…')
    try {
      const extracted = await extractFiles(chosen, message => setExtractStatus(message))
      setPages(extracted); setFiles(chosen)
      await runRestore(extracted, chosen)
    } catch (err) {
      setStage('idle'); setExtractStatus('')
      setError(err instanceof Error ? `추출 실패: ${err.message}` : '추출에 실패했습니다.')
    }
  }

  const saveApproved = async () => {
    if (!selected) { setError('먼저 학생을 선택해 주세요.'); return }
    if (!restoredHtml.trim()) { setError('저장할 복원 결과가 없습니다. 먼저 사진을 업로드해 주세요.'); return }
    setStage('saving'); setError('')
    try {
      // 승인 저장은 기존 계약만 사용한다: exam_material_sets(원본+JSON) + student_wrong_notes(누적).
      // 이 단계에서 AI를 다시 호출하지 않는다(기존 학생 오답 규칙 유지).
      const jsonPayload = JSON.stringify({
        kind: 'wrong-photo-ai-restore', title: title || `${selected.name} 오답 복원 문제`, model,
        pages: pages.map(page => page.name),
        reviewMarkCount: countReviewMarks(restoredHtml),
        restoredHtml, canonicalJson: canonicalJson || {},
      })
      let savedOriginals = 0
      for (const file of files) {
        const form = new FormData()
        form.append('core_type', 'student_wrong_note')
        form.append('school', context.school)
        form.append('grade', context.grade)
        form.append('year', String(new Date().getFullYear()))
        form.append('semester', context.semester)
        form.append('exam', context.exam)
        form.append('student_id', String(selected.id))
        form.append('title', title || `${selected.name} 오답 복원 문제`)
        form.append('original', file)
        form.append('json_text', jsonPayload)
        const response = await api('exam-materials', { method: 'POST', body: form })
        if (response.ok) savedOriginals += 1
      }
      if (files.length && savedOriginals === 0) throw new Error('원본 사진 저장에 실패했습니다. 다시 시도해 주세요.')
      const savedNote = await createWrongNote(selected.id, {
        exam_label: examLabel, title: title || `${selected.name} 오답 복원 문제`, question_type: 'AI 복원',
        question_text: htmlToPlainText(restoredHtml), answer_text: '', source_text: joinExtractedPages(pages),
      })
      // 저장이 실제로 성공한 뒤에만 성공 안내를 노출하고, 저장된 목록을 서버에서 다시 읽어 즉시 표시한다.
      setSavedNotes(current => [savedNote, ...current])
      setFreshNoteId(savedNote.id)
      loadSaved(selected.id)
      setRestoredHtml(''); setCanonicalJson(null); setPages([]); setFiles([]); setStage('idle'); setExtractStatus(''); setTitle('')
      setNotice(`승인 저장 완료 — ${selected.name} 학생 오답으로 누적했습니다. 아래 저장된 오답 자료에서 방금 항목을 확인하세요.${files.length ? ` (원본 ${savedOriginals}/${files.length}건 보관)` : ''}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '승인 저장에 실패했습니다.')
    } finally {
      setStage(prev => (prev === 'saving' ? (restoredHtml ? 'review' : 'idle') : prev))
    }
  }

  return <section className="twin-dashboard wrong-workspace" aria-label="오답 사진 AI 복원">
    <div className="panel-heading"><div><p className="kicker">WRONG PHOTO AI RESTORE</p><h2>오답 사진 AI 복원</h2><p>학생의 오답 사진·PDF에서 AI가 원래 시험 문제 구조를 복원해 편집 가능한 문제로 만들고, 승인 후 학생 오답으로 누적합니다.</p></div><span className="context-chip">{selected ? `선택 · ${selected.name}` : '학생 미선택'}</span></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}
    {error && <div className="tool-notice" role="alert" style={{ background: '#fdecec', color: '#b5222c' }}>{error}<button onClick={() => setError('')} aria-label="오류 닫기">×</button></div>}

    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">STEP 1 · STUDENT</p><h2>학생 선택</h2></div></div>
      <div className="metadata-grid">
        <label>학생<select value={selectedId} onChange={event => chooseStudent(event.target.value)}><option value="">학생 선택</option>{students.map(student => <option key={student.id} value={student.id}>{student.name} · {student.school || '학교 미입력'} · {student.grade || '학년 미입력'}</option>)}</select></label>
        <label>시험명 (선택)<input value={examLabel} onChange={event => setExamLabel(event.target.value)} placeholder="예: 2026 2학기 중간고사" /></label>
      </div>
    </article>

    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">STEP 2 · UPLOAD & RESTORE</p><h2>오답 사진 업로드 · AI 복원</h2><p>사진을 올리면 브라우저 OCR로 원문을 확보한 뒤 AI가 문항 구조를 복원합니다. 저장은 별도 승인 단계에서만 됩니다.</p></div></div>
      <div className="wrong-restore-steps" aria-label="처리 단계"><span className={stage === 'extracting' ? 'active' : ''}>1. 업로드·OCR</span><span className={stage === 'restoring' ? 'active' : ''}>2. AI 복원</span><span className={stage === 'review' || stage === 'saving' ? 'active' : ''}>3. 미리보기·수정</span><span className={stage === 'saving' ? 'active' : ''}>4. 승인·저장</span></div>
      <label className="wrong-upload-btn" style={{ marginTop: 14 }}>📷 오답 사진/PDF 업로드<input type="file" accept={acceptedFileTypes} multiple onChange={event => void handleUpload(event)} /></label>
      <p className="field-hint">JPG · JPEG · PNG · PDF 지원 · 최대 {maxUploadFiles}개 파일 · OCR 텍스트를 원문 기준으로 복원하며 판독이 불확실한 곳은 표시로 남깁니다.</p>
      {(stage === 'extracting' || stage === 'restoring' || status) && <div className="pdf-status" role="status">{stage === 'extracting' || stage === 'restoring' ? '⏳ ' : ''}{status}</div>}
      {pages.length > 0 && <div className="wrong-upload-preview" style={{ marginTop: 14 }}>{pages.map((page, index) => <figure className="preview-doc" key={`${page.name}-${index}`}><span className="preview-doc-frame">{page.previewUrl ? <img src={page.previewUrl} alt={page.name} /> : null}</span><figcaption>{page.name}{page.usedOcr ? ' · OCR' : ''}{page.ok ? '' : ' · 실패'}</figcaption></figure>)}</div>}
    </article>

    {restoredHtml && <article className="work-panel"><div className="panel-heading"><div><p className="kicker">STEP 3 · REVIEW & APPROVE</p><h2>복원 미리보기 · 승인 저장</h2><p>아래 문제지를 직접 수정할 수 있습니다. 승인 저장 전까지 학생 오답에 누적되지 않습니다.{model ? ` · ${model}` : ''}</p></div></div>
      {reviewMarkCount > 0 && <div className="tool-notice" role="alert" style={{ background: '#fff3cd', color: '#7a5b0a' }}>⚠ 검토 필요 표시 {reviewMarkCount}곳 — 판독이 불확실한 부분입니다. 원본 사진과 비교해 수정한 뒤 저장하세요.</div>}
      <div className="metadata-grid"><label>문제지 제목<input value={title} onChange={event => setTitle(event.target.value)} /></label></div>
      <div className="metadata-grid" style={{ gridTemplateColumns: 'repeat(2, minmax(0,1fr))' }}>
        <label>학교<input value={context.school} onChange={event => setContext(current => ({ ...current, school: event.target.value }))} placeholder="예: 압구정중" /></label>
        <label>학년<input value={context.grade} onChange={event => setContext(current => ({ ...current, grade: event.target.value }))} placeholder="예: 2학년" /></label>
        <label>학기<select value={context.semester} onChange={event => setContext(current => ({ ...current, semester: event.target.value }))}><option>1학기</option><option>2학기</option></select></label>
        <label>시험<input value={context.exam} onChange={event => setContext(current => ({ ...current, exam: event.target.value }))} placeholder="예: 중간고사" /></label>
      </div>
      <div ref={canvasRef} className="wrong-review-canvas wrong-restore-canvas" contentEditable suppressContentEditableWarning onInput={event => setRestoredHtml(event.currentTarget.innerHTML)} dangerouslySetInnerHTML={{ __html: restoredHtml }} />
      <details style={{ marginTop: 10 }}><summary className="field-hint">OCR 원문 대조</summary><pre className="wrong-restore-ocr">{joinExtractedPages(pages)}</pre></details>
      <div className="editor-output-actions"><button className="primary-action" disabled={stage === 'saving'} onClick={() => void saveApproved()}>{stage === 'saving' ? '저장 중…' : '승인 · 학생 오답으로 저장'}</button></div>
      <p className="field-hint">저장 위치 · 오답 원본 exam_material_sets, 누적 문제 student_wrong_notes (AI 재호출 없이 저장)</p>
    </article>}

    {selected && <article className="work-panel"><div className="panel-heading"><div><p className="kicker">SAVED WRONG NOTES</p><h2>저장된 오답 자료 · {selected.name}</h2><p>승인 저장한 문제는 이 목록에 즉시 반영되고 새로고침 후에도 유지됩니다. 학생별 오답노트에서도 동일하게 조회됩니다.</p></div></div>
      {savedLoading ? <p className="field-hint">불러오는 중…</p> : savedNotes.length ? <div className="db-list">{savedNotes.map(note => <article key={note.id} style={{ flexWrap: 'wrap' }} className={note.id === freshNoteId ? 'fresh-row' : ''}><div><b>{note.question_type || '문항'} · {note.title || note.wrong_text.slice(0, 40)}</b><p>{note.exam_label || '시험명 미입력'}</p><small>{new Date(note.created_at).toLocaleString('ko-KR')}</small>{note.id === freshNoteId && <span className="fresh-badge">방금 저장</span>}</div><details className="wrong-note-text-details"><summary className="field-hint">복원 텍스트</summary><pre className="wrong-restore-ocr">{note.wrong_text}</pre></details></article>)}</div> : <div className="empty-state"><b>저장된 오답 자료가 없습니다.</b><p>사진을 업로드해 복원한 뒤 승인 저장하면 이곳에 누적됩니다.</p></div>}
      {savedMaterials.length > 0 && <details className="wrong-materials-box" style={{ marginTop: 14 }} open={savedMaterials.length <= 6}><summary>보관 원본 사진/PDF {savedMaterials.length}건 — 클릭해 열기</summary><div className="wrong-material-thumb-grid">{savedMaterials.map(material => { const previewPage = pages.find(page => page.name === material.original_name); const previewSrc = previewPage?.previewUrl || ''; return <figure key={material.id} className="wrong-material-thumb"><button type="button" onClick={() => { setZoomedMaterialId(material.id); setZoomedPreview(previewSrc) }} aria-label={`${material.original_name} 미리보기 확대`} disabled={!previewSrc}>{previewSrc ? <img src={previewSrc} alt={material.original_name} /> : <span className="wrong-material-fileicon">📎</span>}</button><figcaption><a href={apiUrl(`exam-materials/${material.id}/original`)} target="_blank" rel="noreferrer">{material.original_name || '원본'}</a><span>{new Date(material.created_at).toLocaleDateString('ko-KR')}</span></figcaption></figure> })}</div></details>}
    </article>}

    {zoomedMaterialId !== null && <div className="wrong-zoom-overlay" role="dialog" aria-modal="true" aria-label="원본 사진 확대" onClick={() => setZoomedMaterialId(null)}>
      <figure className="wrong-zoom-body" onClick={event => event.stopPropagation()}>
        <figcaption>{savedMaterials.find(material => material.id === zoomedMaterialId)?.original_name || '원본'}{zoomedPreview ? ' — 세션 미리보기' : ''} <a href={apiUrl(`exam-materials/${zoomedMaterialId}/original`)} target="_blank" rel="noreferrer">원본 새 창에서 열기 ↗</a></figcaption>
        {zoomedPreview ? <img src={zoomedPreview} alt="원본 사진 확대" /> : <p className="empty-state">이 세션의 미리보기가 없습니다. 원본은 새 창에서 여세요.</p>}
        <button className="secondary-button" onClick={() => setZoomedMaterialId(null)}>닫기</button>
      </figure>
    </div>}
  </section>
}
