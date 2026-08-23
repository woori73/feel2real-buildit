
import type { Page } from '../App'

type Props = { onNavigate: (page: Page) => void }

const categories = [
  { label: '만들기', description: '문제 · 자료', page: 'create' as Page },
  { label: '학생', description: '오답 · 기록', page: 'student' as Page },
  { label: '지능', description: '수업 · 기억', page: 'intelligence' as Page },
]

export default function EducatorState({ onNavigate }: Props) {
  return <section className="ink-home" aria-labelledby="wiki-title">
    <header className="ink-brand">
      <h1 id="wiki-title"><span className="logo-build">Build</span><span className="logo-it">It</span> <span className="logo-wiki">Wiki</span></h1>
      <p>기록하면, 지능이 이어집니다.</p>
    </header>
    <nav className="ink-categories" aria-label="BuildIt Wiki 카테고리">
      {categories.map(category => <button key={category.label} onClick={() => onNavigate(category.page)}>
        <b>{category.label}</b><span>{category.description}</span>
      </button>)}
    </nav>
  </section>
}

