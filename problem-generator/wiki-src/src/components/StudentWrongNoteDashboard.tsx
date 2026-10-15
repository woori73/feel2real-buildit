import { ChangeEvent, useEffect, useState } from 'react'
import { apiUrl } from '../lib/api'
import { ApiSettings } from './ApiSettingsModal'
import { Student, StudentWrongNote, cleanOcrText, createStudent, createWrongNote, deleteStudent, deleteWrongNote, exportWrongNotesOffice, fetchStudents, fetchWrongMaterials, fetchWrongNotes, updateWrongNote, WrongMaterial } from '../lib/types'
import { ExtractedPage, acceptedFileTypes, extractFiles, joinExtractedPages, maxUploadFiles } from '../lib/ocrExtract'
import { storageKey } from '../lib/workspaceStorage'

type Props = { apiSettings: ApiSettings | null; mode?: 'extract' | 'notebook'; studentId?: number | null; onStudentChange?: (id: number | null) => void }

export type WrongReviewMaterial = { id:string; studentId:number; studentName:string; school:string; grade:string; exam:string; title:string; html:string; createdAt:string }

const grades = ['1학년', '2학년', '3학년']
const semesters = ['1학기', '2학기']
const examTypes = ['중간', '기말']


const downloadBlob = (filename: string, blob: Blob) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = filename; link.click()
  URL.revokeObjectURL(url)
}

