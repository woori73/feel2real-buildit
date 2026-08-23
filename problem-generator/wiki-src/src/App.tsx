import { useEffect, useRef, useState } from 'react'
import MiddleSchoolExamGenerator from './components/MiddleSchoolExamGenerator'
import StudentWrongNoteDashboard from './components/StudentWrongNoteDashboard'
import PassageWorkspace from './components/PassageWorkspace'
import TextbookDatabase from './components/TextbookDatabase'
import SchoolDatabase from './components/SchoolDatabase'
import ApiSettingsModal, { ApiSettings } from './components/ApiSettingsModal'
import DigitalTwinImport from './components/DigitalTwinImport'
import QuestionCard from './components/QuestionCard'
import EducatorState from './components/EducatorState'
import CategoryHub from './components/CategoryHub'
import InsightRecorder from './components/InsightRecorder'
import AssistantComposer, { AssistantMessage } from './components/AssistantComposer'
import CsatEnglishWorkspace from './components/CsatEnglishWorkspace'
import { StoredQuestion, createExamPaper, exportExamOffice, fetchExamDraft, fetchQuestions, saveExamDraft } from './lib/types'
import { api } from './lib/api'
import './styles/generator.css'

export type Page = 'state' | 'create' | 'student' | 'intelligence' | 'home' | 'twin' | 'database' | 'exam' | 'wrongextract' | 'wrongnote' | 'passagememory' | 'passageanalysis' | 'csat' | 'textbook' | 'school' | 'brain'

// role=ADMIN 같은 query parameter는 권한 컨텍스트일 뿐 Wiki의 초기 화면을 바꾸지 않는다.
const initialPage = (): Page => 'state'

const menu: { id: Page; label: string; icon: string }[] = [
  { id: 'state', label: '오늘의 상태', icon: '◌' },
  { id: 'home', label: '내신·수능 문제 제작', icon: '✦' },
  { id: 'twin', label: 'Digital Twin 관리', icon: '◈' },
  { id: 'database', label: '강사 문제 DB', icon: '▤' },
  { id: 'exam', label: '내신 실전 제작', icon: '▧' },
  { id: 'wrongextract', label: '오답 기록·추출', icon: '◉' },
  { id: 'wrongnote', label: '학생별 오답노트', icon: '◎' },
  { id: 'passagememory', label: '지문 암기', icon: '▥' },
  { id: 'passageanalysis', label: '지문 정리 + 내용일치 문제', icon: '▨' },
  { id: 'csat', label: '고3 수능 영어', icon: '◇' },
  { id: 'textbook', label: '교과서 DB', icon: '▦' },
  { id: 'school', label: '학교별 DB', icon: '▧' },
]

const descriptions: Record<Page, string> = {
  state: '현재 목표와 BuildIt이 기억하고 있는 자료를 확인하고 다음 작업을 시작합니다.',
  create: '필요한 제작 작업 하나를 선택합니다.',
  student: '학생 오답 기록과 누적 자료 작업을 선택합니다.',
  intelligence: '수업 판단과 출제 기억을 기록합니다.',
  brain: '수업 인사이트와 교육자의 판단을 기록해 다음 문제·자료 제작의 컨텍스트로 축적합니다.',
  home: '학교·학년·시험을 선택하면 Digital Twin이 자동 적용되어 신규 문제 생성과 기존 문제 변형을 모두 지원합니다.',
  twin: '강사가 만든 Digital Twin HTML을 업로드하면 학교·학년·시험 계층이 자동으로 생성됩니다.',
  database: '생성하거나 변형한 문항을 SQLite DB에서 확인하고 관리합니다.',
  exam: '선택한 문항으로 실전 시험지를 구성합니다.',
  wrongnote: '학생별 오답을 누적하고 맞춤 복습 자료를 준비합니다.',
  wrongextract: '채점지 사진·PDF에서 오답을 추출하고 학생 기록으로 저장합니다.',
  passagememory: '외부 영어 지문을 문장별 영문·한글 대조표로 정리해 암기 자료로 만듭니다.',
  passageanalysis: '외부 지문을 슬래시 원문, 직독직해, 정식 해석, 문법 가이드와 내용일치 문제로 정리합니다.',
  csat: '하프·정식 모의고사 문항과 학생 수능 오답을 유형별로 저장하고 시험지로 조합합니다.',
  textbook: '출판사와 Lesson별 수업 자료를 등록하고 관리합니다.',
  school: '학교·학년·시험별 기출과 수업 자료를 관리합니다.',
}

