
import { useState } from 'react'
import type { Page } from '../App'

type Props = { onNavigate: (page: Page) => void }

const categories = [
  { label: '만들기', description: '문제 · 자료' },
  { label: '학생', description: '오답 · 기록' },
  { label: '지능', description: '수업 · 기억' },
]

const categoryTools: Record<string, { label: string; detail: string; page: Page }[]> = {
  만들기: [
    { label: '내신 문제', detail: '학교·학년·시험별 생성과 문제 변형', page: 'home' },
    { label: '실전 문제지', detail: 'exam_draft를 이어 편집·DOCX 제작', page: 'exam' },
    { label: '강사 문제 DB', detail: '생성·변형 문항 저장과 재사용', page: 'database' },
    { label: '학교별 DB · 프린트', detail: '학교별 기출과 OCR 자료 정리', page: 'school' },
    { label: '교과서 DB', detail: '출판사·Lesson별 수업 자료', page: 'textbook' },
  ],
  학생: [{ label: '학생별 오답노트', detail: '학생 기록·OCR·오답 통합과 DOCX', page: 'wrongnote' }],
  지능: [
    { label: 'Digital Twin 관리', detail: '출제 자료 가져오기와 학교 맥락 적용', page: 'twin' },
    { label: '학교별 DB', detail: '학교 자료와 출제 맥락 확인', page: 'school' },
    { label: '수업 인사이트', detail: 'Educator Brain에 수업 판단 기록', page: 'brain' },
  ],
}

export default function EducatorState({ onNavigate }: Props) {
  const [openCategory, setOpenCategory] = useState<string | null>(null)
  return <section className="ink-home" aria-labelledby="wiki-title">
    <header className="ink-brand">
      <h1 id="wiki-title"><span className="logo-build">Build</span><span className="logo-it">It</span> <span className="logo-wiki">Wiki</span></h1>
      <p>기록하면, 지능이 이어집니다.</p>
    </header>
    <nav className="ink-categories" aria-label="BuildIt Wiki 카테고리">
      {categories.map(category => <button key={category.label} className={openCategory === category.label ? 'active' : ''} aria-expanded={openCategory === category.label} onClick={() => setOpenCategory(current => current === category.label ? null : category.label)}>
        <b>{category.label}</b><span>{category.description}</span>
      </button>)}
    </nav>
    {openCategory && <section className="ink-tool-drawer" aria-label={`${openCategory} 도구`}>
      {categoryTools[openCategory].map(tool => <button key={tool.label} onClick={() => onNavigate(tool.page)}><b>{tool.label}</b><span>{tool.detail}</span></button>)}
    </section>}
  </section>
}

