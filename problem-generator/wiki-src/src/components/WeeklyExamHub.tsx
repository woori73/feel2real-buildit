type ExamContext = {
  school: string
  grade: string
  year: number
  semester: string
  exam: string
}

type Props = {
  context: ExamContext
  pastExamCount: number
  schoolPrintCount: number
  wrongCount: number
  aiWorkLogCount: number
  hasDigitalTwin: boolean
  lastContextUpdate?: string
  onOpenContext: () => void
  onOpenWeeklyWorkspace: () => void
  onOpenWrongReview: () => void
}

type SetType = '40' | '70' | '40A' | '40B'

const SET_POLICY: Record<string, SetType[]> = {
  '압구정중학교|2학년': ['40', '70'],
  '압구정중|2학년': ['40', '70'],
  '신구중학교|2학년': ['40', '70'],
  '신구중|2학년': ['40', '70'],
  '신구중학교|3학년': ['40A', '40B'],
  '신구중|3학년': ['40A', '40B'],
  '신사중학교|3학년': ['40A', '40B'],
  '신사중|3학년': ['40A', '40B'],
}

const setTypesFor = (context: ExamContext): SetType[] =>
  SET_POLICY[`${context.school}|${context.grade}`] || ['40']

const examLabel = (exam: string) =>
  exam === '중간고사' ? '중간고사' : exam === '기말고사' ? '기말고사' : exam

export default function WeeklyExamHub({
  context,
  pastExamCount,
  schoolPrintCount,
  wrongCount,
  aiWorkLogCount,
  hasDigitalTwin,
  lastContextUpdate,
  onOpenContext,
  onOpenWeeklyWorkspace,
  onOpenWrongReview,
}: Props) {
  const setTypes = setTypesFor(context)
  const isPartial = context.school.includes('압구정') && context.grade.includes('2')
  const readySources = [pastExamCount > 0, schoolPrintCount > 0, hasDigitalTwin].filter(Boolean).length

  return <section className="naesin-repeat-hub" aria-label="반복 제작 허브">
    <div className="current-context-card">
      <div>
        <p className="kicker">CURRENT EXAM CONTEXT</p>
        <h2>{context.school} · {context.grade}</h2>
        <p>{context.year}년 · {context.semester} · {examLabel(context.exam)}</p>
      </div>
      <div className="context-health">
        <span className={isPartial ? 'context-status partial' : 'context-status living'}>
          Context {isPartial ? 'PARTIAL' : 'LIVING'}
        </span>
        <small>기출 {pastExamCount} · 학습지/필기 {schoolPrintCount} · Digital Twin {hasDigitalTwin ? '연결' : '미연결'}</small>
        <small>제작·검수 기록 {aiWorkLogCount} · 학생 오답 {wrongCount}</small>
        {lastContextUpdate && <small>마지막 Context 업데이트 {new Date(lastContextUpdate).toLocaleString('ko-KR')}</small>}
      </div>
      <button type="button" className="secondary-button" onClick={onOpenContext}>자료 보기 · AI Context</button>
    </div>

    <div className="repeat-entry-grid">
      <article className="repeat-entry-card weekly">
        <p className="kicker">WEEKLY EXAM</p>
        <h2>주간 실전 모의고사</h2>
        <p>현재 Living Context를 참조해 매주 제작하고, 사용자 승인 뒤에만 등록·공개합니다.</p>
        <div className="weekly-set-list" aria-label="제작 가능한 세트 유형">
          {setTypes.map(type => <span key={type}><b>{type}</b><small>DRAFT 준비 전</small></span>)}
        </div>
        <dl className="weekly-meta-preview">
          <div><dt>회차</dt><dd>다음 회차</dd></div>
          <div><dt>상태</dt><dd>REVIEW → APPROVED → FINAL/PUBLISHED</dd></div>
          <div><dt>Context</dt><dd>{isPartial ? 'PARTIAL · 새 자료 등록 시 갱신' : `${readySources}/3 핵심 자료 연결`}</dd></div>
        </dl>
        <button type="button" className="primary-action" onClick={onOpenWeeklyWorkspace}>시험 제작자료 보기</button>
      </article>

      <article className="repeat-entry-card wrong-review">
        <p className="kicker">STUDENT ERROR CONTEXT</p>
        <h2>오답 복습</h2>
        <p>학교 공통 Context와 섞지 않고 학생별 100발100중 오답을 누적합니다.</p>
        <div className="wrong-summary"><strong>{wrongCount}</strong><span>현재 시험 Context 오답 자료</span></div>
        <ul>
          <li>원문·학생 답·정답·유형</li>
          <li>Lesson·문법·어휘·출처</li>
          <li>오답 횟수·마지막 오답일·복습 상태</li>
        </ul>
        <button type="button" className="primary-action" onClick={onOpenWrongReview}>오답 복습 열기</button>
      </article>
    </div>
    <p className="approval-gate-note">AI 제작 완료는 공개가 아닙니다. 검토 대기 → 사용자 승인 → Wiki 등록 → 학생 공개 순서를 유지합니다.</p>
  </section>
}
