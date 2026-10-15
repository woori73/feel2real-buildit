import { describe, expect, it, vi } from 'vitest'
import { buildEnglishQuestionPrompt, generateWithChromeBuiltInAi, getChromeAiRuntimeStatus, PRESET_INSTRUCTIONS } from './chromeBuiltInAi'

const contextPack = { question: { id: 20, passage: 'Only this passage may be used.' }, insight: ['linked only'] }
const request = { preset: 'grammar' as const, contextPack, sourceQuestionId: 20 }

const apis = (options: { prompt?: string; koEn?: string; enKo?: string } = {}) => {
  const translated: Array<[string, string, string]> = []
  const prompts: string[] = []
  const LanguageModel = {
    availability: vi.fn(async () => options.prompt || 'available'),
    create: vi.fn(async () => ({ prompt: async (value: string) => { prompts.push(value); return JSON.stringify({ type: '어법', instruction: 'Choose.', passage: 'Only this passage may be used.', choices: ['A','B','C','D','E'], answer: 2, explanation: 'English reason.' }) } })),
  }
  const Translator = {
    availability: vi.fn(async ({ sourceLanguage }: { sourceLanguage: string }) => sourceLanguage === 'ko' ? (options.koEn || 'available') : (options.enKo || 'available')),
    create: vi.fn(async ({ sourceLanguage, targetLanguage }: { sourceLanguage: string; targetLanguage: string }) => ({
      translate: async (value: string) => { translated.push([sourceLanguage, targetLanguage, value]); return sourceLanguage === 'ko' ? `EN:${value}` : `KO:${value}` },
    })),
  }
  return { runtime: { LanguageModel, Translator }, translated, prompts }
}

describe('Chrome built-in Prompt + Translator pipeline', () => {
  it('checks Prompt, ko→en and en→ko independently with official availability calls', async () => {
    const { runtime } = apis({ prompt: 'downloadable', koEn: 'downloading', enKo: 'unavailable' })
    await expect(getChromeAiRuntimeStatus(runtime)).resolves.toEqual({ prompt: 'downloadable', koToEn: 'downloading', enToKo: 'unavailable' })
    expect(runtime.Translator.availability).toHaveBeenCalledWith({ sourceLanguage: 'ko', targetLanguage: 'en' })
    expect(runtime.Translator.availability).toHaveBeenCalledWith({ sourceLanguage: 'en', targetLanguage: 'ko' })
  })

  it('uses a predefined English instruction for presets without ko→en translation', async () => {
    const { runtime, translated, prompts } = apis({ koEn: 'unavailable' })
    const result = await generateWithChromeBuiltInAi(request, runtime)
    expect(prompts[0]).toContain(PRESET_INSTRUCTIONS.grammar)
    expect(translated.some(([from]) => from === 'ko')).toBe(false)
    expect(result.question.explanationKo).toBe('KO:English reason.')
  })

  it('translates a Korean free request ko→en before Prompt and explanation en→ko afterwards', async () => {
    const { runtime, translated, prompts } = apis()
    const result = await generateWithChromeBuiltInAi({ ...request, freeRequest: '학교 시험처럼 만들어줘' }, runtime)
    expect(translated[0]).toEqual(['ko', 'en', '학교 시험처럼 만들어줘'])
    expect(prompts[0]).toContain('Additional user instruction: EN:학교 시험처럼 만들어줘')
    expect(translated[translated.length - 1]).toEqual(['en', 'ko', 'English reason.'])
    expect(result.question.generationMode).toBe('CHROME_BUILT_IN_AI')
  })

  it('sends an English request directly without unnecessary translation', async () => {
    const { runtime, translated, prompts } = apis()
    await generateWithChromeBuiltInAi({ ...request, freeRequest: 'Make five choices.' }, runtime)
    expect(prompts[0]).toContain('Additional user instruction: Make five choices.')
    expect(translated.filter(([from]) => from === 'ko')).toHaveLength(0)
  })

  it('blocks only Korean custom input when ko→en is unavailable', async () => {
    const { runtime } = apis({ koEn: 'unavailable' })
    await expect(generateWithChromeBuiltInAi({ ...request, freeRequest: '한글 요청' }, runtime)).rejects.toThrow(/기본 유형 또는 영어 요청/)
    await expect(generateWithChromeBuiltInAi(request, runtime)).resolves.toBeTruthy()
  })

  it('falls back to English explanation with an explicit notice when en→ko is unavailable', async () => {
    const { runtime } = apis({ enKo: 'unavailable' })
    const result = await generateWithChromeBuiltInAi(request, runtime)
    expect(result.question.explanation).toBe('English reason.')
    expect(result.question.explanationKo).toBeUndefined()
    expect(result.notice).toMatch(/영문 해설로 표시/)
  })

  it('does not invoke Prompt when Prompt API is unavailable', async () => {
    const { runtime } = apis({ prompt: 'unavailable' })
    await expect(generateWithChromeBuiltInAi(request, runtime)).rejects.toThrow(/기존 Wiki 기능은 그대로/)
    expect(runtime.LanguageModel.create).not.toHaveBeenCalled()
  })

  it('marks school examples as PATTERN ONLY and forbids their content from becoming a source', () => {
    const prompt = buildEnglishQuestionPrompt({ ...request, schoolPatternReference: 'REFERENCE TOPIC' }, '', 'REFERENCE TOPIC')
    expect(prompt).toContain('SCHOOL_PATTERN_REFERENCE (PATTERN ONLY)')
    expect(prompt).toContain('CURRENT_WIKI_CONTEXT is the only content source')
    expect(prompt).toContain('Never copy or paraphrase the reference passage')
  })

  it('contains no external AI or translation endpoint', () => {
    expect(generateWithChromeBuiltInAi.toString()).not.toMatch(/openai|claude|gemini|googleapis|fetch\s*\(/i)
  })
})
