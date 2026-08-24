
import type { Page } from '../App'

type Props = { onNavigate: (page: Page) => void }

const categories = [
  { label: '학교별 문제 제작·변형', description: 'Digital Twin을 반영한 새 문제와 기존문제 변형', page: 'home' as Page },
  { label: '학교 프린트 정리', description: '사진·PDF를 편집 가능한 학습자료로 정리', page: 'school' as Page },
  { label: '학생 오답 정리', description: '오답 사진을 복습용 HTML·Word 자료로 정리', page: 'wrongextract' as Page },
  { label: '외부지문 학습자료', description: '해석·어휘·문장분석·암기자료 제작', page: 'passageanalysis' as Page },
]

export default function EducatorState({ onNavigate }: Props) {
  return <section className="ink-home" aria-labelledby="wiki-title">
    <header className="ink-brand">
      <h1 id="wiki-title"><span className="logo-build">Build</span><span className="logo-it">It</span> <span className="logo-wiki">Wiki</span></h1>
      <p>기록하면, 지능이 이어집니다.</p>
    </header>
    <nav className="ink-categories core-task-cards" aria-label="BuildIt Wiki 핵심 작업">
      {categories.map(category => <button key={category.label} onClick={() => onNavigate(category.page)}>
        <b>{category.label}</b><span>{category.description}</span>
      </button>)}
    </nav>
  </section>
}


