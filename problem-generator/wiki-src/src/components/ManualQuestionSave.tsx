import { useEffect, useMemo, useState } from 'react'
import { SourceMaterial, fetchSourceMaterials, saveManualQuestions } from '../lib/types'
import { ParsedQuestion, parsePastedQuestions, toContractQuestion } from '../lib/parseQuestions'

// 강사 문제 DB — 외부(Claude/MCP 등)에서 이미 제작한 문제를 사용자가 직접 붙여넣어 저장한다.
// AI 재생성·Digital Twin 분석 없음. 붙여넣기 → 문항 분석 → 미리보기·수정 → 저장.
const YEARS = [new Date().getFullYear(), new Date().getFullYear() - 1, new Date().getFullYear() - 2]
const EXAMS = ['전체', '중간고사', '기말고사']

const readContext = () => {
  try {
    const s = JSON.parse(sessionStorage.getItem('buildit_assistant_state') || '{}')
    return { school: s.school || '', grade: s.grade || '', year: Number(s.year) || YEARS[0], semester: s.semester || '2학기', exam: s.exam || '전체' }
  } catch { return { school: '', grade: '', year: YEARS[0], semester: '2학기', exam: '전체' } }
}

const SAMPLE = `Q1. 다음 빈칸에 알맞은 단어를 쓰시오.\nThe temperature increased ______ over time.\n정답: gradually\n해설: gradually는 '점차적으로'라는 뜻이다.\n\nQ2. 다음 중 어법상 옳은 것은?\n① is  ② are  ③ was  ④ were  ⑤ be\n정답: ②\n해설: 주어가 복수이므로 are.`

