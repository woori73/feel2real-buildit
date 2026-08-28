// 업로드한 문제 파일(.json / .html / .txt)을 강사 문제 DB 저장 계약으로 변환한다.
// AI 호출 없음. JSON은 구조화 데이터를 직접 사용하고, HTML/TXT는 parseQuestions.ts를 재사용한다.
import { ParsedQuestion, htmlToText, markersToIndices, parsePastedQuestions } from './parseQuestions'

export type ImportMeta = {
  title?: string; sourceMaterial?: string; sourcePage?: string; digitalTwin?: string
  twinPattern?: string; difficulty?: string; targetVocabulary?: string[]
}
export type ImportResult = { format: 'json' | 'html' | 'txt'; questions: ParsedQuestion[]; meta: ImportMeta }

const s = (v: unknown) => (v == null ? '' : String(v)).trim()
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : v == null || v === '' ? [] : [v])
const pick = (o: Record<string, unknown>, ...keys: string[]) => {
  for (const k of keys) if (o[k] != null && o[k] !== '') return o[k]
  return undefined
}

const emptyQuestion = (): ParsedQuestion => ({
  type: 'sa', questionType: '', sourceText: '', stem: '', options: [], answer: [], multi: false,
  score: 0, conditions: [], answerFormat: '', acceptedAnswers: [], sampleAnswer: '', gradingRule: '',
})

const fromJsonQuestion = (raw: Record<string, unknown>): ParsedQuestion => {
  const q = emptyQuestion()
  const options = arr(pick(raw, 'options', 'choices', '보기', '선택지')).map(s).filter(Boolean)
  const declaredType = s(pick(raw, 'type', 'q_kind', 'questionKind')).toLowerCase()
  const isMc = declaredType === 'mc' || (declaredType !== 'sa' && options.length >= 2)
  const ansRaw = pick(raw, 'answer', 'correct', 'correctAnswer', '정답', 'answers')
  const explanation = s(pick(raw, 'gradingRule', 'explanation', 'solution', '해설', '풀이', 'rationale'))

  q.type = isMc ? 'mc' : 'sa'
  q.questionType = s(pick(raw, 'questionType', 'question_type', 'type_name', '유형', 'category')) || (isMc ? '객관식' : '서답형')
  q.sourceText = s(pick(raw, 'sourceText', 'source_text', 'passage', 'context', '지문'))
  q.stem = s(pick(raw, 'stem', 'question', 'prompt', 'text', '문제', '발문'))
  q.options = options
  q.score = Number(s(pick(raw, 'score', 'points', '배점', 'point'))) || (isMc ? 4 : 5)
  q.conditions = arr(pick(raw, 'conditions', '조건')).map(s).filter(Boolean)
  q.answerFormat = s(pick(raw, 'answerFormat', 'answer_format', '답안형태'))
  q._warnings = []

  if (isMc) {
    if (Array.isArray(ansRaw)) q.answer = ansRaw.map(Number).filter(n => n >= 1 && n <= options.length)
    else q.answer = markersToIndices(s(ansRaw), options.length)
    q.multi = q.answer.length > 1
    q.gradingRule = explanation
    q._answerText = s(ansRaw)
    if (!q.answer.length) q._warnings!.push('정답 번호 확인 필요')
  } else {
    const sample = s(pick(raw, 'sampleAnswer', 'sample_answer', 'modelAnswer')) || s(ansRaw)
    q.sampleAnswer = sample
    q.acceptedAnswers = arr(pick(raw, 'acceptedAnswers', 'accepted_answers')).map(s).filter(Boolean)
    if (!q.acceptedAnswers.length && sample) q.acceptedAnswers = [sample]
    q.gradingRule = explanation || '모범답안과 의미·문법이 일치해야 함'
    q._answerText = sample
    if (!sample) q._warnings!.push('정답 확인 필요')
  }
  if (!q.stem) q._warnings!.push('문제 지문 확인 필요')
  return q
}

const jsonMeta = (root: Record<string, unknown>): ImportMeta => {
  const ctx = (root.context && typeof root.context === 'object' ? root.context : {}) as Record<string, unknown>
  return {
    title: s(pick(root, 'title', 'examTitle', 'exam_title')) || undefined,
    sourceMaterial: s(pick(root, 'source_material', 'sourceMaterial', 'material') || pick(ctx, 'material', 'source_material')) || undefined,
    sourcePage: s(pick(root, 'source_page', 'sourcePage', 'page') || pick(ctx, 'source_page', 'page')) || undefined,
    digitalTwin: s(pick(root, 'digital_twin', 'digitalTwin', 'twin_label')) || undefined,
    twinPattern: s(pick(root, 'twin_pattern', 'twinPattern')) || undefined,
    difficulty: s(pick(root, 'difficulty')) || undefined,
    targetVocabulary: arr(pick(root, 'target_vocabulary', 'targetVocabulary')).map(s).filter(Boolean),
  }
}

const importJson = (text: string): ImportResult => {
  const data = JSON.parse(text)
  const root: Record<string, unknown> = Array.isArray(data) ? { questions: data } : data
  let list = arr(pick(root, 'questions', 'items', 'data', 'problems'))
  if (!list.length && Array.isArray(data)) list = data
  const questions = list
    .filter((x): x is Record<string, unknown> => !!x && typeof x === 'object')
    .map(fromJsonQuestion)
  return { format: 'json', questions, meta: jsonMeta(root) }
}

export const importQuestionFile = (filename: string, text: string): ImportResult => {
  const ext = filename.split('.').pop()?.toLowerCase()
  const trimmed = text.trimStart()
  if (ext === 'json' || trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try { return importJson(text) } catch { /* JSON 아니면 아래 fallback */ }
  }
  if (ext === 'html' || ext === 'htm' || /<html|<body|<div|<p[ >]/i.test(trimmed.slice(0, 500))) {
    return { format: 'html', questions: parsePastedQuestions(htmlToText(text)), meta: {} }
  }
  return { format: 'txt', questions: parsePastedQuestions(text), meta: {} }
}
