import type { StoredQuestion } from './types'

const CIRCLED = ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧', '⑨', '⑩']

/** 저장된 문항의 근거·검증 기록을 한 줄로 만든다. 기록이 하나도 없으면 null. */
export function reviewLine(q: Pick<StoredQuestion, 'q_kind' | 'answer' | 'accepted_answers' | 'evidence' | 'verify' | 'teacher'>): string | null {
  const { evidence, verify, teacher } = q
  if (!evidence && !verify && !teacher) return null
  const parts: string[] = []
  const status = verify?.status
  parts.push(status ? (status === '일치' ? '검증 일치' : `검수 필요(${status})`) : '검증 기록 없음')
  const sources = [evidence?.past_exam, evidence?.school_print].filter(Boolean)
  if (sources.length) parts.push(`근거: ${sources.join(' / ')}`)
  if (evidence?.changed) parts.push(`바꾼 부분: ${evidence.changed}`)
  const made = q.q_kind === 'mc' ? q.answer.map(n => CIRCLED[n - 1] || String(n)).join(', ') : (q.accepted_answers || []).join(' / ')
  if (verify?.independent_answer) parts.push(`제작 정답 ${made || '-'} · 독립 풀이 ${verify.independent_answer}`)
  if (verify?.note) parts.push(`쟁점: ${verify.note}`)
  if (teacher?.action) parts.push(`교사 조치: ${teacher.action}`)
  return parts.join(' · ')
}
