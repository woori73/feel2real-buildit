import { StoredQuestion } from '../lib/types'
import { isInlineUnderline, renderMarkupHtml } from '../lib/markup'
import { sanitizeQuestionHtml } from '../lib/wikiQuestion'

// 문제5(5차): 문제 생성 결과 화면 / 강사 문제 DB(전체보기) / 내신 실전 제작이 모두 이 컴포넌트
// 하나로 문항 전체(지문/발문/선택지/밑줄/조건/정답)를 렌더링한다. 화면마다 따로 문제를
// 재구성하지 않고, DB에 저장된 완전한 문항 데이터를 그대로 그린다.
// 정답/모범답안 표시 여부는 부모의 ".exam-sheet.show-answers" 클래스가 CSS로 제어한다
// (문제 생성기 결과 화면과 동일한 방식).

export const Markup = ({ text, as: Tag = 'span' }: { text: string; as?: 'span' | 'div' | 'p' }) =>
  <Tag dangerouslySetInnerHTML={{ __html: renderMarkupHtml(text || '') }} />
const Rich=({html,text,as:Tag='div'}:{html?:string;text:string;as?:'div'|'span'|'p'})=><Tag dangerouslySetInnerHTML={{__html:html?sanitizeQuestionHtml(html):renderMarkupHtml(text||'')}}/>

export default function QuestionCard({ q, index }: { q: StoredQuestion; index: number }) {
  const c=q.canonical
  if(c?.schema_version==='wiki-question-v1')return <article className={`q-card ${c.kind==='sa'?'q-sa':'q-mc'}`}>
    <header className="q-head"><span className="q-num">{c.kind==='sa'?'서답형 ':''}{index}</span><span className="q-type">{c.question_type}</span><span className="q-score">{c.scoring.score}점</span></header>
    <div className="q-instruction"><Rich html={c.instruction.html} text={c.instruction.text}/></div>
    {c.passage.status==='missing'&&<p className="passage-missing">지문을 찾지 못한 REVIEW 문항입니다.</p>}
    {c.passage.text&&<div className="source-text"><Rich html={c.passage.html} text={c.passage.text}/></div>}
    {c.conditions.length>0&&<div className="cond-box"><div className="cond-title">보기 · 조건</div><ul>{c.conditions.map((v,i)=><li key={i}><Rich as="span" html={v.html} text={v.text}/></li>)}</ul></div>}
    {c.kind==='mc'&&c.choices.length>0&&<ul className="opt-list">{c.choices.map(v=><li key={v.number} className={c.answer.choice_numbers.includes(v.number)?'is-answer':''}><span className="opt-num">{['','①','②','③','④','⑤'][v.number]||v.number}</span><Rich as="span" html={v.html} text={v.text}/></li>)}</ul>}
    {c.kind==='sa'&&<><div className="answer-format"><b>답안 형태</b>{c.scoring.answer_format||'지시문에 맞게 작성'}</div><div className="answer-line" aria-hidden="true"/></>}
    <section className="answer-panel"><b>정답 · 해설</b><p>정답: {c.kind==='mc'?c.answer.choice_numbers.join(', '):c.answer.sample_answer||'미등록'}</p>{c.explanation.text&&<Rich html={c.explanation.html} text={c.explanation.text}/>}</section>
  </article>
  const inline = isInlineUnderline(q.question_type, q.stem, q.source_text)
  const answerSet = new Set(q.answer)

  if (q.q_kind === 'sa') return <article className="q-card q-sa">
    <header className="q-head"><span className="q-num">서답형 {index}</span><span className="q-type">{q.question_type || '서답형'}</span><span className="q-score">{q.score}점</span></header>
    <div className="q-instruction"><Markup as="div" text={q.stem} /></div>
    {q.source_text && <div className="source-text"><Markup text={q.source_text} /></div>}
    {q.conditions.length > 0 && <div className="cond-box"><div className="cond-title">조건</div><ul>{q.conditions.map((c, ci) => <li key={ci}><Markup text={c} /></li>)}</ul></div>}
    <div className="answer-format"><b>답안 형태</b>{q.answer_format || '지시문에 맞게 작성'}</div>
    <div className="answer-line" aria-hidden="true" />
    <section className="answer-panel"><b>정답 · 채점 기준</b>{q.sample_answer && <p>모범답안: {q.sample_answer}</p>}<p>{q.grading_rule || '모범답안과 의미·문법이 일치해야 함'}</p></section>
  </article>

  return <article className="q-card q-mc">
    <header className="q-head">
      <span className="q-num">{index}</span>
      <span className="q-type">{q.question_type || '객관식'}</span>
      {q.multi ? <span className="tag-multi">정답 2개</span> : null}
      <span className="q-score">{q.score}점</span>
    </header>
    <div className="q-instruction"><Markup text={q.stem} /></div>
    {q.source_text && <div className="source-text"><Markup text={q.source_text} /></div>}
    {q.conditions.length > 0 && <div className="cond-box"><div className="cond-title">조건</div><ul>{q.conditions.map((condition, conditionIndex) => <li key={conditionIndex}><Markup text={condition} /></li>)}</ul></div>}
    {!inline && q.options.length > 0 && <ul className="opt-list">{q.options.map((opt, oi) => <li key={oi} className={answerSet.has(oi + 1) ? 'is-answer' : ''}><span className="opt-num">{['①', '②', '③', '④', '⑤'][oi]}</span><Markup text={opt} /></li>)}</ul>}
    <section className="answer-panel"><b>정답 · 해설</b><p>정답: {q.answer.map(a => ['', '①', '②', '③', '④', '⑤'][a] || a).join(', ') || '저장된 정답 없음'}</p>{q.grading_rule && <p>{q.grading_rule}</p>}</section>
  </article>
}
