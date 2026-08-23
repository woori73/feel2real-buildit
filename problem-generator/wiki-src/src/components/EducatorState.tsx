import type { Page } from '../App'

type Props = {
  onNavigate: (page: Page) => void
  onCommand: (command: string) => void
}

const actions: { label: string; detail: string; page: Page; command?: string }[] = [
  { label: '시험 문제', detail: '학교·학년·시험에 맞는 문제를 준비합니다.', page: 'home' },
  { label: '수업 자료', detail: '학교 프린트와 지문을 수업용 자료로 정리합니다.', page: 'school', command: '학교 프린트를 수업자료로 만들어줘' },
  { label: '학생 자료', detail: '오답 사진과 학생별 자료를 복습 자료로 만듭니다.', page: 'wrongnote', command: '학생 오답 자료를 정리해줘' },
  { label: '수업 인사이트', detail: '오늘 수업에서 얻은 판단과 노하우를 BRAIN에 남깁니다.', page: 'brain', command: '오늘 수업 인사이트를 기록할게' },
]

export default function EducatorState({ onNavigate, onCommand }: Props) {
  const run = (item: (typeof actions)[number]) => {
    if (item.command) onCommand(item.command)
    onNavigate(item.page)
  }

  return <section className="educator-state" aria-labelledby="educator-state-title">
    <div className="state-wave" aria-hidden="true">
      <i /><i /><i /><i /><i />
    </div>
    <div className="state-copy">
      <p className="state-kicker">EDUCATOR STATE · CURRENT SCENARIO</p>
      <h1 id="educator-state-title">오늘 무엇을 준비하고 계세요?</h1>
      <p className="state-intro">BuildIt은 문제만 만들고 끝나지 않습니다. 지금 준비하는 수업과 학생, 자료를 알려주면 작업의 맥락을 기억하고 다음 제작에 다시 활용합니다.</p>
    </div>

    <div className="state-actions">
      {actions.map(item => <button key={item.label} className="state-action" onClick={() => run(item)}>
        <b>{item.label}</b><span>{item.detail}</span>
      </button>)}
    </div>

    <div className="state-memory">
      <div><span>현재 목표</span><b>아직 정해지지 않았습니다.</b></div>
      <div><span>BuildIt이 알고 있는 것</span><b>학교 · 교과서 · 프린트 · 수업 인사이트</b></div>
      <div><span>조금 더 필요한 것</span><b>지금 가르치는 학생과 준비 중인 시험</b></div>
      <div><span>다음 행동</span><b>위에서 가장 가까운 작업을 선택해 주세요.</b></div>
    </div>

    <div className="state-command">
      <p>BuildIt Assistant에게 바로 말하기</p>
      <div>
        <button onClick={() => onCommand('신구중 2학년 중간고사 문제 만들어줘')}>시험 문제 만들어줘</button>
        <button onClick={() => onCommand('이 학교 프린트를 수업자료로 만들어줘')}>프린트 수업자료로 만들어줘</button>
        <button onClick={() => onCommand('이 외부 지문을 어휘·구문·지문정리까지 만들어줘')}>지문 정리해줘</button>
      </div>
    </div>
  </section>
}