export default function StudentWrongNoteDashboard({ apiSettings, mode = 'notebook', studentId, onStudentChange }: Props) {
  const [students, setStudents] = useState<Student[]>([])
  // 학생 오답 통합 화면에서 선택을 공유하기 위한 선택적 제어 프롭. 단독 화면(wrongextract/wrongnote)은 기존 내부 상태를 그대로 쓴다.
  const [internalSelectedId, setInternalSelectedId] = useState<number | null>(null)
  const selectedId = studentId !== undefined ? studentId : internalSelectedId
  const setSelectedId = (id: number | null) => { if (onStudentChange) onStudentChange(id); else setInternalSelectedId(id) }
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [form, setForm] = useState({ name: '', school: '', grade: grades[2], semester: semesters[0], exam_type: examTypes[0] })
  const updateForm = (key: keyof typeof form, value: string) => setForm(current => ({ ...current, [key]: value }))
  const [examLabel, setExamLabel] = useState('')
  const [noteTitle, setNoteTitle] = useState('')
  const [questionType, setQuestionType] = useState('')
  const [answerInput, setAnswerInput] = useState('')
  const [pages, setPages] = useState<ExtractedPage[]>([])
  const [finalText, setFinalText] = useState('')
  const [extracting, setExtracting] = useState(false)
  const [extractStatus, setExtractStatus] = useState('')
  const [saving, setSaving] = useState(false)
  const [notes, setNotes] = useState<StudentWrongNote[]>([])
  const [notesLoading, setNotesLoading] = useState(false)
  const [materials, setMaterials] = useState<WrongMaterial[]>([])
  const [zoomedMaterialId, setZoomedMaterialId] = useState<number | null>(null)
  // 실사용 검증: "저장 성공" 안내가 저장 직전에 노출되지 않도록, 실제 저장 완료 여부를 상태로 추적한다.
  // 원본 보관(오답 사진 AI 복원 승인 저장) 여부도 함께 표시한다.
  const [saveCommitted, setSaveCommitted] = useState(false)
  const [expandedNoteIds, setExpandedNoteIds] = useState<Set<number>>(new Set())
  const [editDrafts, setEditDrafts] = useState<Record<number, { title: string; question_type: string; wrong_text: string; answer_text: string }>>({})
  const [savingNoteId, setSavingNoteId] = useState<number | null>(null)
  const [selectedNoteIds, setSelectedNoteIds] = useState<number[]>([])
  const [showIntegratePreview, setShowIntegratePreview] = useState(false)
  const [integrateTitle, setIntegrateTitle] = useState('')
  const [exporting, setExporting] = useState(false)
  const [reviewHtml, setReviewHtml] = useState('')

  const selected = students.find(student => student.id === selectedId) || null
  const loadStudents = () => { fetchStudents().then(setStudents).catch(() => setError('학생 목록을 불러오지 못했습니다.')) }
  useEffect(() => { loadStudents() }, [])
  const loadNotes = (studentId: number) => {
    setNotesLoading(true)
    fetchWrongNotes(studentId).then(setNotes).catch(() => setError('오답 자료를 불러오지 못했습니다.')).finally(() => setNotesLoading(false))
    fetchWrongMaterials(studentId).then(setMaterials).catch(() => setMaterials([]))
  }
  useEffect(() => {
    setSelectedNoteIds([]); setExpandedNoteIds(new Set()); setShowIntegratePreview(false); setSaveCommitted(false)
    if (selectedId) loadNotes(selectedId); else setNotes([])
  }, [selectedId])

  const registerStudent = async () => {
    setError(''); setNotice('')
    if (!form.name.trim()) { setError('학생 이름을 입력해 주세요.'); return }
    try {
      const student = await createStudent(form)
      setStudents(current => [student, ...current])
      setSelectedId(student.id)
      setForm({ name: '', school: form.school, grade: form.grade, semester: form.semester, exam_type: form.exam_type })
      setNotice(`${student.name} 학생 프로필을 등록했습니다.`)
    } catch (err) { setError(err instanceof Error ? err.message : '학생 프로필을 저장하지 못했습니다.') }
  }

  const removeStudent = async (id: number) => {
    if (!window.confirm('이 학생 프로필과 저장된 오답 자료를 모두 삭제할까요?')) return
    try {
      await deleteStudent(id)
      setStudents(current => current.filter(item => item.id !== id))
      if (selectedId === id) setSelectedId(null)
      setNotice('학생 프로필을 삭제했습니다.')
    } catch { setError('학생 프로필을 삭제하지 못했습니다.') }
  }

  const handleUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    const chosen = Array.from(event.target.files || []).slice(0, maxUploadFiles)
    event.target.value = ''
    if (!chosen.length) return
    setError(''); setNotice(''); setFinalText('')
    setExtracting(true)
    setExtractStatus('원본 파일을 불러오는 중…')
    try {
      const extracted = await extractFiles(chosen, message => setExtractStatus(message))
      setPages(extracted)
      const raw = joinExtractedPages(extracted)
      {
        setExtractStatus('Gemini로 원본 시험지 구조에 맞게 정리하는 중…')
        try {
          const cleaned = await cleanOcrText({ api_key: '', raw_text: raw })
          setFinalText(cleaned || raw)
        } catch (err) {
          setFinalText(raw)
          setError(err instanceof Error ? `AI 정리에 실패해 1차 추출 텍스트를 그대로 표시합니다: ${err.message}` : 'AI 정리에 실패해 1차 추출 텍스트를 그대로 표시합니다.')
        }
      }
      setExtractStatus(`추출 완료 · ${extracted.length}개 항목 · 아래에서 확인/수정 후 저장하세요.`)
    } catch (err) {
      setExtractStatus(err instanceof Error ? `추출 실패: ${err.message}` : '추출에 실패했습니다.')
    } finally {
      setExtracting(false)
    }
  }

  const saveWrongNote = async () => {
    setError(''); setNotice('')
    if (!selected) { setError('먼저 학생 프로필을 선택해 주세요.'); return }
    if (!finalText.trim()) { setError('오답 사진/PDF를 업로드해 최종 추출 텍스트를 준비해 주세요.'); return }
    setSaving(true)
    try {
      const note = await createWrongNote(selected.id, {
        exam_label: examLabel, title: noteTitle, question_type: questionType,
        question_text: finalText, answer_text: answerInput, source_text: joinExtractedPages(pages),
      })
      // 저장이 실제로 성공한 뒤에만 목록에 반영하고 성공 안내를 노출한다.
      setNotes(current => [note, ...current])
      setSaveCommitted(true)
      fetchWrongMaterials(selected.id).then(setMaterials).catch(() => undefined)
      setFinalText(''); setPages([]); setExamLabel(''); setNoteTitle(''); setQuestionType(''); setAnswerInput(''); setExtractStatus('')
      setNotice(`저장 완료 · 아래 저장된 오답 자료에 방금 항목이 추가되었습니다 (${new Date(note.created_at).toLocaleString('ko-KR')}).`)
    } catch (err) { setError(err instanceof Error ? err.message : '오답 저장 중 오류가 발생했습니다.') } finally { setSaving(false) }
  }

  const removeNote = async (noteId: number) => {
    if (!selected) return
    if (!window.confirm('이 오답 자료를 삭제할까요?')) return
    try {
      await deleteWrongNote(selected.id, noteId)
      setNotes(current => current.filter(item => item.id !== noteId))
      setSelectedNoteIds(ids => ids.filter(id => id !== noteId))
    } catch { setError('오답 자료를 삭제하지 못했습니다.') }
  }

  const toggleExpanded = (note: StudentWrongNote) => {
    setExpandedNoteIds(current => {
      const next = new Set(current)
      if (next.has(note.id)) { next.delete(note.id) } else {
        next.add(note.id)
        setEditDrafts(drafts => ({ ...drafts, [note.id]: { title: note.title, question_type: note.question_type, wrong_text: note.wrong_text, answer_text: note.answer_text } }))
      }
      return next
    })
  }
  const updateDraft = (noteId: number, key: 'title' | 'question_type' | 'wrong_text' | 'answer_text', value: string) =>
    setEditDrafts(current => ({ ...current, [noteId]: { ...current[noteId], [key]: value } }))

  const saveNoteEdit = async (noteId: number) => {
    if (!selected) return
    const draft = editDrafts[noteId]
    if (!draft) return
    setSavingNoteId(noteId)
    try {
      const updated = await updateWrongNote(selected.id, noteId, {
        title: draft.title, question_type: draft.question_type, question_text: draft.wrong_text, answer_text: draft.answer_text,
      })
      setNotes(current => current.map(item => item.id === noteId ? updated : item))
      setNotice('수정한 내용을 다시 저장했습니다.')
    } catch (err) { setError(err instanceof Error ? err.message : '오답 자료를 수정하지 못했습니다.') } finally { setSavingNoteId(null) }
  }

  const toggleNoteSelection = (id: number) => setSelectedNoteIds(ids => ids.includes(id) ? ids.filter(itemId => itemId !== id) : [...ids, id])
  const openIntegratePreview = () => {
    if (!selectedNoteIds.length) return
    setIntegrateTitle(selected ? `${selected.name} 오답 복습 문제` : '오답 복습 문제')
    setReviewHtml(`<article class="wrong-review-document"><h1>${selected ? selected.name : ''} 오답 복습 문제</h1>${selectedNotesOrdered.map((note, index) => `<section data-note-id="${note.id}"><h2>${index + 1}. ${note.title || note.question_type || '오답 문항'}</h2><div style="white-space:pre-wrap">${note.wrong_text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}</div>${note.answer_text ? `<p class="answer"><b>정답</b> ${note.answer_text}</p>` : ''}</section>`).join('')}</article>`)
    setShowIntegratePreview(true)
  }
  const selectedNotesOrdered = selectedNoteIds.map(id => notes.find(note => note.id === id)).filter((note): note is StudentWrongNote => Boolean(note))

  const exportIntegrated = async () => {
    if (!selected || !selectedNoteIds.length) return
    setExporting(true)
    try {
      const blob = await exportWrongNotesOffice(selected.id, { note_ids: selectedNoteIds, title: integrateTitle })
      downloadBlob(`${(integrateTitle || '오답_복습_문제').replace(/[\\/:*?"<>|]/g, '_')}.docx`, blob)
      setNotice('오답 복습 문제지를 오피스 2007(.docx) 형식으로 내보냈습니다.')
    } catch (err) {
      setNotice(err instanceof Error ? err.message : '오답 복습 문제지를 내보내지 못했습니다.')
    } finally {
      setExporting(false)
    }
  }

  const downloadReviewHtml = () => {
    const blob = new Blob([`<!doctype html><html lang="ko"><meta charset="utf-8"><title>${integrateTitle}</title><style>body{max-width:190mm;margin:auto;font-family:Arial,sans-serif;line-height:1.65}section{margin:24px 0;break-inside:avoid}.answer{border-top:1px solid #ddd;padding-top:8px}@media print{body{max-width:none}}</style><body>${reviewHtml}</body></html>`], { type: 'text/html;charset=utf-8' })
    downloadBlob(`${(integrateTitle || '오답_복습_문제').replace(/[\\/:*?"<>|]/g, '_')}.html`, blob)
  }
  const saveReviewMaterial = () => {
    if (!selected || !reviewHtml.trim()) return
    const key=storageKey('f2r_wrong_review_materials'), previous=JSON.parse(localStorage.getItem(key)||'[]') as WrongReviewMaterial[]
    const material:WrongReviewMaterial={id:crypto.randomUUID(),studentId:selected.id,studentName:selected.name,school:selected.school,grade:selected.grade,exam:selected.exam_type||examLabel,title:integrateTitle,html:reviewHtml,createdAt:new Date().toISOString()}
    localStorage.setItem(key,JSON.stringify([material,...previous]));window.dispatchEvent(new Event('f2r-wrong-reviews-changed'))
    setNotice('편집한 HTML을 저장했습니다. 파이널 조립 화면에서 그대로 선택할 수 있습니다.')
  }

  return <section className={`twin-dashboard wrong-workspace ${mode}-mode`} aria-label={mode === 'extract' ? '오답 기록·추출' : '학생별 오답노트'}>
    <div className="panel-heading"><div><p className="kicker">{mode === 'extract' ? 'WRONG ANSWER CAPTURE' : 'STUDENT REVIEW LIBRARY'}</p><h2>{mode === 'extract' ? '오답 기록·추출' : '학생별 오답노트'}</h2><p>{mode === 'extract' ? '학생과 시험을 지정하고 채점지 사진·PDF에서 오답을 추출해 기록합니다.' : '학생별 누적 오답을 확인·수정하고 선택 문항을 복습 문제지로 만듭니다.'}</p></div><span className="context-chip">{selected ? `선택 · ${selected.name}` : '학생 미선택'}</span></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}
    {error && <div className="tool-notice" role="alert" style={{ background: '#fdecec', color: '#b5222c' }}>{error}<button onClick={() => setError('')} aria-label="오류 닫기">×</button></div>}
    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">STUDENT REGISTRATION</p><h2>학생 등록</h2></div></div><div className="metadata-grid"><label>학생 이름<input value={form.name} onChange={event => updateForm('name', event.target.value)} placeholder="예: 김민준" /></label><label>학교<input value={form.school} onChange={event => updateForm('school', event.target.value)} placeholder="예: 압구정중" /></label><label>학년<select value={form.grade} onChange={event => updateForm('grade', event.target.value)}>{grades.map(item => <option key={item}>{item}</option>)}</select></label><label>학기<select value={form.semester} onChange={event => updateForm('semester', event.target.value)}>{semesters.map(item => <option key={item}>{item}</option>)}</select></label><label>시험<select value={form.exam_type} onChange={event => updateForm('exam_type', event.target.value)}>{examTypes.map(item => <option key={item}>{item}</option>)}</select></label></div><button className="primary-action" onClick={() => void registerStudent()}>학생 프로필 등록</button></article>
    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">STUDENT LIST</p><h2>등록된 학생</h2></div></div>{students.length ? <div className="report-controls">{students.map(student => <button key={student.id} className={selectedId === student.id ? 'active' : ''} onClick={() => setSelectedId(student.id)}>{student.name} · {student.school || '학교 미입력'} · {student.grade || '학년 미입력'}</button>)}</div> : <p className="field-hint">등록된 학생이 없습니다. 위에서 먼저 등록해 주세요.</p>}{selected && <button className="secondary-button" style={{ marginTop: 10 }} onClick={() => void removeStudent(selected.id)}>선택한 학생 삭제</button>}</article>
    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">WRONG ANSWER EXTRACTION</p><h2>오답 추출</h2><p>채점된 시험지 사진 또는 PDF를 올리면 원본을 보면서 추출된 텍스트를 확인·수정할 수 있습니다. 수정이 끝나면 아래에서 저장하세요.</p></div></div><div className="metadata-grid"><label>시험명 (선택)<input value={examLabel} onChange={event => setExamLabel(event.target.value)} placeholder="예: 2026 2학기 중간고사" /></label><label>문제 유형 (선택)<input value={questionType} onChange={event => setQuestionType(event.target.value)} placeholder="예: 문장 흐름, 어법" /></label><label>문제 제목/번호 (선택)<input value={noteTitle} onChange={event => setNoteTitle(event.target.value)} placeholder="예: 6번 문장 흐름 문제" /></label></div><label className="wrong-upload-btn" style={{ marginTop: 14 }}>📷 오답 사진 업로드<input type="file" accept={acceptedFileTypes} multiple onChange={event => void handleUpload(event)} /></label><p className="field-hint">JPG · JPEG · PNG · PDF 지원 · 최대 {maxUploadFiles}개 파일</p>{(extracting || extractStatus) && <div className="pdf-status" role="status">{extracting ? '⏳ ' : ''}{extractStatus}</div>}{pages.length > 0 && <div className="wrong-upload-columns"><div><label className="title-label" style={{ marginTop: 0 }}>원본 미리보기 (A4 절반 크기)</label><div className="wrong-upload-preview">{pages.map((page, index) => <figure className="preview-doc" key={`${page.name}-${index}`}><span className="preview-doc-frame"><img src={page.previewUrl} alt={page.name} /></span><figcaption>{page.name}{page.usedOcr ? ' · OCR' : ''}</figcaption></figure>)}</div></div><div className="wrong-upload-text"><label className="title-label" style={{ marginTop: 0 }}>최종 추출 텍스트 (필요 시 직접 수정 가능)</label><textarea value={finalText} onChange={event => setFinalText(event.target.value)} placeholder="추출된 텍스트가 여기에 표시됩니다." /></div></div>}{pages.length > 0 && <div className="metadata-grid" style={{ gridTemplateColumns: '1fr', marginTop: 14 }}><label>정답 (선택 — 알고 있다면 입력)<input value={answerInput} onChange={event => setAnswerInput(event.target.value)} placeholder="예: ④" /></label></div>}<button className="primary-action" disabled={saving || !selected || !finalText.trim()} onClick={() => void saveWrongNote()}>{saving ? '저장 중…' : '학생 오답으로 저장'}</button>{!selected && <p className="field-hint">오답을 저장하려면 먼저 위에서 학생을 선택해 주세요.</p>}</article>
    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">SAVED WRONG NOTES</p><h2>저장된 오답 자료{selected ? ` · ${selected.name}` : ''}</h2>{selected && <p>{saveCommitted ? '● 방금 저장한 항목이 아래에 있습니다.' : '학생을 바꾸거나 새로고침해도 이 목록은 그대로 유지됩니다.'}</p>}</div><div className="twin-actions"><button className="primary-action" style={{ marginTop: 0 }} disabled={!selectedNoteIds.length} onClick={openIntegratePreview}>선택 오답 통합 ({selectedNoteIds.length})</button></div></div>{!selected ? <div className="empty-state"><b>학생을 선택해 주세요.</b><p>학생별로 저장된 오답 자료가 여기에 표시됩니다.</p></div> : notesLoading ? <p className="field-hint">불러오는 중…</p> : notes.length ? <div className="db-list">{notes.map(note => { const expanded = expandedNoteIds.has(note.id); const draft = editDrafts[note.id]; return <article key={note.id} style={{ flexWrap: 'wrap' }}><input type="checkbox" aria-label={`${note.id} 선택`} checked={selectedNoteIds.includes(note.id)} onChange={() => toggleNoteSelection(note.id)} /><div><b>{note.question_type || '문항'} · {note.title || note.wrong_text.slice(0, 40)}</b><p>{note.student_name} · {note.student_school || '학교 미입력'} · {note.student_grade || '학년 미입력'}{note.exam_label ? ` · ${note.exam_label}` : ''}</p><small>{new Date(note.created_at).toLocaleString('ko-KR')}{note.answer_text ? ` · 정답 ${note.answer_text}` : ''}</small></div><button onClick={() => toggleExpanded(note)}>{expanded ? '간략히' : '전체보기'}</button><button onClick={() => void removeNote(note.id)}>삭제</button>{expanded && draft && <div style={{ flexBasis: '100%', marginTop: 12 }}><div className="metadata-grid"><label>문제 유형<input value={draft.question_type} onChange={event => updateDraft(note.id, 'question_type', event.target.value)} /></label><label>문제 제목/번호<input value={draft.title} onChange={event => updateDraft(note.id, 'title', event.target.value)} /></label><label>정답<input value={draft.answer_text} onChange={event => updateDraft(note.id, 'answer_text', event.target.value)} /></label></div><label className="title-label">저장된 문제 전체 내용 (발문·지문·선택지·학생 답 등 — 수정 가능)<textarea style={{ minHeight: 220 }} value={draft.wrong_text} onChange={event => updateDraft(note.id, 'wrong_text', event.target.value)} /></label><button className="primary-action" disabled={savingNoteId === note.id} onClick={() => void saveNoteEdit(note.id)}>{savingNoteId === note.id ? '저장 중…' : '수정 내용 저장'}</button></div>}</article> })}</div> : <div className="empty-state"><b>저장된 오답 자료가 없습니다.</b><p>{saveCommitted ? '방금 저장한 항목을 불러오지 못했습니다. 새로고침 후에도 없다면 저장이 실패한 것입니다.' : '위에서 채점된 시험지 사진을 업로드하고 저장해 주세요. 오답 사진 AI 복원에서 승인 저장한 문제도 이곳에 누적됩니다.'}</p></div>}{selected && materials.length > 0 && <div className="wrong-materials-box" style={{ marginTop: 14 }}><p className="field-hint">보관 원본 사진/PDF {materials.length}건 — 대조용 썸네일(작게 유지), 클릭하면 확대됩니다.</p><div className="wrong-material-thumb-grid">{materials.map(material => { const isImage = (material.original_type || '').startsWith('image/'); const originalUrl = apiUrl(`exam-materials/${material.id}/original`); return <figure key={material.id} className="wrong-material-thumb">{isImage ? <button type="button" onClick={() => setZoomedMaterialId(material.id)} aria-label={`${material.original_name || '원본'} 확대`}><img src={originalUrl} alt={material.original_name || '원본'} loading="lazy" /></button> : <a className="wrong-material-filelink" href={originalUrl} target="_blank" rel="noreferrer" aria-label={`${material.original_name || '원본'} 열기`}><span className="wrong-material-fileicon">📎</span></a>}<figcaption><span>{material.original_name || '원본'}</span><span>{new Date(material.created_at).toLocaleDateString('ko-KR')}</span></figcaption></figure> })}</div></div>}</article>
    {showIntegratePreview && <article className="work-panel" aria-label="선택 오답 HTML 편집"><div className="panel-heading"><div><p className="kicker">INTEGRATED REVIEW SHEET</p><h2>선택 오답 HTML 편집</h2><p>선택 순서로 만든 복습자료를 큰 HTML 작업 영역에서 직접 수정한 뒤 저장·다운로드하거나 기존 DOCX로 내보냅니다.</p></div><button className="secondary-button" onClick={() => setShowIntegratePreview(false)}>닫기</button></div><div className="metadata-grid" style={{ gridTemplateColumns: '1fr' }}><label>문제지 제목<input value={integrateTitle} onChange={event => setIntegrateTitle(event.target.value)} /></label></div><div className="wrong-review-canvas" contentEditable suppressContentEditableWarning onInput={event=>setReviewHtml(event.currentTarget.innerHTML)} dangerouslySetInnerHTML={{__html:reviewHtml}}/><div className="editor-output-actions"><button className="primary-action" onClick={saveReviewMaterial}>HTML 저장·파이널 연결</button><button className="secondary-button" onClick={downloadReviewHtml}>HTML 다운로드</button><button className="secondary-button" disabled={exporting} onClick={() => void exportIntegrated()}>{exporting ? '내보내는 중...' : '오피스 2007 내보내기'}</button></div></article>}
    {zoomedMaterialId !== null && <div className="wrong-zoom-overlay" role="dialog" aria-modal="true" aria-label="원본 사진 확대" onClick={() => setZoomedMaterialId(null)}><figure className="wrong-zoom-body" onClick={event => event.stopPropagation()}><figcaption>{materials.find(material => material.id === zoomedMaterialId)?.original_name || '원본'} <a href={apiUrl(`exam-materials/${zoomedMaterialId}/original`)} target="_blank" rel="noreferrer">원본 새 창에서 열기 ↗</a></figcaption><img src={apiUrl(`exam-materials/${zoomedMaterialId}/original`)} alt="원본 사진 확대" /><button className="secondary-button" onClick={() => setZoomedMaterialId(null)}>닫기</button></figure></div>}
  </section>
}


