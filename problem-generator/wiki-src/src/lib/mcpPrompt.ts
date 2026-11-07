import type { ExamContext } from './wikiContext'

// MCP 문제제작 화면에서 Claude(또는 다른 MCP client)에 붙여 넣을 지시문을 만든다.
// 서버를 호출하지 않는 순수 함수만 둔다 — 실제 자료 조회·저장은 MCP tool이 한다.

export const MCP_ENDPOINT_PATH = '/problem-generator/wiki/api/mcp'

export type McpJobKind = 'expected' | 'jikbo'
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

// 인자에는 위키에 저장된 형식(2학년, 2학기, 기말고사) 그대로 넣는다 — 다른 형식이면 MCP 조회가 비어 온다.
const examLine = (c: ExamContext) => `${c.school} ${c.grade} · ${c.year} ${c.semester} ${c.exam}`
const toolArgs = (c: ExamContext) => `school="${c.school}", grade="${c.grade}", year=${c.year}, semester="${c.semester}", exam="${c.exam}"`

function mixLine(job: McpJob): string {
  if (job.mix === 'mcOnly') return '모두 객관식'
  if (job.mix === 'custom') {
    const sa = Math.min(Math.max(0, Math.floor(job.saCount)), job.count)
    return `객관식 ${job.count - sa}문항 · 서답형 ${sa}문항`
  }
  return '학교 기출의 객관식·서답형 비율을 따름'
}

const EXPECTED_PRINCIPLES = [
  '1. 해당 학교 기출을 철저히 분석하고 저장한다.',
  '2. 발문, 유형, 선택지 형식, 난이도는 기출을 그대로 따른다. 별도 지시가 있으면 지시를 따른다.',
  '3. 기출에 없는 유형, 편집 양식, 기호는 쓰지 않는다.',
  '4. 지정한 범위 안에서만 출제하고, 기출에 나온 지문이나 내용은 다시 쓰지 않는다.',
  '5. 시험지는 해당 학교 시험지와 똑같이 편집한다(폰트, 글씨 크기, 줄간격). 학원 로고를 넣고, 하단의 학교 저작권 문구는 뺀다.',
]
const JIKBO_PRINCIPLES = [
  '1. 학습지 원본만 근거로 만든다.',
  '2. 원본에 없는 내용, 문구, 번호는 추가하거나 바꾸지 않는다.',
]

/** 화면에서 고른 시험·작업 조건으로 MCP 작업 지시문을 만든다. 원칙과 기본 순서만 담고 세부 작업은 사용자 지시에 맡긴다. */
export function buildMcpPrompt(context: ExamContext, job: McpJob): string {
  const expected = job.kind === 'expected'
  const lines: string[] = []
  lines.push(expected
    ? `[BuildIt Wiki] ${examLine(context)} · 예상문제 시험지 ${job.count}문항`
    : `[BuildIt Wiki] ${examLine(context)} · 직보`)
  lines.push('')
  lines.push(expected
    ? `get_final_context(${toolArgs(context)})로 자료를 읽는다.`
    : `get_final_context(${toolArgs(context)})로 학교 프린트(학습지 원본)를 읽는다.`)
  lines.push('')
  lines.push(expected ? '■ 출제 원칙' : '■ 제작 원칙')
  lines.push(...(expected ? EXPECTED_PRINCIPLES : JIKBO_PRINCIPLES))
  lines.push('')
  lines.push('세부 작업은 사용자 지시에 따른다.')
  // 화면에서 직접 고른 조건만 사용자 지시로 덧붙인다. 기본값(학교 기출 비율)은 원칙 2가 이미 다룬다.
  const requests = [
    ...(expected && job.mix !== 'school' ? [`문항 구성: ${mixLine(job)}`] : []),
    ...(job.note.trim() ? [`추가 요청: ${job.note.trim()}`] : []),
  ]
  if (requests.length) lines.push('', '■ 사용자 지시', ...requests)
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
