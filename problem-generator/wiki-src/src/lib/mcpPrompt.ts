import type { ExamContext } from './wikiContext'

// MCP 문제제작 화면에서 Claude(또는 다른 MCP client)에 붙여 넣을 지시문을 만든다.
// 서버를 호출하지 않는 순수 함수만 둔다 — 실제 자료 조회·저장은 MCP tool이 한다.

export const MCP_ENDPOINT_PATH = '/problem-generator/wiki/api/mcp'

export type McpJobKind = 'expected' | 'final' | 'jikbo'
export type McpKindMix = 'school' | 'mcOnly' | 'custom'

export type McpJob = {
  kind: McpJobKind
  count: number
  mix: McpKindMix
  saCount: number
  note: string
}

export const JOB_LABELS: Record<McpJobKind, string> = {
  expected: '예상문제 시험지',
  final: 'FINAL 자료',
  jikbo: '직보(시험 직전 정리)',
}

/** 문항 수를 20문항 안팎 batch로 나눈다 (docs/exam/WIKI_TUTOR_BROWSER_CONTEXT.md §6). */
export function splitBatches(count: number, size = 20): string[] {
  const total = Math.max(0, Math.floor(count))
  const batches: string[] = []
  for (let start = 1; start <= total; start += size) {
    const end = Math.min(total, start + size - 1)
    batches.push(`Q${start}~${end}`)
  }
  return batches
}

export function mcpEndpointUrl(origin: string): string {
  return origin.replace(/\/+$/, '') + MCP_ENDPOINT_PATH
}

const examLine = (c: ExamContext) => `${c.school} ${c.grade} · ${c.year}년 ${c.semester} · ${c.exam === '전체' ? '시험 전체' : c.exam}`
const toolArgs = (c: ExamContext) => `school="${c.school}", grade="${c.grade}", year=${c.year}, semester="${c.semester}", exam="${c.exam}"`

function mixLine(job: McpJob): string {
  if (job.mix === 'mcOnly') return '모두 객관식'
  if (job.mix === 'custom') {
    const sa = Math.min(Math.max(0, Math.floor(job.saCount)), job.count)
    return `객관식 ${job.count - sa}문항 · 서답형 ${sa}문항`
  }
  return '학교 기출의 객관식·서답형 비율을 따름'
}

/** 화면에서 고른 시험·작업 조건으로 MCP 작업 지시문을 만든다. */
export function buildMcpPrompt(context: ExamContext, job: McpJob): string {
  const lines: string[] = []
  const note = job.note.trim()
  lines.push(`[BuildIt Wiki MCP 작업] ${examLine(context)} ${JOB_LABELS[job.kind]}`)
  lines.push('')
  if (job.kind === 'jikbo') {
    lines.push(`1. get_jikbo_context(${toolArgs(context)})로 FINAL 완성본과 그 뒤 수업 인사이트·학생 오답을 읽는다.`)
    lines.push('   ready가 false이면 FINAL이 아직 없으니 멈추고 알려 준다.')
    lines.push('2. 시험 직전에 볼 핵심만 정리한 직보를 HTML로 만든다.')
    lines.push(`3. save_jikbo(${toolArgs(context)}, ...)로 저장한다.`)
  } else {
    lines.push(`1. get_final_context(${toolArgs(context)})로 기출·학교 프린트·Digital Twin·수업 인사이트·학생 오답·기존 문제를 읽는다.`)
    lines.push('   결과가 비어 있어도 자료가 없다고 단정하지 말고, 로그인된 브라우저에서 내신위키를 열어 확인한다.')
    lines.push('2. 읽은 자료로 시험범위·핵심 본문·어휘·문법·기출 유형을 한 번 정리한다(Context Pack). 단계마다 다시 분석하지 않는다.')
    if (job.kind === 'expected') {
      const batches = splitBatches(job.count)
      lines.push(`3. 전체 ${job.count}문항 Blueprint를 먼저 정한다. 구성: ${mixLine(job)}.`)
      lines.push(`4. ${batches.join(' → ')} 순서로 나눠 만들고, 각 묶음마다 정답을 따로 검증한 뒤 다음으로 넘어간다.`)
      lines.push('5. 다 만든 뒤 중복 문항·본문 편중·정답 번호 분포를 한 번 확인한다.')
      lines.push(`6. 검수한 문항을 Wiki에 저장해 달라: save_generated_questions(school="${context.school}", grade="${context.grade}", exam="${context.exam}", questions=[...])로 문항을, save_final(${toolArgs(context)}, ...)로 시험지 HTML을 저장한다.`)
      lines.push('   저장이 실패하면 "생성: 성공 / 저장: 실패"로 나눠 알려 준다.')
    } else {
      lines.push('3. 시험 전 최종 정리용 FINAL 자료를 HTML로 만든다.')
      lines.push(`4. save_final(${toolArgs(context)}, ...)로 저장한다.`)
    }
  }
  if (note) {
    lines.push('')
    lines.push(`추가 요청: ${note}`)
  }
  lines.push('')
  lines.push('끝나면 만든 것, 근거로 쓴 자료, 저장 결과, 애매한 문항을 짧게 알려 준다.')
  return lines.join('\n')
}

export type ContextSummaryRow = { key: string; label: string; count: number; essential: boolean }

const listLength = (value: unknown) => (Array.isArray(value) ? value.length : 0)

/** GET /api/workspaces/final/context 응답에서 화면에 보여 줄 자료 수를 뽑는다. */
export function summarizeFinalContext(body: Record<string, unknown> | null | undefined): ContextSummaryRow[] {
  const b = body || {}
  return [
    { key: 'past_exams', label: '기출시험지', count: listLength(b.past_exams), essential: true },
    { key: 'school_prints', label: '학교 프린트', count: listLength(b.school_prints), essential: true },
    { key: 'digital_twin', label: 'Digital Twin', count: b.digital_twin ? 1 : 0, essential: false },
    { key: 'class_insights', label: '수업 인사이트', count: listLength(b.class_insights), essential: false },
    { key: 'student_errors', label: '학생 오답', count: listLength(b.student_errors), essential: false },
    { key: 'ai_work_logs', label: 'AI 작업기록', count: listLength(b.ai_work_logs), essential: false },
    { key: 'generated_questions', label: '저장된 문항', count: listLength(b.generated_questions), essential: false },
    { key: 'final_deliverables', label: 'FINAL 완성본', count: listLength(b.final_deliverables), essential: false },
  ]
}
