import type { Page } from '../App'

type Props = {
  onNavigate: (page: Page) => void
}

const studentActions: { label: string; detail: string; page: Page }[] = [
  { label: '학생별 오답노트', detail: '학생별 오답을 누적하고 맞춤 복습 자료를 준비합니다.', page: 'wrongnote' },
  { label: '학교 자료', detail: '학교·학년·시험별 기출과 수업 자료를 확인합니다.', page: 'school' },
  { label: '교과서 자료', detail: '출판사와 Lesson별 수업 자료를 확인하고 수업에 연결합니다.', page: 'textbook' },
]

export default function StudentHub({ onNavigate }: Props) {
  return (
    <section className="work-panel student-hub" aria-labelledby="student-hub-title">
      <div className="panel-heading">
        <div>
          <p className="kicker">STUDENT</p>
          <h2 id="student-hub-title">학생을 중심으로 이어서 작업합니다.</h2>
          <p>오답, 학교 자료, 교과서 자료를 한 흐름에서 확인하고 다음 복습 자료로 연결합니다.</p>
        </div>
      </div>
      <div className="state-actions">
        {studentActions.map(item => (
          <button key={item.label} className="state-action" onClick={() => onNavigate(item.page)}>
            <b>{item.label}</b>
            <span>{item.detail}</span>
          </button>
        ))}
      </div>
    </section>
  )
}