export default function ManualQuestionSave({ onSaved, onClose }: { onSaved: () => void; onClose: () => void }) {
  const [ctx, setCtx] = useState(readContext)
  const [title, setTitle] = useState('')
  const [materials, setMaterials] = useState<SourceMaterial[]>([])
  const [sourceMaterial, setSourceMaterial] = useState('')
  const [sourcePage, setSourcePage] = useState('')
  const [digitalTwin, setDigitalTwin] = useState('')
  const [paste, setPaste] = useState('')
  const [rows, setRows] = useState<(ParsedQuestion & { _include: boolean })[]>([])
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!ctx.school || !ctx.grade) { setMaterials([]); return }
    fetchSourceMaterials({ school: ctx.school, grade: ctx.grade }).then(setMaterials).catch(() => setMaterials([]))
  }, [ctx.school, ctx.grade])

  const included = useMemo(() => rows.filter(r => r._include), [rows])

  const analyze = () => {
    const parsed = parsePastedQuestions(paste)
    if (!parsed.length) { setNotice('문항을 분리하지 못했습니다. "Q1.", "1.", "문제 1" 같은 번호나 빈 줄로 구분해 주세요.'); return }
    setRows(parsed.map(q => ({ ...q, _include: true })))
    setNotice(`${parsed.length}문항으로 분리했습니다. 아래에서 확인하고 저장하세요.`)
  }

  const patch = (i: number, next: Partial<ParsedQuestion & { _include: boolean }>) =>
    setRows(rs => rs.map((r, idx) => idx === i ? { ...r, ...next } : r))

  const save = async () => {
    if (!included.length) { setNotice('저장할 문항을 선택해 주세요.'); return }
    if (!ctx.school || !ctx.grade) { setNotice('학교·학년 Context를 입력해 주세요.'); return }
    setSaving(true); setNotice('저장하는 중입니다.')
    try {
      const res = await saveManualQuestions({
        school: ctx.school, grade: ctx.grade, year: ctx.year, semester: ctx.semester, exam: ctx.exam,
        exam_title: title.trim(), source_material: sourceMaterial.trim(), source_page: sourcePage.trim(),
        digital_twin: digitalTwin.trim(),
        questions: included.map(({ _include, ...q }) => { void _include; return toContractQuestion(q) }),
      })
      setNotice(`✓ ${res.question_count}문항 저장 완료`)
      setPaste(''); setRows([])
      onSaved()
    } catch (e) {
      setNotice(e instanceof Error ? e.message : '저장하지 못했습니다.')
    } finally { setSaving(false) }
  }

  return <section className="work-panel manual-question-save">
    <div className="panel-heading"><div><p className="kicker">SAVE EXTERNAL QUESTIONS</p><h2>문제 저장하기</h2>
      <p>Claude/MCP 등에서 이미 만든 문제를 붙여넣어 강사 문제 DB에 그대로 저장합니다. AI가 다시 생성하지 않습니다.</p></div>
      <button type="button" className="secondary-button" onClick={onClose}>닫기</button></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}

    <div className="exam-context" aria-label="저장 Context">
      <label>학교<input value={ctx.school} onChange={e => setCtx({ ...ctx, school: e.target.value })} placeholder="신사중" /></label>
      <label>학년<input value={ctx.grade} onChange={e => setCtx({ ...ctx, grade: e.target.value })} placeholder="3학년" /></label>
      <label>연도<select value={ctx.year} onChange={e => setCtx({ ...ctx, year: Number(e.target.value) })}>{YEARS.map(y => <option key={y}>{y}</option>)}</select></label>
      <label>학기<select value={ctx.semester} onChange={e => setCtx({ ...ctx, semester: e.target.value })}><option>1학기</option><option>2학기</option></select></label>
      <label>시험<select value={ctx.exam} onChange={e => setCtx({ ...ctx, exam: e.target.value })}>{EXAMS.map(x => <option key={x}>{x}</option>)}</select></label>
    </div>

    <div className="metadata-grid">
      <label>시험/세트 제목 (선택)<input value={title} onChange={e => setTitle(e.target.value)} placeholder="예: The Boiling Frog Myth Page 2 Vocabulary" /></label>
      <label>출처 학습자료 (선택)
        <input list="manual-source-materials" value={sourceMaterial} onChange={e => setSourceMaterial(e.target.value)} placeholder="The Boiling Frog Myth" />
        <datalist id="manual-source-materials">{materials.map(m => <option key={m.id} value={m.title} />)}</datalist>
      </label>
      <label>출처 페이지 (선택)<input value={sourcePage} onChange={e => setSourcePage(e.target.value)} placeholder="2" /></label>
      <label>출제자 Digital Twin (선택)<input value={digitalTwin} onChange={e => setDigitalTwin(e.target.value)} placeholder="신사중 3학년 출제자 Digital Twin" /></label>
    </div>
    <p className="field-hint">출처 학습자료를 입력하면 Generated Question Store의 시험으로도 연결됩니다. 없으면 비워 두세요.</p>

    <label className="manual-paste-label">문제 붙여넣기
      <textarea value={paste} onChange={e => setPaste(e.target.value)} rows={10}
        placeholder={SAMPLE} />
    </label>
    <div className="twin-actions">
      <button type="button" className="secondary-button" onClick={analyze} disabled={!paste.trim()}>문항 분석</button>
      {rows.length > 0 && <span className="context-chip">저장 예정 {included.length}문항 / 분리 {rows.length}</span>}
    </div>

    {rows.length > 0 && <div className="db-list manual-preview">
      {rows.map((r, i) => <article key={i} style={{ flexWrap: 'wrap' }}>
        <input type="checkbox" aria-label={`${i + 1}번 문항 포함`} checked={r._include} onChange={e => patch(i, { _include: e.target.checked })} />
        <div style={{ flex: 1, minWidth: 260 }}>
          <b>{i + 1}. {r.type === 'mc' ? '객관식' : '서답형'} · {r.questionType || '유형 미상'} · {r.score}점
            {r._warnings && r._warnings.length > 0 && <span className="tag-multi" style={{ marginLeft: 6 }}>확인 필요: {r._warnings.join(', ')}</span>}</b>
          <label className="manual-edit">문제<textarea value={r.stem} rows={2} onChange={e => patch(i, { stem: e.target.value })} /></label>
          {r.options.length > 0 && <label className="manual-edit">선택지 (줄바꿈 구분)
            <textarea rows={Math.min(6, r.options.length + 1)} value={r.options.join('\n')}
              onChange={e => { const options = e.target.value.split('\n').map(s => s.trim()).filter(Boolean); patch(i, { options }) }} /></label>}
          <div className="manual-edit-row">
            <label>유형<input value={r.questionType} onChange={e => patch(i, { questionType: e.target.value })} /></label>
            <label>배점<input type="number" min={0} value={r.score} onChange={e => patch(i, { score: Number(e.target.value) || 0 })} /></label>
            <label>정답{r.type === 'mc'
              ? <input value={r.answer.join(', ')} placeholder="예: 2 또는 2,4"
                  onChange={e => { const answer = e.target.value.split(/[^\d]+/).map(Number).filter(n => n >= 1 && n <= r.options.length); patch(i, { answer, multi: answer.length > 1 }) }} />
              : <input value={r.sampleAnswer} onChange={e => patch(i, { sampleAnswer: e.target.value, acceptedAnswers: e.target.value ? [e.target.value] : [] })} />}
            </label>
          </div>
          <label className="manual-edit">해설 / 채점 기준<textarea rows={2} value={r.gradingRule} onChange={e => patch(i, { gradingRule: e.target.value })} /></label>
        </div>
      </article>)}
    </div>}

    <button className="primary-action" disabled={saving || !included.length} onClick={() => void save()}>
      {saving ? '저장하는 중...' : `Wiki 문제 DB에 저장 (${included.length}문항)`}
    </button>
  </section>
}