const storageLabels: Partial<Record<Page, string>> = {
  home: '문제 DB', database: '문제 DB', exam: '문제 DB / exam_draft',
  wrongextract: '학생 오답 기록', wrongnote: '학생 오답 기록', passagememory: '현재 작업 · 다운로드 가능', passageanalysis: '현재 작업 · 다운로드 가능', csat: '수능 문항·오답 SQLite 영구 저장', brain: 'Educator Brain · 수업 인사이트',
  twin: '학교별 Digital Twin', school: '이 브라우저 · 학교별 DB', textbook: '이 브라우저 · 교과서 DB',
}

const downloadBlob = (filename: string, blob: Blob) => { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url) }

export default function App() {
  const [page, setPage] = useState<Page>(initialPage)
  const [questions, setQuestions] = useState<StoredQuestion[]>([])
  const [selectedExamIds, setSelectedExamIds] = useState<number[]>([])
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())
  const [notice, setNotice] = useState('')
  const [examTitle, setExamTitle] = useState('2026학년도 영어 내신 실전 대비')
  const [academyName, setAcademyName] = useState('Feel to Real 영어학원')
  const [teacherName, setTeacherName] = useState('박정민 원장')
  const [showExamAnswers, setShowExamAnswers] = useState(false)
  const [savingExam, setSavingExam] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [assistantMessages, setAssistantMessages] = useState<AssistantMessage[]>([])
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [assistantBusy, setAssistantBusy] = useState(false)
  const [assistantState, setAssistantState] = useState(() => { try { return JSON.parse(sessionStorage.getItem('buildit_assistant_state') || '{}') } catch { return {} } })
  const [apiSettings, setApiSettings] = useState<ApiSettings | null>(() => { try { const saved = JSON.parse(sessionStorage.getItem('f2r_api_settings') || '{}') as Partial<ApiSettings>; return { provider: saved.provider || 'auto', geminiKey: saved.geminiKey || '', openrouterKey: saved.openrouterKey || '' } } catch { return null } })
  const draftLoaded = useRef(false)
  const refreshQuestions = () => { fetchQuestions().then(setQuestions).catch(() => undefined) }
  useEffect(() => { if (page === 'database' || page === 'exam') refreshQuestions() }, [page])
  useEffect(() => { fetchExamDraft().then(draft => { if (draft.question_ids.length) setSelectedExamIds(draft.question_ids); if (draft.title) setExamTitle(draft.title); if (draft.academy_name) setAcademyName(draft.academy_name); if (draft.teacher_name) setTeacherName(draft.teacher_name) }).catch(() => undefined).finally(() => { draftLoaded.current = true }) }, [])
  useEffect(() => { if (!draftLoaded.current) return; saveExamDraft({ title: examTitle, academy_name: academyName, teacher_name: teacherName, question_ids: selectedExamIds }).catch(() => undefined) }, [selectedExamIds, examTitle, academyName, teacherName])
  const ready = (text = '현재 준비 중인 기능입니다.') => setNotice(text)
  const deleteQuestion = async (id: number) => { if (!window.confirm('이 문항을 삭제할까요?')) return; await api(`questions/${id}`, { method: 'DELETE' }); setQuestions(items => items.filter(item => item.id !== id)); setSelectedExamIds(ids => ids.filter(itemId => itemId !== id)); setExpandedIds(ids => { const next = new Set(ids); next.delete(id); return next }) }
  const toggleExamSelection = (id: number) => setSelectedExamIds(ids => ids.includes(id) ? ids.filter(itemId => itemId !== id) : [...ids, id])
  const sendToExam = (id: number) => { setSelectedExamIds(ids => ids.includes(id) ? ids : [...ids, id]); setPage('exam') }
  const sendSelectedToExam = () => { if (selectedExamIds.length) setPage('exam') }
  const toggleExpanded = (id: number) => setExpandedIds(ids => { const next = new Set(ids); if (next.has(id)) next.delete(id); else next.add(id); return next })
  const saveExamPaper = async () => { await createExamPaper({ title: examTitle, academy_name: academyName, teacher_name: teacherName, question_ids: selectedExamIds }) }
  const exportOffice = async () => { if (!selectedExamIds.length) { ready('강사 문제 DB에서 문항을 먼저 선택해 주세요.'); return } setSavingExam(true); try { await saveExamPaper(); const blob = await exportExamOffice({ title: examTitle, academy_name: academyName, teacher_name: teacherName, question_ids: selectedExamIds }); downloadBlob(`${(examTitle || '내신_실전_시험지').replace(/[\\/:*?"<>|]/g, '_')}.docx`, blob); setNotice('문제지와 정답을 하나로 묶은 오피스 2007(.docx) 파일을 내보냈습니다.') } catch (err) { setNotice(err instanceof Error ? err.message : '문제지·정답지를 내보내지 못했습니다.') } finally { setSavingExam(false) } }
  const examQuestions = selectedExamIds.map(id => questions.find(item => item.id === id)).filter((item): item is StoredQuestion => Boolean(item))
  const mcCount = examQuestions.filter(q => q.q_kind === 'mc').length, saCount = examQuestions.filter(q => q.q_kind === 'sa').length, totalScore = examQuestions.reduce((sum, q) => sum + (q.score || 0), 0)
  const runAssistant = async (raw: string, files: File[] = []) => {
    const command = raw.trim()
    if (!command && !files.length) return
    const userMessage: AssistantMessage = { role: 'user', content: command || `첨부: ${files.map(file => file.name).join(', ')}` }
    const history = [...assistantMessages, userMessage]
    setAssistantMessages(history); setAssistantOpen(true); setAssistantBusy(true)
    try {
      let pageContext = {}; try { pageContext = JSON.parse(sessionStorage.getItem('buildit_page_context') || '{}') } catch { /* use assistant state */ }
      const response = await api('assistant/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: command, provider: apiSettings?.provider || 'auto', api_key: apiSettings?.geminiKey || '', openrouter_api_key: apiSettings?.openrouterKey || '', state: { ...assistantState, ...pageContext, current_page: page }, history: history.slice(-8), attachments: files.map(file => ({ name: file.name, type: file.type })) }) })
      if (!response.ok) throw new Error('Assistant 응답을 준비하지 못했습니다.')
      const body = await response.json()
      const nextState = body.state || assistantState
      setAssistantState(nextState); sessionStorage.setItem('buildit_assistant_state', JSON.stringify(nextState))
      setAssistantMessages(items => [...items, { role: 'assistant', content: body.text, page: body.page as Page, action: body.action }])
    } catch {
      setAssistantMessages(items => [...items, { role: 'assistant', content: '요청을 정리했습니다. 연결 서비스가 잠시 응답하지 않아도 기존 기록은 변경되지 않습니다. 학교·학년·시험과 시험 범위 지문 또는 프린트를 알려 주시면 출제 경향 확인 → 유형 추천 → 문제 제작 → 검수 → 저장 순서로 안내하겠습니다.', page: 'home', action: '내신 문제 준비하기' }])
    } finally { setAssistantBusy(false) }
  }

  const isHome = page === 'state'
  const isHub = page === 'create' || page === 'student' || page === 'intelligence'
  const parentHub: Page = ['wrongextract', 'wrongnote'].includes(page) ? 'student' : page === 'brain' || page === 'twin' ? 'intelligence' : 'create'
  return <div className={`tool-shell${isHome ? ' wiki-home-shell' : ''}${isHub ? ' category-shell' : ''}`}>
    {!isHome && !isHub && <aside className="side-nav compact-nav"><button className="side-wiki-home" onClick={() => setPage('state')}><span>BuildIt</span> Wiki</button><nav aria-label="현재 작업 내비게이션"><button onClick={() => setPage(parentHub)}>← {parentHub === 'student' ? '학생' : parentHub === 'intelligence' ? '지능' : '만들기'}</button></nav><div className="side-bottom"><button onClick={() => setSettingsOpen(true)}>⚙ 설정</button><a className="side-export-link" href={`${import.meta.env.BASE_URL}api/export/project`}>⇩ 내보내기</a></div></aside>}
    <main className={`tool-main${isHome ? ' wiki-home-main' : ''}${isHub ? ' category-main' : ''}`}>{!isHome && !isHub && <header className="tool-header"><div><h1>{menu.find(item => item.id === page)?.label || (page === 'brain' ? '수업 인사이트' : '')}</h1><p>{descriptions[page]}</p>{storageLabels[page] && <p className="storage-location">저장 위치 · {storageLabels[page]}</p>}</div><span className="context-chip">{page === 'database' ? `저장 ${questions.length}문항 · 선택 ${selectedExamIds.length}개` : ''}</span></header>}
      {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}
      {page === 'state' && <EducatorState onNavigate={setPage} />}
      {page === 'create' && <CategoryHub hub="create" onNavigate={setPage} />}
      {page === 'student' && <CategoryHub hub="student" onNavigate={setPage} />}
      {page === 'intelligence' && <CategoryHub hub="intelligence" onNavigate={setPage} />}
      {page === 'brain' && <InsightRecorder />}
      {page === 'home' && <MiddleSchoolExamGenerator onNavigate={setPage} apiSettings={apiSettings} />}
      {page === 'twin' && <DigitalTwinImport onApplied={profile => { setPage('home'); setNotice(`${profile.school} Digital Twin을 저장했습니다. 문제 생성기에서 학교를 선택하면 자동 적용됩니다.`) }} />}
      {page === 'wrongextract' && <StudentWrongNoteDashboard apiSettings={apiSettings} mode="extract" />}
      {page === 'wrongnote' && <StudentWrongNoteDashboard apiSettings={apiSettings} mode="notebook" />}
      {page === 'passagememory' && <PassageWorkspace mode="memory" apiSettings={apiSettings} />}
      {page === 'passageanalysis' && <PassageWorkspace mode="analysis" apiSettings={apiSettings} />}
      {page === 'csat' && <CsatEnglishWorkspace />}
      {page === 'textbook' && <TextbookDatabase />}
      {page === 'school' && <SchoolDatabase onAnalyze={() => setPage('twin')} apiSettings={apiSettings} />}
      {page === 'database' && <section className="work-panel"><div className="panel-heading"><div><p className="kicker">TEACHER QUESTION DATABASE</p><h2>강사 문제 DB</h2><p>신규 생성·변형한 문항이 SQLite DB에 영구 저장됩니다.</p></div><div className="twin-actions"><button className="secondary-button" onClick={refreshQuestions}>새로고침</button><button className="primary-action" style={{ marginTop: 0 }} disabled={!selectedExamIds.length} onClick={sendSelectedToExam}>선택 문항으로 실전 제작 ({selectedExamIds.length})</button></div></div>{questions.length === 0 ? <div className="empty-state"><b>저장된 문항이 없습니다.</b><p>문제 생성기에서 만든 문항을 저장하면 이곳에서 관리할 수 있어요.</p><button className="primary-action" onClick={() => setPage('home')}>문제 생성으로 이동</button></div> : <div className="db-list">{questions.map(item => <article key={item.id} style={{ flexWrap: 'wrap' }}><input type="checkbox" aria-label={`${item.id} 선택`} checked={selectedExamIds.includes(item.id)} onChange={() => toggleExamSelection(item.id)} /><div><b>{item.question_type || '문항'} · {item.mode === 'transform' ? '변형' : '신규'} · {item.score}점</b><p>{item.school} · {item.grade} · {item.exam} · {new Date(item.created_at).toLocaleString('ko-KR')}</p><small>{item.stem.slice(0, 80)}</small></div><button onClick={() => toggleExpanded(item.id)}>{expandedIds.has(item.id) ? '간단히' : '전체보기'}</button><button onClick={() => sendToExam(item.id)}>실전 제작</button><button onClick={() => deleteQuestion(item.id)}>삭제</button>{expandedIds.has(item.id) && <div style={{ flexBasis: '100%', marginTop: 12 }}><div className="f2r-gen embedded-question-view"><div className="exam-sheet show-answers"><div className="question-list"><QuestionCard q={item} index={1} /></div></div></div></div>}</article>)}</div>}</section>}
      {page === 'exam' && <section className="work-panel"><div className="panel-heading"><div><p className="kicker">EXAM MAKER</p><h2>내신 실전 문제 제작</h2><p>강사 문제 DB에서 선택한 문항으로 실전 시험지를 구성합니다.</p></div><button className="btn-ghost" type="button" onClick={() => setShowExamAnswers(v => !v)}>{showExamAnswers ? '🙈 정답 숨기기' : '🔍 정답 보기'}</button></div><div className="exam-settings"><label>시험지 제목<input value={examTitle} onChange={event => setExamTitle(event.target.value)} /></label><label>학원명<input value={academyName} onChange={event => setAcademyName(event.target.value)} /></label><label>강사명<input value={teacherName} onChange={event => setTeacherName(event.target.value)} /></label></div><div className="f2r-gen embedded-question-view"><div className={`exam-sheet${showExamAnswers ? ' show-answers' : ''}`}><div className="exam-meta-box"><div className="meta-row"><span>시험지</span><b>{examTitle}</b></div><div className="meta-row"><span>학교</span><b>{examQuestions[0] ? `${examQuestions[0].school} · ${examQuestions[0].grade}` : '문제 DB에서 문항 선택 필요'}</b></div><div className="meta-row"><span>문항 구성</span><b>객관식 {mcCount} · 서답형 {saCount} · 총 {totalScore}점</b></div></div>{examQuestions.length > 0 ? <div className="question-list">{examQuestions.map((item, index) => <QuestionCard key={item.id} q={item} index={index + 1} />)}</div> : <p className="empty-paper">강사 문제 DB에서 문항을 선택해 실전 제작으로 보내 주세요.</p>}<div className="footer-brand">{academyName} · {teacherName}</div></div></div><button className="primary-action" disabled={savingExam || !selectedExamIds.length} onClick={() => void exportOffice()}>{savingExam ? '내보내는 중...' : '오피스 2007 내보내기'}</button></section>}
    </main>
    <AssistantComposer messages={assistantOpen ? assistantMessages : []} busy={assistantBusy} onRun={runAssistant} onNavigate={setPage} onDismiss={() => setAssistantOpen(false)} />
    <ApiSettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} onSaved={settings => { setApiSettings(settings); setNotice('AI API 설정을 저장했습니다. 문제 생성기에서 바로 사용할 수 있습니다.') }} />
  </div>
}

