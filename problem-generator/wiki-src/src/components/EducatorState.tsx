
import { useState } from 'react'
import type { Page } from '../App'

type Props = { onNavigate: (page: Page) => void }

const categories: { label: string; description: string; page?: Page }[] = [
  { label: '만들기', description: '문제 · 자료' },
  { label: '학생', description: '오답 · 기록', page: 'wrongnote' },
  { label: '지능', description: '수업 · 기억', page: 'brain' },
]

const createTools: { label: string; detail: string; page: Page }[] = [
  { label: '내신 문제', detail: '학교·학년·시험별 생성과 문제 변형', page: 'home' },
  { label: '실전 문제지', detail: '선택 문항으로 편집·인쇄·DOCX 제작', page: 'exam' },
  { label: '강사 문제 DB', detail: '생성·변형 문항 저장과 재사용', page: 'database' },
  { label: '교과서 DB', detail: '출판사·Lesson별 수업 자료', page: 'textbook' },
  { label: '학교별 DB · 프린트', detail: '학교별 기출과 OCR 자료 정리', page: 'school' },
  { label: '출제자 Digital Twin', detail: '분석 HTML 가져오기와 적용', page: 'twin' },
]

export default function EducatorState({ onNavigate }: Props) {
  const [createOpen, setCreateOpen] = useState(false)
  return <section className="ink-home" aria-labelledby="wiki-title">
    <header className="ink-brand">
      <h1 id="wiki-title"><span className="logo-build">Build</span><span className="logo-it">It</span> <span className="logo-wiki">Wiki</span></h1>
      <p>기록하면, 지능이 이어집니다.</p>
    </header>
    <nav className="ink-categories" aria-label="BuildIt Wiki 카테고리">
      {categories.map(category => <button key={category.label} className={createOpen && category.label === '만들기' ? 'active' : ''} onClick={() => category.label === '만들기' ? setCreateOpen(value => !value) : category.page && onNavigate(category.page)}>
        <b>{category.label}</b><span>{category.description}</span>
      </button>)}
    </nav>
    {createOpen && <section className="ink-tool-drawer" aria-label="만들기 도구">
      {createTools.map(tool => <button key={tool.label} onClick={() => onNavigate(tool.page)}><b>{tool.label}</b><span>{tool.detail}</span></button>)}
    </section>}
  </section>
}

