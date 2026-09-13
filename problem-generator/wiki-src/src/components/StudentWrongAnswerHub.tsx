import { useEffect, useState } from 'react'
import StudentWrongNoteDashboard from './StudentWrongNoteDashboard'
import WrongPhotoAiRestore from './WrongPhotoAiRestore'
import { Student, fetchStudents, fetchWrongMaterials, fetchWrongNotes } from '../lib/types'

type Tab = 'materials' | 'restore' | 'review'

type Props = { initialTab?: Tab }

const TABS: { id: Tab; label: string }[] = [
  { id: 'materials', label: '오답 자료' },
  { id: 'restore', label: '사진 추가 · AI 복원' },
  { id: 'review', label: '오답 복습' },
]

// 학생 오답을 하나의 기능으로 이해시키는 통합 화면.
// 상단에서 학생을 한 번 선택하면 누적 오답/보관 원본 개수와 함께 하위 탭(자료/추가·복원/복습)이 이어진다.
// 각 탭은 기존 컴포넌트를 그대로 조합한다(기능 복제 없음, 새 API/DDL 없음).
export default function StudentWrongAnswerHub({ initialTab = 'materials' }: Props) {
  const [students, setStudents] = useState<Student[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [tab, setTab] = useState<Tab>(initialTab)
  const [noteCount, setNoteCount] = useState<number | null>(null)
  const [materialCount, setMaterialCount] = useState<number | null>(null)
  const [loadingCounts, setLoadingCounts] = useState(false)

  useEffect(() => { fetchStudents().then(setStudents).catch(() => undefined) }, [])
  useEffect(() => {
    if (!selectedId) { setNoteCount(null); setMaterialCount(null); return }
    setLoadingCounts(true)
    Promise.all([
      fetchWrongNotes(selectedId).then(notes => setNoteCount(notes.length)).catch(() => setNoteCount(null)),
      fetchWrongMaterials(selectedId).then(materials => setMaterialCount(materials.length)).catch(() => setMaterialCount(null)),
    ]).finally(() => setLoadingCounts(false))
  }, [selectedId])

  const selected = students.find(student => student.id === selectedId) || null

  return <section className="wrong-hub" aria-label="학생 오답">
    <div className="panel-heading"><div><p className="kicker">STUDENT WRONG ANSWERS</p><h2>학생 오답</h2><p>학생을 선택하면 누적 오답 자료, 사진 추가·AI 복원, 복습이 한 흐름으로 이어집니다.</p></div>
      {selected && <span className="context-chip">{selected.name} · {selected.school || '학교 미입력'} · {selected.grade || '학년 미입력'}</span>}
    </div>

    <article className="work-panel">
      <div className="wrong-hub-selector">
        <label>학생
          <select value={selectedId ?? ''} onChange={event => setSelectedId(event.target.value ? Number(event.target.value) : null)}>
            <option value="">학생 선택</option>
            {students.map(student => <option key={student.id} value={student.id}>{student.name} · {student.school || '학교 미입력'} · {student.grade || '학년 미입력'}</option>)}
          </select>
        </label>
        {selected && <div className="wrong-hub-counts" aria-label="누적 현황">
          <span>누적 오답 <b>{loadingCounts && noteCount === null ? '…' : noteCount ?? '—'}</b></span>
          <span>보관 원본 <b>{loadingCounts && materialCount === null ? '…' : materialCount ?? '—'}</b></span>
        </div>}
      </div>
      {!selected && <p className="field-hint">학생을 선택하면 아래 탭이 그 학생 기준으로 동작합니다. 새 학생은 오답 자료 탭의 학생 등록에서 추가할 수 있습니다.</p>}
      {selected && <nav className="wrong-hub-tabs" aria-label="학생 오답 하위 기능">
        {TABS.map(item => <button key={item.id} type="button" className={tab === item.id ? 'active' : ''} aria-current={tab === item.id ? 'page' : undefined} onClick={() => setTab(item.id)}>{item.label}</button>)}
      </nav>}
    </article>

    {selected && tab === 'materials' && <StudentWrongNoteDashboard apiSettings={null} mode="notebook" studentId={selectedId} onStudentChange={setSelectedId} />}
    {selected && tab === 'restore' && <WrongPhotoAiRestore studentId={selectedId} onStudentChange={setSelectedId} />}
    {selected && tab === 'review' && <article className="work-panel">
      <div className="panel-heading"><div><p className="kicker">REVIEW</p><h2>오답 복습</h2><p>{selected.name} 학생의 누적 오답을 선택해 복습 문제지로 만듭니다. 자료 탭에서 오답을 고르고 "선택 오답 통합"을 누르면 여기에서 편집·내보내기로 이어집니다.</p></div></div>
      <p className="field-hint">복습 문제지 만들기는 기존 흐름을 그대로 사용합니다. 오답 자료 탭에서 오답을 선택해 진행해 주세요. 복습 문제지 제작 화면(학생별 오답노트)은 왼쪽 ← 내비게이션의 학생 카테고리에서도 열 수 있습니다.</p>
    </article>}
  </section>
}
