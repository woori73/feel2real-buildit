import { useEffect, useRef, useState } from 'react'
import MiddleSchoolExamGenerator from './components/MiddleSchoolExamGenerator'
import StudentWrongNoteDashboard from './components/StudentWrongNoteDashboard'
import TextbookDatabase from './components/TextbookDatabase'
import SchoolDatabase from './components/SchoolDatabase'
import ApiSettingsModal, { ApiSettings } from './components/ApiSettingsModal'
import DigitalTwinImport from './components/DigitalTwinImport'
import QuestionCard from './components/QuestionCard'
import EducatorState from './components/EducatorState'
import { StoredQuestion, createExamPaper, exportExamOffice, fetchExamDraft, fetchQuestions, saveExamDraft } from './lib/types'
import { api } from './lib/api'
import './styles/generator.css'

export type Page = 'state' | 'home' | 'twin' | 'database' | 'exam' | 'wrongnote' | 'textbook' | 'school' | 'brain'

// role=ADMIN 같은 query parameter는 권한 컨텍스트일 뿐 Wiki의 초기 화면을 바꾸지 않는다.
const initialPage = (): Page => 'state'

const menu: { id: Page; label: string; icon: string }[] = [
  { id: 'state', label: '오늘의 상태', icon: '◌' },
  { id: 'home', label: '중등 내신 문제 생성기', icon: '✦' },
  { id: 'twin', label: 'Digital Twin 관리', icon: '◈' },
  { id: 'database', label: '강사 문제 DB', icon: '▤' },
  { id: 'exam', label: '내신 실전 제작', icon: '▧' },
  { id: 'wrongnote', label: '학생별 오답노트', icon: '◎' },
  { id: 'textbook', label: '교과서 DB', icon: '▦' },
  { id: 'school', label: '학교별 DB', icon: '▧' },
]

