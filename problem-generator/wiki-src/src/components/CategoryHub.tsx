import type { Page } from '../App'

type Hub = 'create' | 'student' | 'intelligence'
type Props = { hub: Hub; onNavigate: (page: Page) => void }

const hubs: Record<Hub, { eyebrow: string; title: string; description: string; actions: { page: Page; title: string; detail: string }[] }> = {
  create: {
    eyebrow: '만들기', title: '무엇을 만들까요?', description: '필요한 작업 하나를 고르면 해당 제작 화면만 엽니다.',
    actions: [
      { page: 'home', title: '내신 문제 만들기', detail: '학교·학년·시험별 신규·변형 문제' },
      { page: 'exam', title: '실전 문제지 만들기', detail: '선택 문항과 exam_draft로 DOCX 제작' },
      { page: 'school', title: '학교 프린트·자료 정리', detail: '사진·PDF OCR, 편집과 학교별 보관' },
      { page: 'database', title: '문제 DB 보기', detail: '저장된 강사 문항 선택과 재사용' },
      { page: 'textbook', title: '교과서 DB', detail: '출판사·Lesson별 수업 자료' },
    ],
  },
  student: {
    eyebrow: '학생', title: '학생 작업을 선택하세요', description: '오답을 새로 추출하거나 저장된 학생 기록을 이어서 봅니다.',
    actions: [
      { page: 'wrongextract', title: '오답 기록·추출', detail: '채점지 사진·PDF에서 오답 텍스트 추출·저장' },
      { page: 'wrongnote', title: '학생별 오답노트', detail: '누적 오답 확인, 수정과 복습 DOCX 제작' },
      { page: 'passagememory', title: '지문 암기', detail: '영문과 자연스러운 한글 해석을 문장별로 나란히 정리' },
      { page: 'passageanalysis', title: '지문 분석·문제 제작', detail: '구조·문법·흐름 분석과 한영 T/F 10문항 제작' },
    ],
  },
  intelligence: {
    eyebrow: '지능', title: '어떤 기억을 남길까요?', description: '수업 판단과 출제 자료를 BuildIt의 다음 작업 컨텍스트로 연결합니다.',
    actions: [
      { page: 'brain', title: '수업 인사이트 기록', detail: '학생 반응·설명 방식·다음 수업 아이디어' },
      { page: 'twin', title: 'Digital Twin', detail: '출제 자료와 학교별 패턴 가져오기' },
      { page: 'school', title: '학교별 DB·출제 기억', detail: '학교 프린트와 기출 맥락 확인' },
    ],
  },
}

export default function CategoryHub({ hub, onNavigate }: Props) {
  const content = hubs[hub]
  return <section className="category-hub">
    <button className="hub-back" onClick={() => onNavigate('state')}>← Wiki</button>
    <p className="kicker">{content.eyebrow}</p>
    <h1>{content.title}</h1>
    <p className="hub-description">{content.description}</p>
    <div className="hub-actions">{content.actions.map(action => <button key={`${action.page}-${action.title}`} onClick={() => onNavigate(action.page)}><b>{action.title}</b><span>{action.detail}</span><i aria-hidden="true">→</i></button>)}</div>
  </section>
}
