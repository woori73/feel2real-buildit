import { useEffect, useMemo, useState } from 'react'
import {
  GeneratedTest, GeneratedTestDetail, SchoolNode, SourceMaterial,
  fetchGeneratedTest, fetchSchoolTree, fetchSourceMaterials, fetchSourceMaterialTests,
} from '../lib/types'
import QuestionCard from './QuestionCard'

// 생성 문제 — MCP가 Generated Question Store(source_materials → generated_tests → generated_questions)에
// 저장한 시험을 학습자료별로 다시 확인한다. 저장은 하지 않고 조회만 한다.
export default function GeneratedTestBrowser() {
  const [tree, setTree] = useState<SchoolNode[]>([])
  const [school, setSchool] = useState('')
  const [grade, setGrade] = useState('')
  const [materials, setMaterials] = useState<SourceMaterial[]>([])
  const [materialId, setMaterialId] = useState<number | null>(null)
  const [tests, setTests] = useState<GeneratedTest[]>([])
  const [testId, setTestId] = useState<number | null>(null)
  const [detail, setDetail] = useState<GeneratedTestDetail | null>(null)
  const [showAnswers, setShowAnswers] = useState(false)
  const [notice, setNotice] = useState('')

  useEffect(() => { fetchSchoolTree().then(nodes => {
    setTree(nodes)
    if (nodes[0]) { setSchool(nodes[0].school); setGrade(nodes[0].grades[0]?.grade || '') }
  }).catch(() => setNotice('학교 목록을 불러오지 못했습니다.')) }, [])

  const grades = useMemo(() => tree.find(n => n.school === school)?.grades || [], [tree, school])

  useEffect(() => {
    if (!school || !grade) return
    setMaterials([]); setMaterialId(null); setTests([]); setTestId(null); setDetail(null)
    fetchSourceMaterials({ school, grade }).then(setMaterials).catch(() => setNotice('학습자료 목록을 불러오지 못했습니다.'))
  }, [school, grade])

  useEffect(() => {
    if (materialId == null) return
    setTests([]); setTestId(null); setDetail(null)
    fetchSourceMaterialTests(materialId).then(setTests).catch(() => setNotice('생성 시험 목록을 불러오지 못했습니다.'))
  }, [materialId])

  useEffect(() => {
    if (testId == null) { setDetail(null); return }
    setShowAnswers(false)
    fetchGeneratedTest(testId).then(setDetail).catch(() => setNotice('생성 시험을 불러오지 못했습니다.'))
  }, [testId])

  const selectedMaterial = materials.find(m => m.id === materialId) || null
  const test = detail?.test

  return <section className="work-panel">
    <div className="panel-heading"><div><p className="kicker">GENERATED QUESTION STORE</p><h2>생성 문제</h2>
      <p>학습자료를 선택하면 그 자료에서 MCP가 생성·저장한 시험을 학생용 문제 / 정답·해설 / 출제 정보로 다시 확인합니다.</p>
      <p className="storage-location">저장 위치 · source_materials → generated_tests → generated_questions</p></div>
      <span className="context-chip">{materials.length}개 자료 · {tests.length}개 시험</span></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}

    <div className="exam-settings">
      <label>학교<select value={school} onChange={e => { setSchool(e.target.value); setGrade(tree.find(n => n.school === e.target.value)?.grades[0]?.grade || '') }}>{tree.map(n => <option key={n.school}>{n.school}</option>)}</select></label>
      <label>학년<select value={grade} onChange={e => setGrade(e.target.value)}>{grades.map(g => <option key={g.grade}>{g.grade}</option>)}</select></label>
    </div>

    <div className="panel-heading"><div><p className="kicker">SOURCE MATERIAL</p><h3>학습자료</h3></div></div>
    {materials.length === 0
      ? <div className="empty-state"><b>이 학교·학년에 저장된 학습자료가 없습니다.</b><p>MCP가 문제를 생성해 저장하면 여기에 자료가 나타납니다.</p></div>
      : <div className="db-list">{materials.map(m => <article key={m.id}>
          <div><b>{m.title}</b><p>{m.school} · {m.grade} · 생성 시험 {m.test_count}개{m.publisher ? ` · ${m.publisher}` : ''}{m.lesson ? ` · ${m.lesson}` : ''}</p></div>
          <button className={materialId === m.id ? 'primary-action' : 'secondary-button'} style={{ marginTop: 0 }} onClick={() => setMaterialId(m.id)}>{materialId === m.id ? '선택됨' : '시험 보기'}</button>
        </article>)}</div>}

    {selectedMaterial && <>
      <div className="panel-heading"><div><p className="kicker">GENERATED TESTS</p><h3>{selectedMaterial.title} · 생성 시험</h3></div></div>
      {tests.length === 0
        ? <div className="empty-state"><b>이 학습자료에서 생성된 시험이 없습니다.</b></div>
        : <div className="db-list">{tests.map(t => <article key={t.id}>
            <div><b>{t.title}</b><p>{t.test_type || '시험'} · 문항 {t.question_count}개 · Page {t.source_page || '-'} · {new Date(t.created_at).toLocaleString('ko-KR')}</p>
              <small>{t.twin_label ? `출제자 Digital Twin: ${t.twin_label}` : '출제자 Digital Twin 미지정'} · source {t.generation_source}</small></div>
            <button className={testId === t.id ? 'primary-action' : 'secondary-button'} style={{ marginTop: 0 }} onClick={() => setTestId(t.id)}>{testId === t.id ? '열림' : '열기'}</button>
          </article>)}</div>}
    </>}

    {test && detail && <section className="work-panel" style={{ marginTop: 16 }}>
      <div className="panel-heading"><div><p className="kicker">출제 정보</p><h3>{test.title}</h3></div>
        <button className="btn-ghost" type="button" onClick={() => setShowAnswers(v => !v)}>{showAnswers ? '🙈 정답 숨기기' : '🔍 정답·해설 보기'}</button></div>
      <div className="exam-meta-box">
        <div className="meta-row"><span>학습자료 · 페이지</span><b>{test.source_material_title || selectedMaterial?.title} · Page {test.source_page || '-'}</b></div>
        <div className="meta-row"><span>시험 유형 · 난이도</span><b>{test.test_type || '-'} · {test.difficulty || '-'}</b></div>
        <div className="meta-row"><span>출제자 Digital Twin</span><b>{test.twin_label || '미지정'}{test.twin_id ? ` (#${test.twin_id})` : ''}</b></div>
        {test.twin_pattern && <div className="meta-row"><span>Digital Twin 출제 패턴</span><b>{test.twin_pattern}</b></div>}
        {test.target_vocabulary.length > 0 && <div className="meta-row"><span>target vocabulary ({test.target_vocabulary.length})</span><b>{test.target_vocabulary.join(', ')}</b></div>}
        {test.target_grammar.length > 0 && <div className="meta-row"><span>target grammar</span><b>{test.target_grammar.join(', ')}</b></div>}
        <div className="meta-row"><span>생성 source · 생성일</span><b>{test.generation_source} · {new Date(test.created_at).toLocaleString('ko-KR')}</b></div>
      </div>
      <div className="f2r-gen embedded-question-view">
        <div className={`exam-sheet${showAnswers ? ' show-answers' : ''}`}>
          <div className="question-list">{detail.questions.map((q, i) => <QuestionCard key={q.id} q={q} index={i + 1} />)}</div>
        </div>
      </div>
    </section>}
  </section>
}
