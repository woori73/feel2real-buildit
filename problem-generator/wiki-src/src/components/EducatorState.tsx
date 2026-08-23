import { useState } from 'react'
import type { Page } from '../App'

type Props = { onNavigate: (page: Page) => void; onCommand: (command: string) => void }

const categories: { label: string; description: string; page?: Page }[] = [
  { label: '만들기', description: '문제 · 프린트 · 실전 자료' },
  { label: '학생', description: '오답 · 복습 · 학생 기록', page: 'wrongnote' },
  { label: '지능', description: '수업 인사이트 · Digital Twin', page: 'brain' },
]
const createTools: { label: string; detail: string; page: Page }[] = [
  { label: '내신 문제', detail: '학교·학년·시험별 생성과 문제 변형', page: 'home' },
  { label: '실전 문제지', detail: '선택 문항으로 편집·인쇄·DOCX 제작', page: 'exam' },
  { label: '강사 문제 DB', detail: '생성·변형 문항 저장과 재사용', page: 'database' },
  { label: '교과서 DB', detail: '출판사·Lesson별 수업 자료', page: 'textbook' },
  { label: '학교별 DB · 프린트', detail: '학교별 기출과 OCR 자료 정리', page: 'school' },
  { label: '출제자 Digital Twin', detail: '분석 HTML 가져오기와 적용', page: 'twin' },
]
const quickActions = ['내신 문제 만들어줘', '실전 문제지 만들기', '학생 오답 분석해줘', '학교 프린트 정리', '수업 인사이트 기록']

export default function EducatorState({ onNavigate, onCommand }: Props) {
  const [createOpen, setCreateOpen] = useState(false)
  return <section className="ink-home" aria-labelledby="wiki-title">
    <div className="paper-grain" aria-hidden="true" />
    <div className="ink-landscape" aria-hidden="true"><i /><i /><i /><span /></div>
    <header className="ink-brand">
      <p>FEEL TO REAL EDUCATOR WORKSPACE</p>
      <h1 id="wiki-title"><span>BuildIt</span> Wiki</h1>
      <div className="ink-seal">빌딧</div>
    </header>
    <nav className="ink-categories" aria-label="BuildIt Wiki 카테고리">
      {categories.map(category => <button key={category.label} className={createOpen && category.label === '만들기' ? 'active' : ''} onClick={() => category.label === '만들기' ? setCreateOpen(value => !value) : category.page && onNavigate(category.page)}><b>{category.label}</b><span>{category.description}</span></button>)}
    </nav>
    {createOpen && <section className="ink-tool-drawer" aria-label="만들기 도구">
      {createTools.map(tool => <button key={tool.label} onClick={() => onNavigate(tool.page)}><b>{tool.label}</b><span>{tool.detail}</span></button>)}
    </section>}
    <section className="quiet-state" aria-label="교육자 상태">
      <div><span>현재 준비</span><b>준비 중인 시험을 선택해 이어서 작업하세요</b><button onClick={() => onNavigate('home')}>내신 문제 열기 →</button></div>
      <div><span>최근 작업</span><b>문제 DB와 exam_draft가 작업 상태를 기억합니다</b><button onClick={() => onNavigate('database')}>최근 문항 보기 →</button></div>
      <div><span>다음 기록</span><b>수업에서 발견한 판단을 지능에 남겨보세요</b><button onClick={() => onNavigate('brain')}>인사이트 기록 →</button></div>
    </section>
    <section className="home-prompt" aria-label="BuildIt Assistant">
      <h2>무엇을 도와드릴까요?</h2><p>말로 시작하면 이미 연결된 BuildIt 도구로 안내합니다.</p>
      <div>{quickActions.map(action => <button key={action} onClick={() => onCommand(action)}>{action}</button>)}</div>
    </section>
  </section>
}