const descriptions: Record<Page, string> = {
  state: '현재 목표와 BuildIt이 기억하고 있는 자료를 확인하고 다음 작업을 시작합니다.',
  brain: '수업 인사이트와 교육자의 판단을 기록해 다음 문제·자료 제작의 컨텍스트로 축적합니다.',
  home: '학교·학년·시험을 선택하면 Digital Twin이 자동 적용되어 신규 문제 생성과 기존 문제 변형을 모두 지원합니다.',
  twin: '강사가 만든 Digital Twin HTML을 업로드하면 학교·학년·시험 계층이 자동으로 생성됩니다.',
  database: '생성하거나 변형한 문항을 SQLite DB에서 확인하고 관리합니다.',
  exam: '선택한 문항으로 실전 시험지를 구성합니다.',
  wrongnote: '학생별 오답을 누적하고 맞춤 복습 자료를 준비합니다.',
  textbook: '출판사와 Lesson별 수업 자료를 등록하고 관리합니다.',
  school: '학교·학년·시험별 기출과 수업 자료를 관리합니다.',
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
  const [assistantInput, setAssistantInput] = useState('')
  const [assistantReply, setAssistantReply] = useState<{ text: string; page: Page; action: string } | null>(null)
  const [apiSettings, setApiSettings] = useState<ApiSettings | null>(() => { try { const saved = JSON.parse(sessionStorage.getItem('f2r_api_settings') || '{}') as Partial<ApiSettings>; return saved.provider && (saved.geminiKey || saved.openaiKey) ? { provider: saved.provider, geminiKey: saved.geminiKey || '', openaiKey: saved.openaiKey || '' } : null } catch { return null } })
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
  const runAssistant = (raw: string) => {
    const command = raw.trim()
    if (!command) return
    const normalized = command.toLowerCase()
    const route: Page = /오답|학생/.test(command) ? 'wrongnote' : /digital\s*twin|디지털\s*트윈|출제자/.test(normalized) ? 'twin' : /프린트|학교 자료|기출 정리/.test(command) ? 'school' : /실전|문제지|docx|인쇄/.test(normalized) ? 'exam' : /인사이트|수업 기록|지능/.test(command) ? 'brain' : 'home'
    const guidance: Record<Page, { text: string; action: string }> = {
      state: { text: '원하시는 작업을 조금 더 자세히 말씀해 주세요.', action: '홈에 머물기' },
      home: { text: '할 수 있는 작업: 학교별 내신 문제 생성·변형. 필요한 자료: 학교·학년·시험과 영어 지문 또는 저장된 프린트. 다음 행동: 기존 생성기에서 조건과 자료를 확인하세요.', action: '내신 문제 준비하기' },
      wrongnote: { text: '할 수 있는 작업: 학생별 오답 조회·OCR 저장·복습 DOCX 제작. 필요한 자료: 학생 선택과 채점된 시험지 또는 오답 사진. 다음 행동: 기존 학생 오답 화면에서 기록을 선택하세요.', action: '학생 오답 열기' },
      brain: { text: '할 수 있는 작업: 수업 인사이트와 교육자 판단 기록. 필요한 자료: 수업·학생·발견 내용. 다음 행동: 기존 Educator Brain 기록 화면을 여세요.', action: '수업 기록 열기' },
      school: { text: '할 수 있는 작업: 학교 프린트 OCR·편집·DOCX 정리. 필요한 자료: 학교·학년·시험과 원본 사진/PDF. 다음 행동: 기존 학교별 DB에서 프린트를 올리세요.', action: '학교 자료 열기' },
      exam: { text: '할 수 있는 작업: 저장 문항과 현재 exam_draft로 실전 문제지·DOCX 제작. 필요한 자료: 문제 DB에서 선택한 문항. 다음 행동: 기존 실전 제작 화면에서 초안을 확인하세요.', action: '실전 문제지 열기' },
      twin: { text: '할 수 있는 작업: 기존 출제 자료를 Digital Twin으로 가져와 학교 맥락에 적용. 필요한 자료: 분석 가능한 HTML·JSON·PDF·TXT. 다음 행동: 기존 Digital Twin 관리 화면에서 자료를 선택하세요.', action: 'Digital Twin 열기' },
      database: { text: '저장된 강사 문항을 확인하고 실전 문제지로 보낼 수 있습니다.', action: '문제 DB 열기' },
      textbook: { text: '교과서와 Lesson별 수업 자료를 확인하고 제작 작업에 연결할 수 있습니다.', action: '교과서 DB 열기' },
    }
    try { sessionStorage.setItem('buildit_assistant_context', JSON.stringify({ request: command, route, createdAt: Date.now() })) } catch { /* routing still works without browser storage */ }
    setAssistantReply({ ...guidance[route], page: route })
    setAssistantInput('')
  }

  return <div className={`tool-shell${page === 'state' ? ' wiki-home-shell' : ''}`}>
    {page !== 'state' && <aside className="side-nav"><button className="side-wiki-home" onClick={() => setPage('state')}><span>BuildIt</span> Wiki</button><p className="tool-subtitle">Digital Twin 기반<br />교육자 제작 도구</p><nav aria-label="주요 메뉴">{menu.filter(item => item.id !== 'state').map(item => <button key={item.id} className={page === item.id ? 'active' : ''} onClick={() => setPage(item.id)}><span>{item.icon}</span>{item.label}</button>)}</nav><div className="side-bottom"><button onClick={() => setSettingsOpen(true)}>⚙ 설정</button><button onClick={() => ready()}>♲ 휴지통</button><a className="side-export-link" href={`${import.meta.env.BASE_URL}api/export/project`}>⇩ 데이터 내보내기</a></div></aside>}
    <main className={`tool-main${page === 'state' ? ' wiki-home-main' : ''}`}>{page !== 'state' && <header className="tool-header"><div><h1>{menu.find(item => item.id === page)?.label || (page === 'brain' ? '지능' : '')}</h1><p>{descriptions[page]}</p></div><span className="context-chip">{page === 'database' ? `저장 ${questions.length}문항 · 선택 ${selectedExamIds.length}개` : ''}</span></header>}
      {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="안내 닫기">×</button></div>}
      {page === 'state' && <EducatorState onNavigate={setPage} />}
      {page === 'brain' && <section className="work-panel"><div className="panel-heading"><div><p className="kicker">EDUCATOR BRAIN</p><h2>수업 인사이트 기록</h2><p>수업에서 발견한 학생 반응, 설명 방식, 출제 포인트와 다음 수업 아이디어를 기록합니다.</p></div></div><div className="empty-state"><b>오늘 수업에서 무엇을 발견하셨나요?</b><p>이 기록은 이후 문제·프린트·강의 상품 제작에서 재사용할 교육자 컨텍스트가 됩니다.</p></div></section>}
      {page === 'home' && <MiddleSchoolExamGenerator onNavigate={setPage} apiSettings={apiSettings} />}
      {page === 'twin' && <DigitalTwinImport onApplied={profile => { setPage('home'); setNotice(`${profile.school} Digital Twin을 저장했습니다. 문제 생성기에서 학교를 선택하면 자동 적용됩니다.`) }} />}
      {page === 'wrongnote' && <StudentWrongNoteDashboard apiSettings={apiSettings} />}
      {page === 'textbook' && <TextbookDatabase />}
      {page === 'school' && <SchoolDatabase onAnalyze={() => setPage('twin')} apiSettings={apiSettings} />}
      {page === 'database' && <section className="work-panel"><div className="panel-heading"><div><p className="kicker">TEACHER QUESTION DATABASE</p><h2>강사 문제 DB</h2><p>신규 생성·변형한 문항이 SQLite DB에 영구 저장됩니다.</p></div><div className="twin-actions"><button className="secondary-button" onClick={refreshQuestions}>새로고침</button><button className="primary-action" style={{ marginTop: 0 }} disabled={!selectedExamIds.length} onClick={sendSelectedToExam}>선택 문항으로 실전 제작 ({selectedExamIds.length})</button></div></div>{questions.length === 0 ? <div className="empty-state"><b>저장된 문항이 없습니다.</b><p>문제 생성기에서 만든 문항을 저장하면 이곳에서 관리할 수 있어요.</p><button className="primary-action" onClick={() => setPage('home')}>문제 생성으로 이동</button></div> : <div className="db-list">{questions.map(item => <article key={item.id} style={{ flexWrap: 'wrap' }}><input type="checkbox" aria-label={`${item.id} 선택`} checked={selectedExamIds.includes(item.id)} onChange={() => toggleExamSelection(item.id)} /><div><b>{item.question_type || '문항'} · {item.mode === 'transform' ? '변형' : '신규'} · {item.score}점</b><p>{item.school} · {item.grade} · {item.exam} · {new Date(item.created_at).toLocaleString('ko-KR')}</p><small>{item.stem.slice(0, 80)}</small></div><button onClick={() => toggleExpanded(item.id)}>{expandedIds.has(item.id) ? '간단히' : '전체보기'}</button><button onClick={() => sendToExam(item.id)}>실전 제작</button><button onClick={() => deleteQuestion(item.id)}>삭제</button>{expandedIds.has(item.id) && <div style={{ flexBasis: '100%', marginTop: 12 }}><div className="f2r-gen embedded-question-view"><div className="exam-sheet show-answers"><div className="question-list"><QuestionCard q={item} index={1} /></div></div></div></div>}</article>)}</div>}</section>}
      {page === 'exam' && <section className="work-panel"><div className="panel-heading"><div><p className="kicker">EXAM MAKER</p><h2>내신 실전 문제 제작</h2><p>강사 문제 DB에서 선택한 문항으로 실전 시험지를 구성합니다.</p></div><button className="btn-ghost" type="button" onClick={() => setShowExamAnswers(v => !v)}>{showExamAnswers ? '🙈 정답 숨기기' : '🔍 정답 보기'}</button></div><div className="exam-settings"><label>시험지 제목<input value={examTitle} onChange={event => setExamTitle(event.target.value)} /></label><label>학원명<input value={academyName} onChange={event => setAcademyName(event.target.value)} /></label><label>강사명<input value={teacherName} onChange={event => setTeacherName(event.target.value)} /></label></div><div className="f2r-gen embedded-question-view"><div className={`exam-sheet${showExamAnswers ? ' show-answers' : ''}`}><div className="exam-meta-box"><div className="meta-row"><span>시험지</span><b>{examTitle}</b></div><div className="meta-row"><span>학교</span><b>{examQuestions[0] ? `${examQuestions[0].school} · ${examQuestions[0].grade}` : '문제 DB에서 문항 선택 필요'}</b></div><div className="meta-row"><span>문항 구성</span><b>객관식 {mcCount} · 서답형 {saCount} · 총 {totalScore}점</b></div></div>{examQuestions.length > 0 ? <div className="question-list">{examQuestions.map((item, index) => <QuestionCard key={item.id} q={item} index={index + 1} />)}</div> : <p className="empty-paper">강사 문제 DB에서 문항을 선택해 실전 제작으로 보내 주세요.</p>}<div className="footer-brand">{academyName} · {teacherName}</div></div></div><button className="primary-action" disabled={savingExam || !selectedExamIds.length} onClick={() => void exportOffice()}>{savingExam ? '내보내는 중...' : '오피스 2007 내보내기'}</button></section>}
    </main>
    <form className={`assistant-dock${page === 'state' ? ' on-home' : ''}`} onSubmit={event => { event.preventDefault(); runAssistant(assistantInput) }}>
      <label htmlFor="buildit-assistant">무엇을 만들거나 기록할까요?</label><div><input id="buildit-assistant" value={assistantInput} onChange={event => setAssistantInput(event.target.value)} placeholder="예: 신구중 2학년 중간 문제 만들어줘" /><button type="submit" aria-label="요청 보내기">↑</button></div>
      {assistantReply && <div className="assistant-reply" role="status"><p>{assistantReply.text}</p><button type="button" onClick={() => { setPage(assistantReply.page); setAssistantReply(null) }}>{assistantReply.action} →</button></div>}
    </form>
    <ApiSettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} onSaved={settings => { setApiSettings(settings); setNotice('AI API 설정을 저장했습니다. 문제 생성기에서 바로 사용할 수 있습니다.') }} />
  </div>
}

