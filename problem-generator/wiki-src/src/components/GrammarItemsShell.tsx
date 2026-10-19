import GrammarBank from './GrammarBank'

// 내신위키와 분리된 문법 문제은행 독립 화면 셸. 전용 상단 메뉴(홈·내신위키 복귀) + 기존 GrammarBank 7탭.
export default function GrammarItemsShell() {
  return <div className="grammar-items-shell">
    <header className="grammar-items-topbar">
      <span className="grammar-items-brand">문법 문제은행</span>
      <nav className="grammar-items-nav" aria-label="문법 문제은행 바로가기">
        <a href="/">홈으로</a>
        <a href="/problem-generator/wiki/index.html">내신위키로 돌아가기</a>
      </nav>
    </header>
    <main className="tool-main">
      <GrammarBank />
    </main>
  </div>
}
