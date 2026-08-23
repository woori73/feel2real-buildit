import { FormEvent, useEffect, useState } from 'react'
import { EducatorInsight, SchoolNode, createEducatorInsight, fetchEducatorInsights, fetchSchoolTree } from '../lib/types'

export default function InsightRecorder() {
  const [content, setContent] = useState('')
  const [school, setSchool] = useState('')
  const [grade, setGrade] = useState('')
  const [lessonContext, setLessonContext] = useState('')
  const [studentContext, setStudentContext] = useState('')
  const [recent, setRecent] = useState<EducatorInsight[]>([])
  const [status, setStatus] = useState('')
  const [saving, setSaving] = useState(false)
  const [schools, setSchools] = useState<SchoolNode[]>([])

  const refresh = () => fetchEducatorInsights().then(setRecent).catch(() => setStatus('최근 기록을 불러오지 못했습니다.'))
  useEffect(() => { void refresh(); fetchSchoolTree().then(setSchools).catch(() => undefined) }, [])
  const grades = schools.find(item => item.school === school)?.grades || []

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!content.trim()) { setStatus('수업에서 발견한 내용을 입력해 주세요.'); return }
    setSaving(true); setStatus('')
    try {
      await createEducatorInsight({ content, school, grade, lesson_context: lessonContext, student_context: studentContext })
      setContent(''); setStatus('저장됨 · Educator Brain · 수업 인사이트'); await refresh()
    } catch (error) { setStatus(error instanceof Error ? error.message : '기록을 저장하지 못했습니다.') }
    finally { setSaving(false) }
  }

  return <div className="insight-layout">
    <form className="work-panel insight-form" onSubmit={submit}>
      <div className="panel-heading"><div><p className="kicker">EDUCATOR BRAIN</p><h2>수업 인사이트 기록</h2><p>수업 직후의 판단을 짧게 남겨 다음 문제·프린트·강의 제작에 이어 씁니다.</p></div></div>
      <label className="insight-prompt">오늘 수업에서 무엇을 발견하셨나요?<textarea value={content} maxLength={10000} onChange={event => setContent(event.target.value)} placeholder="예: 관계대명사 what을 설명할 때 문장 성분을 먼저 표시하니 학생들이 빠르게 이해했다. 다음 프린트에는 오답 선택지 근거 찾기를 추가하자." /></label>
      <div className="metadata-grid"><label>학교<input list="buildit-school-options" value={school} onChange={event => { setSchool(event.target.value); setGrade('') }} placeholder="학교 DB에서 선택 또는 입력" /><datalist id="buildit-school-options">{schools.map(item => <option key={item.school} value={item.school} />)}</datalist></label><label>학년<select value={grade} onChange={event => setGrade(event.target.value)}><option value="">선택 입력</option>{grades.map(item => <option key={item.grade} value={item.grade}>{item.grade}</option>)}</select></label><label>학생·반 맥락<input value={studentContext} onChange={event => setStudentContext(event.target.value)} placeholder="예: 월수 중2반" /></label><label>수업 맥락<input value={lessonContext} onChange={event => setLessonContext(event.target.value)} placeholder="예: 관계대명사 보강" /></label></div>
      <p className="storage-location">저장 위치 · Educator Brain · 수업 인사이트</p><div className="insight-submit"><small>{content.length.toLocaleString()} / 10,000자</small><button className="primary-action" disabled={saving || !content.trim()} type="submit">{saving ? '기록 중…' : '기록하기'}</button></div>
      {status && <p className="form-status" role="status">{status}</p>}
    </form>
    <section className="work-panel recent-insights"><div className="panel-heading"><div><p className="kicker">RECENT MEMORY</p><h2>최근 기록</h2></div></div>{recent.length ? <div>{recent.map(item => <article key={item.id}><p>{item.content}</p><small>{[item.school, item.grade, item.student_context, item.lesson_context].filter(Boolean).join(' · ') || '수업 맥락 미입력'} · {new Date(item.created_at).toLocaleString('ko-KR')}</small></article>)}</div> : <p className="recent-empty">아직 기록이 없습니다. 첫 수업 인사이트를 남겨 보세요.</p>}<footer>이 기록은 이후 제작 화면에서 재사용할 Educator Brain 컨텍스트로 축적됩니다.</footer></section>
  </div>
}
