export type ChromeAiAvailability = 'available' | 'downloadable' | 'downloading' | 'unavailable' | 'error'

export type ChromeAiRuntimeStatus = {
  prompt: ChromeAiAvailability
  koToEn: ChromeAiAvailability
  enToKo: ChromeAiAvailability
}

export type QuestionPreset = 'similar' | 'grammar' | 'vocabulary' | 'blank' | 'order' | 'insertion' | 'title-topic' | 'subjective'

export type ChromeGeneratedQuestion = {
  type: string
  instruction: string
  passage: unknown
  choices: unknown[]
  answer: unknown
  explanation: string
  explanationKo?: string
  sourceQuestionId: number
  generationMode: 'CHROME_BUILT_IN_AI'
}

type AvailabilityOptions = Record<string, unknown>
type CreateOptions = AvailabilityOptions & { monitor?: (monitor: EventTarget) => void }
type LanguageModelSession = { prompt: (input: string) => Promise<string>; destroy?: () => void }
type TranslatorSession = { translate: (input: string) => Promise<string>; destroy?: () => void }
type LanguageModelApi = { availability: (options?: AvailabilityOptions) => Promise<string>; create: (options?: CreateOptions) => Promise<LanguageModelSession> }
type TranslatorApi = { availability: (options: { sourceLanguage: string; targetLanguage: string }) => Promise<string>; create: (options: CreateOptions & { sourceLanguage: string; targetLanguage: string }) => Promise<TranslatorSession> }

export type ChromeBuiltInApis = { LanguageModel?: LanguageModelApi; Translator?: TranslatorApi }
export type GenerateRequest = {
  preset: QuestionPreset
  freeRequest?: string
  schoolPatternReference?: string
  contextPack: Record<string, unknown>
  sourceQuestionId: number
  onDownloadProgress?: (api: 'prompt' | 'koToEn' | 'enToKo', loaded: number) => void
}
export type GenerateResult = { question: ChromeGeneratedQuestion; notice?: string; translatedRequest?: string; translatedPattern?: string }

export const PRESET_INSTRUCTIONS: Record<QuestionPreset, string> = {
  similar: 'Create one similar but meaningfully transformed English exam question.',
  grammar: 'Create one five-choice English grammar question.',
  vocabulary: 'Create one five-choice English vocabulary-in-context question.',
  blank: 'Create one five-choice English blank-completion question.',
  order: 'Create one five-choice paragraph-order question.',
  insertion: 'Create one five-choice sentence-insertion question.',
  'title-topic': 'Create one five-choice English title or main-topic question.',
  subjective: 'Create one constructed-response English question with explicit answer conditions.',
}

const HANGUL = /[\u3131-\u318e\uac00-\ud7a3]/
const status = (value: unknown): ChromeAiAvailability =>
  value === 'available' || value === 'downloadable' || value === 'downloading' || value === 'unavailable' ? value : 'error'

const availability = async (call: (() => Promise<string>) | undefined): Promise<ChromeAiAvailability> => {
  if (!call) return 'unavailable'
  try { return status(await call()) } catch { return 'error' }
}

export const getChromeAiRuntimeStatus = async (apis: ChromeBuiltInApis = globalThis as unknown as ChromeBuiltInApis): Promise<ChromeAiRuntimeStatus> => ({
  prompt: await availability(apis.LanguageModel && (() => apis.LanguageModel!.availability({
    expectedInputs: [{ type: 'text', languages: ['en'] }],
    expectedOutputs: [{ type: 'text', languages: ['en'] }],
  }))),
  koToEn: await availability(apis.Translator && (() => apis.Translator!.availability({ sourceLanguage: 'ko', targetLanguage: 'en' }))),
  enToKo: await availability(apis.Translator && (() => apis.Translator!.availability({ sourceLanguage: 'en', targetLanguage: 'ko' }))),
})

const monitor = (name: 'prompt' | 'koToEn' | 'enToKo', callback?: GenerateRequest['onDownloadProgress']) => callback
  ? (target: EventTarget) => target.addEventListener('downloadprogress', event => callback(name, Number((event as Event & { loaded?: number }).loaded || 0)))
  : undefined

const translate = async (api: TranslatorApi, sourceLanguage: 'ko' | 'en', targetLanguage: 'en' | 'ko', text: string, name: 'koToEn' | 'enToKo', callback?: GenerateRequest['onDownloadProgress']) => {
  const translator = await api.create({ sourceLanguage, targetLanguage, monitor: monitor(name, callback) })
  try { return await translator.translate(text) } finally { translator.destroy?.() }
}

