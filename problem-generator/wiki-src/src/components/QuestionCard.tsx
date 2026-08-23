import { renderMarkedText } from '../lib/markup'
import type { GeneratedQuestion, StoredQuestion } from '../lib/types'

const typeLabels: Record<string, string> = {
  main_idea: '주제', title: '제목', detail_match: '내용 일치', detail_mismatch: '내용 불일치', blank: '빈칸',
  grammar: '어법', vocabulary: '어휘', sentence_order: '순서', sentence_insert: '문장 삽입', summary: '요약',
  context_word: '문맥상 낱말', referent: '지칭 대상', subjective: '서답형', summary_subjective: '요약 서답형',
}

type Props = { q: GeneratedQuestion | StoredQuestion; index: number }

export default function QuestionCard({ q, index }: Props) {
  const qType = 'question_type' in q ? q.question_type : ''
  const qKind = 'q_kind' in q ? q.q_kind : 'mc'
  return <article className="question-card">
    <div className="q-head"><span className="q-num">{index}</span><span className="q-type">{typeLabels[qType] || qType || '문항'}</span>{'score' in q && <span className="q-score">{q.score}점</span>}</div>
    {'passage' in q && q.passage && <div className="q-passage">{renderMarkedText(q.passage)}</div>}
    <div className="q-stem">{renderMarkedText(q.stem)}</div>
    {qKind === 'mc' && 'choices' in q && Array.isArray(q.choices) && q.choices.length > 0 && <ol className="q-choices">{q.choices.map((choice, i) => <li key={i}>{renderMarkedText(choice)}</li>)}</ol>}
    <div className="q-answer"><b>정답</b> {'answer' in q ? String(q.answer) : ''}</div>
    {'explanation' in q && q.explanation && <div className="q-explanation"><b>해설</b> {q.explanation}</div>}
  </article>
}
