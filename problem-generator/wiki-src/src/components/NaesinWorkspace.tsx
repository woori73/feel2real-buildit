import { useState } from 'react'
import type { Page } from '../App'
import NaesinWikiV1 from './NaesinWikiV1'
import GrammarBank from './GrammarBank'
import '../styles/naesin-workspace.css'

type Section = 'materials' | 'grammar' | 'production'
const sections: { id: Section; label: string; detail: string }[] = [
  { id: 'materials', label: '문제·자료 업로드', detail: '학교별 자료 · 기출 · 오답 · 완성본' },
  { id: 'grammar', label: '문법문제은행', detail: '검색 · 검수 · 선택함 · 문제집' },
  { id: 'production', label: '내신제작소', detail: '원재료 · AI 작업 · 버전 · 승인' },
]
const initialSection = (): Section => {
  const section = new URLSearchParams(window.location.search).get('section')
  return section === 'grammar' || section === 'production' ? section : 'materials'
}
export default function NaesinWorkspace({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [active, setActive] = useState<Section>(initialSection)
  const [opened, setOpened] = useState<Set<Section>>(() => new Set([initialSection()]))
  const select = (section: Section) => {
    setOpened(previous => new Set([...previous, section]))
    setActive(section)
  }
  // Keep visited panels mounted to preserve unfinished uploads, searches and project work.
  return <section className="naesin-workspace" aria-label="내신위키 통합 작업실">
    <header className="naesin-workspace-heading"><div><h1>내신위키</h1><p>문제를 올리고, 문법 문항을 찾고, 시험 자료를 완성하는 하나의 작업실</p></div><a href="/dashboard">대시보드</a></header>
    <nav className="naesin-workspace-tabs" aria-label="내신위키 기능">
      {sections.map(section => <button type="button" key={section.id} aria-pressed={active === section.id} aria-controls={`naesin-${section.id}`} onClick={() => select(section.id)}><b>{section.label}</b><span>{section.detail}</span></button>)}
    </nav>
    <section id="naesin-materials" aria-label="문제·자료 업로드" hidden={active !== 'materials'}>
      {opened.has('materials') && <NaesinWikiV1 onNavigate={page => page === 'grammabank' ? select('grammar') : onNavigate(page)} />}
    </section>
    <section id="naesin-grammar" aria-label="문법문제은행" hidden={active !== 'grammar'}>{opened.has('grammar') && <GrammarBank />}</section>
    <section id="naesin-production" aria-label="내신제작소" hidden={active !== 'production'}>
      {opened.has('production') && <iframe title="내신제작소 작업 화면" src="/naesin-production-studio?embedded=1" className="naesin-production-frame" />}
    </section>
  </section>
}