const parseJson = (raw: string): Record<string, unknown> => {
  const cleaned = raw.trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '')
  const start = cleaned.indexOf('{'), end = cleaned.lastIndexOf('}')
  if (start < 0 || end <= start) throw new Error('Chrome AI가 유효한 JSON 문항을 반환하지 않았습니다.')
  return JSON.parse(cleaned.slice(start, end + 1))
}

export const buildEnglishQuestionPrompt = (request: GenerateRequest, freeRequest = '', schoolPatternReference = '') => `
You are generating exactly one English exam question from CURRENT_WIKI_CONTEXT.
${PRESET_INSTRUCTIONS[request.preset]}
${freeRequest ? `Additional user instruction: ${freeRequest}` : ''}

SECURITY AND SOURCE RULES:
- CURRENT_WIKI_CONTEXT is the only content source. Do not import content, facts, passages, or subject matter from anywhere else.
- SCHOOL_PATTERN_REFERENCE is pattern reference only. Analyze only its instruction structure, item mechanics, choice layout, answer requirements, word limits, and response format.
- Never copy or paraphrase the reference passage, topic, names, facts, answer, or choices into the new question.
- Keep the source question unchanged. Return a new self-contained question only.
- Return JSON only, with keys: type, instruction, passage, choices, answer, explanation.
- Write explanation in English. Ensure passage and choices contain everything needed to solve the question.

CURRENT_WIKI_CONTEXT:
${JSON.stringify(request.contextPack)}

SCHOOL_PATTERN_REFERENCE (PATTERN ONLY):
${schoolPatternReference || '(none)'}
`.trim()

export const generateWithChromeBuiltInAi = async (
  request: GenerateRequest,
  apis: ChromeBuiltInApis = globalThis as unknown as ChromeBuiltInApis,
): Promise<GenerateResult> => {
  const runtime = await getChromeAiRuntimeStatus(apis)
  if (!apis.LanguageModel || runtime.prompt === 'unavailable' || runtime.prompt === 'error') throw new Error('이 Chrome 기기에서는 Prompt AI를 사용할 수 없습니다. 기존 Wiki 기능은 그대로 사용할 수 있습니다.')

  const needsRequestTranslation = Boolean(request.freeRequest && HANGUL.test(request.freeRequest))
  const needsPatternTranslation = Boolean(request.schoolPatternReference && HANGUL.test(request.schoolPatternReference))
  if ((needsRequestTranslation || needsPatternTranslation) && (!apis.Translator || runtime.koToEn === 'unavailable' || runtime.koToEn === 'error')) {
    throw new Error('현재 Chrome 기기 내 번역 기능에서 한국어 요청을 영어로 변환할 수 없습니다. 기본 유형 또는 영어 요청은 계속 사용할 수 있습니다.')
  }

  const translatedRequest = needsRequestTranslation ? await translate(apis.Translator!, 'ko', 'en', request.freeRequest!, 'koToEn', request.onDownloadProgress) : (request.freeRequest || '')
  const translatedPattern = needsPatternTranslation ? await translate(apis.Translator!, 'ko', 'en', request.schoolPatternReference!, 'koToEn', request.onDownloadProgress) : (request.schoolPatternReference || '')
  const session = await apis.LanguageModel.create({
    expectedInputs: [{ type: 'text', languages: ['en'] }], expectedOutputs: [{ type: 'text', languages: ['en'] }],
    monitor: monitor('prompt', request.onDownloadProgress),
  })
  let payload: Record<string, unknown>
  try { payload = parseJson(await session.prompt(buildEnglishQuestionPrompt(request, translatedRequest, translatedPattern))) }
  finally { session.destroy?.() }

  const explanation = String(payload.explanation || '')
  let explanationKo: string | undefined
  let notice: string | undefined
  if (explanation && apis.Translator && runtime.enToKo !== 'unavailable' && runtime.enToKo !== 'error') {
    explanationKo = await translate(apis.Translator, 'en', 'ko', explanation, 'enToKo', request.onDownloadProgress)
  } else {
    notice = '현재 Chrome 기기 내 번역 기능에서 한국어 번역을 사용할 수 없어 영문 해설로 표시합니다.'
  }
  return {
    question: {
      type: String(payload.type || request.preset), instruction: String(payload.instruction || ''), passage: payload.passage || '',
      choices: Array.isArray(payload.choices) ? payload.choices : [], answer: payload.answer ?? '', explanation,
      ...(explanationKo ? { explanationKo } : {}), sourceQuestionId: request.sourceQuestionId, generationMode: 'CHROME_BUILT_IN_AI',
    },
    ...(notice ? { notice } : {}), ...(translatedRequest ? { translatedRequest } : {}), ...(translatedPattern ? { translatedPattern } : {}),
  }
}
