import { StoredQuestion } from '../lib/types'
import { isInlineUnderline, renderMarkupHtml } from '../lib/markup'

// 문제5(5차): 문제 생성 결과 화면 / 강사 문제 DB(전체보기) / 내신 실전 제작이 모두 이 컴포넌트
// 하나로 문항 전체(지문/발문/선택지/밑줄/조건/정답)를 렌더링한다. 화면마다 따로 문제를
// 재구성하지 않고, DB에 저장된 완전한 문항 데이터를 그대로 그린다.
// 정답/모범답안 표시 여부는 부모의 ".exam-sheet.show-answers" 클래스가 CSS로 제어한다
// (문제 생성기 결과 화면과 동일한 방식).

export const Markup = ({ text, as: Tag = 'span' }: { text: string; as?: 'span' | 'div' | 'p' }) =>
  <Tag dangerouslySetInnerHTML={{ __html: renderMarkupHtml(text || '') }} />

export default function QuestionCard({ q, index }: { q: StoredQuestion; index: number }) {
  const inline = isInlineUnderline(q.question_type, q.stem, q.source_text)
  const answerSet = new Set(q.answer)

  if (q.q_kind === 'sa') return <div className="q-card q-sa">
    <div className="q-head"><span className="q-num">[서답형 {index}]</span><span className="q-score">[{q.score}점]</span></div>
    <Markup as="div" text={q.stem} />
    {q.source_text && <div className="source-text"><Markup text={q.source_text} /></div>}
    {q.conditions.length > 0 && <div className="cond-box"><div className="cond-title">조건</div><ul>{q.conditions.map((c, ci) => <li key={ci}><Markup text={c} /></li>)}</ul></div>}
    <div className="answer-format">✍️ 답안 형태: {q.answer_format || '지시문에 맞게 작성'}</div>
    <div className="answer-line">＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿＿</div>
    <div className="grading-rule">📌 채점 기준: {q.grading_rule || '모범답안과 의미·문법이 일치해야 함'}</div>
    <div className="sample-answer">✅ 모범답안: {q.sample_answer}</div>
  </div>

  return <div className="q-card q-mc">
    <div className="q-head">
      <span className="q-num">{index}.</span>
      <Markup text={q.stem} />
      {q.multi ? <span className="tag-multi">정답 2개</span> : null}
      <span className="q-score">[{q.score}점]</span>
    </div>
    {q.source_text && <div className="source-text"><Markup text={q.source_text} /></div>}
    {!inline && q.options.length > 0 && <ul className="opt-list">{q.options.map((opt, oi) => <li key={oi} className={answerSet.has(oi + 1) ? 'is-answer' : ''}><span className="opt-num">{['①', '②', '③', '④', '⑤'][oi]}</span><Markup text={opt} /></li>)}</ul>}
    {inline && <div className="inline-answer">✅ 정답: {q.answer.map(a => ['', '①', '②', '③', '④', '⑤'][a] || a).join(', ')}</div>}
  </div>
}
