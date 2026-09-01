import { api } from './api'
import { isSelfContained, normalizeChromeQuestion, WIKI_QUESTION_SCHEMA, wikiQuestionToLegacy } from './wikiQuestion'

type ToolDefinition = {
  name: string
  description: string
  inputSchema: Record<string, unknown>
  annotations: { readOnlyHint: boolean }
  execute: (input: Record<string, unknown>) => Promise<unknown>
}

type ModelContext = { registerTool: (tool: ToolDefinition) => Promise<void> | void }

const getModelContext = (): ModelContext | null => {
  const candidate = (document as Document & { modelContext?: ModelContext }).modelContext
  return candidate?.registerTool ? candidate : null
}

const readJson = async (response: Response) => {
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(typeof body?.detail === 'string' ? body.detail : 'Chrome 문제 변형 요청에 실패했습니다.')
  return body
}

const emit = (name: string, detail: unknown) => window.dispatchEvent(new CustomEvent(name, { detail }))

const previewInputSchema = {
  type: 'object', required: ['question'],
  properties: {
    question: { type:'object', description: 'Wiki canonical 또는 호환 가능한 Chrome 변형 문항. Workbench가 wiki-question-v1로 정규화합니다.' },
    revision_note: { type: 'string' },
  },
}

const previewTransformedQuestion = async (input: Record<string, unknown>) => {
  const question=normalizeChromeQuestion(input.question)
  if(!isSelfContained(question))throw new Error('변형 문항이 wiki-question-v1 self-contained 검사를 통과하지 못했습니다.')
  const context=JSON.parse(sessionStorage.getItem('buildit_chrome_workbench_context')||'{}')
  sessionStorage.setItem('buildit_chrome_preview_question',JSON.stringify({source_question_id:context.source_question_id,question}))
  emit('buildit-chrome-question-preview', {...input,question})
  return { previewed: true, tool: 'preview_transformed_question' }
}

export const registerChromeQuestionTools = async () => {
  const modelContext = getModelContext()
  if (!modelContext) return false

  await modelContext.registerTool({
    name: 'get_wiki_question_schema',
    description: 'Wiki가 저장하고 Preview하는 self-contained 문항의 wiki-question-v1 JSON Schema를 읽습니다.',
    inputSchema: { type: 'object', properties: {} },
    annotations: { readOnlyHint: true },
    execute: async () => WIKI_QUESTION_SCHEMA,
  })

  await modelContext.registerTool({
    name: 'get_current_question',
    description: '현재 Workbench QID의 발문, 전체 지문과 HTML 표시, 조건, 선택지, 정답, 해설을 wiki-question-v1로 읽습니다.',
    inputSchema: { type: 'object', properties: {} },
    annotations: { readOnlyHint: true },
    execute: async () => {
      const context=JSON.parse(sessionStorage.getItem('buildit_chrome_workbench_context')||'{}')
      return {question_id:context.source_question_id||null,question:context.source_question||null}
    },
  })

  await modelContext.registerTool({
    name: 'get_chrome_question_workbench_context',
    description: '현재 Chrome 문제 변형 Workbench에서 선택된 시험 Context, Digital Twin, Insight, 학교 프린트와 원본 문제를 읽습니다.',
    inputSchema: { type: 'object', properties: {} },
    annotations: { readOnlyHint: true },
    execute: async () => {
      const raw = sessionStorage.getItem('buildit_chrome_workbench_context') || '{}'
      return JSON.parse(raw)
    },
  })

  // Canonical public name used in the Workbench/Chrome instructions.
  await modelContext.registerTool({
    name: 'preview_transformed_question',
    description: '변형한 문항을 저장하기 전에 Workbench의 변형본 Preview에 표시합니다. 호환 입력을 wiki-question-v1로 정규화합니다.',
    inputSchema: previewInputSchema,
    annotations: { readOnlyHint: true },
    execute: previewTransformedQuestion,
  })

  // Backward-compatible alias for sessions/prompts created before the canonical name was fixed.
  await modelContext.registerTool({
    name: 'preview_chrome_transformed_question',
    description: 'preview_transformed_question의 이전 호환 이름입니다. 변형 문항을 Workbench Preview에 표시합니다.',
    inputSchema: previewInputSchema,
    annotations: { readOnlyHint: true },
    execute: previewTransformedQuestion,
  })

  await modelContext.registerTool({
    name: 'save_chrome_transformed_question',
    description: '사용자가 승인한 Chrome AI 변형 문항을 원본과 분리해 기존 강사 문제 DB에 저장합니다. 먼저 preview 도구로 확인한 뒤 명시적 승인 후에만 호출합니다.',
    inputSchema: {
      type: 'object', required: ['source_question_id'],
      properties: {
        source_question_id: { type: 'integer' },
        revision_note: { type: 'string' },
        material_ids: { type: 'array', items: { type: 'integer' } },
        insight_ids: { type: 'array', items: { type: 'integer' } },
        digital_twin_id: { type: ['integer', 'null'] },
      },
    },
    annotations: { readOnlyHint: false },
    execute: async input => {
      const context = JSON.parse(sessionStorage.getItem('buildit_chrome_workbench_context') || '{}')
      const source = context.source_question || {}
      const preview=JSON.parse(sessionStorage.getItem('buildit_chrome_preview_question')||'{}')
      if(preview.source_question_id!==input.source_question_id)throw new Error('현재 QID에서 승인된 Preview가 없습니다. 먼저 Preview를 생성해 주세요.')
      const question=preview.question
      if(!isSelfContained(question))throw new Error('완전한 wiki-question-v1 문항만 저장할 수 있습니다.')
      const provenance = { source_question_id: input.source_question_id, digital_twin_id: input.digital_twin_id, material_ids: input.material_ids || [], insight_ids: input.insight_ids || [], revision_note: input.revision_note || '', workbench: 'chrome-question-transform' }
      const response = await api('generated-tests', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
          school: source.school || context.exam_context?.school || '', grade: source.grade || context.exam_context?.grade || '',
          year: context.exam_context?.year, semester: context.exam_context?.semester || '', exam: source.exam || context.exam_context?.exam || '전체',
          source_material: `기존 문제 QID ${input.source_question_id}`, source_page: `question:${input.source_question_id}`,
          questions: [wikiQuestionToLegacy(question)], test_type: 'chrome-transform', title: `Chrome 변형 · QID ${input.source_question_id}`,
          digital_twin: input.digital_twin_id ? `Digital Twin #${input.digital_twin_id}` : '', twin_pattern: JSON.stringify(provenance),
          target_passage: String(input.revision_note || ''), external_id: `chrome_${Date.now()}_${Math.random().toString(16).slice(2)}`, generation_source: 'transform',
        }),
      })
      const result = await readJson(response)
      emit('buildit-chrome-question-saved', result)
      return result
    },
  })
  return true
}
