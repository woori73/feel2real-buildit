import { useState } from 'react'

type Props = { title: string }
type ReportType = '시험 결과' | '주간 리포트' | '월간 리포트' | '상담 요약'

const reportCopy: Record<ReportType, { label: string; summary: string; next: string }> = {
  '시험 결과': { label: '시험 결과 리포트', summary: '지문의 흐름을 끝까지 유지하며 주제를 정확히 찾았습니다.', next: '문장 형태를 적용하는 연습을 이어가겠습니다.' },
  '주간 리포트': { label: '주간 학습 리포트', summary: '수업 중 조건에 밑줄을 긋고 답안을 수정하는 과정을 확인했습니다.', next: '핵심 문장 구조를 짧게 복습하도록 안내하겠습니다.' },
  '월간 리포트': { label: '월간 성장 리포트', summary: '독해 정확도는 상승했고 서술형 수행은 안정적으로 유지되었습니다.', next: '어휘 복습 주기를 조정해 학습 균형을 돕겠습니다.' },
  '상담 요약': { label: '학부모 상담 전 요약', summary: '학습 태도와 독해 과정에서 확인된 변화가 누적되고 있습니다.', next: '다음 평가 전 서술형 문장 완성도를 점검하겠습니다.' },
}

export default function ParentReportDashboard({ title }: Props) {
  const [type, setType] = useState<ReportType>('시험 결과')
  const [showJson, setShowJson] = useState(false)
  const [notice, setNotice] = useState('')
  const content = reportCopy[type]
  const reportJson = {
    report_type: type,
    source: '예시 시험 결과 · 수업 관찰 기록',
    fact_accuracy: 98,
    observation_coverage: 94,
    growth_analysis: { reading_accuracy: '상승', writing: '유지', vocabulary: '보완 필요' },
    next_guidance: content.next,
  }
  const prepareOutput = (format: string) => setNotice(`${format} 형식의 예시 리포트를 준비했어요. 실제 발송이나 다운로드는 포함하지 않습니다.`)

  return <section className="parent-dashboard" aria-label="학부모 리포트 엔진">
    <div className="parent-head">
      <div><p className="kicker">F2R PARENT REPORT ENGINE</p><h2>학부모 리포트</h2><p>{title} · 입력된 예시 학습 데이터만으로 작성한 객관적 안내문입니다.</p></div>
      <span className="report-safe">● 사실 기반 검수 완료</span>
    </div>
    <div className="report-source"><span>현재 적용 데이터</span><b>예시중 3학년 · 영어 중간평가 · 수업 관찰 4건</b><small>시험 결과 · 학습 로그 · 오답 기록 · 교사 관찰</small></div>
    <div className="report-controls" aria-label="리포트 종류 선택">
      {(Object.keys(reportCopy) as ReportType[]).map(item => <button key={item} className={type === item ? 'active' : ''} onClick={() => { setType(item); setNotice('') }}>{item}</button>)}
      <button className="json-button" onClick={() => setShowJson(!showJson)}>{showJson ? 'JSON 닫기' : 'JSON 보기'}</button>
    </div>
    {showJson && <pre className="json-preview">{JSON.stringify(reportJson, null, 2)}</pre>}
    <div className="report-layout">
      <article className="parent-report-card">
        <div className="report-card-head"><div><p className="kicker">{content.label.toUpperCase()}</p><h3>박서준 학생 학습 안내</h3></div><span>2026.08.03</span></div>
        <section><h4>시험 결과</h4><div className="score-row"><b>86점</b><span>독해 90% · 문법 78% · 어휘 80%</span></div></section>
        <section><h4>강점과 근거</h4><p>{content.summary}</p><small>근거: 독해 문항 8개 중 7개 정답 · 수업 관찰 기록</small></section>
        <section><h4>보완점</h4><p>문장 형태를 조금 더 안정적으로 적용하면 점수가 더욱 향상될 수 있습니다.</p><small>근거: 관계절 문항과 서술형 문장 완성에서 확인</small></section>
        <section><h4>다음 지도 방향</h4><p>{content.next}</p></section>
        <section><h4>총평</h4><p>현재 독해 과정은 안정적입니다. 문법 적용 정확도를 보완하며 다음 평가를 준비하겠습니다.</p></section>
      </article>
      <aside className="report-insight-card">
        <p className="kicker">GROWTH ANALYSIS</p><h3>이전 기록과 비교</h3>
        <div className="growth-row"><span>독해 정확도</span><b className="up">↑ 상승</b></div>
        <div className="growth-row"><span>서술형 수행</span><b className="same">→ 유지</b></div>
        <div className="growth-row"><span>어휘 정확도</span><b className="down">↓ 보완</b></div>
        <div className="observation"><p className="kicker">OBSERVATION</p><p>조건에 밑줄을 긋고 답안을 수정하는 모습을 확인했습니다.</p><small>사진·영상은 예시 관찰 기록으로만 표현됩니다.</small></div>
      </aside>
    </div>
    <div className="report-quality">
      <div><p className="kicker">REPORT QUALITY</p><h3>사실·문체·가독성 검수 완료</h3></div>
      <div><b>98%</b><small>Fact Accuracy</small></div><div><b>94%</b><small>Observation Coverage</small></div><div><b>96%</b><small>Parent Readability</small></div>
      <p>입력되지 않은 사실, 과장 표현, 반복 문장과 긴 문단을 점검했습니다. 교사 관찰과 실제 예시 데이터만 반영했습니다.</p>
    </div>
    <div className="report-output"><div><p className="kicker">OUTPUT FORMAT</p><h3>검토용 출력 준비</h3><p>원장 검토 후 활용할 수 있는 형식으로 정리합니다.</p></div><div>{['카카오톡', 'PDF', 'Word', 'HTML'].map(format => <button key={format} onClick={() => prepareOutput(format)}>{format} 준비</button>)}</div></div>
    {notice && <p className="report-notice">{notice}</p>}
  </section>
}
