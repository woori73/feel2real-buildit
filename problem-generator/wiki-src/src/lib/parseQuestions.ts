// 붙여넣은 문제 텍스트를 강사 문제 DB 저장 계약(question_contract) 형태로 분리한다.
// AI 호출 없이 정규식만 사용한다. 잘못 분리된 문항은 미리보기에서 사용자가 고치거나 제외한다.

export type ParsedQuestion = {
  type: 'mc' | 'sa'
  questionType: string
  sourceText: string
  stem: string
  options: string[]
  answer: number[]
  multi: boolean
  score: number
  conditions: string[]
  answerFormat: string
  acceptedAnswers: string[]
  sampleAnswer: string
  gradingRule: string
  // 미리보기 전용(저장 시 제거)
  _answerText?: string
  _warnings?: string[]
}

const CIRCLED = '①②③④⑤⑥⑦⑧⑨⑩'
const BLOCK_START = /^\s*(?:Q|문제|문항|No\.?)?\s*\d+\s*[.)．]/i
const OPTION_LINE = /^\s*(?:([①-⑩])|([A-Ea-e])[.)]|(\d{1,2})[.)])\s*(.*\S)?\s*$/
const LABELLED = /^\s*(정답|답|해설|풀이|유형|문제유형|조건|배점|점수)\s*[:：]\s*(.*)$/

const markersToIndices = (raw: string, optionCount: number): number[] => {
  const out = new Set<number>()
  for (const ch of raw) {
    const c = CIRCLED.indexOf(ch)
    if (c >= 0) out.add(c + 1)
  }
  for (const m of raw.matchAll(/\b(\d{1,2})\b/g)) {
    const n = Number(m[1])
    if (n >= 1 && n <= Math.max(optionCount, 5)) out.add(n)
  }
  for (const m of raw.matchAll(/\b([A-Ea-e])\b/g)) {
    const n = m[1].toUpperCase().charCodeAt(0) - 64
    if (n >= 1 && n <= optionCount) out.add(n)
  }
  return [...out].sort((a, b) => a - b)
}

const splitBlocks = (text: string): string[] => {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: string[] = []
  let current: string[] = []
  for (const line of lines) {
    if (BLOCK_START.test(line) && current.some(l => l.trim())) {
      blocks.push(current.join('\n'))
      current = []
    }
    current.push(line)
  }
  if (current.some(l => l.trim())) blocks.push(current.join('\n'))
  // 번호 마커가 전혀 없으면 빈 줄 2개로 분리 시도
  if (blocks.length <= 1) {
    return text.split(/\n\s*\n\s*\n?/).map(b => b.trim()).filter(Boolean)
  }
  return blocks
}

const parseBlock = (block: string): ParsedQuestion | null => {
  const lines = block.split('\n')
  const stemLines: string[] = []
  const options: string[] = []
  let answerText = ''
  let explanation = ''
  let questionType = ''
  const conditions: string[] = []
  let score = 0
  let sawOptionsOrAnswer = false

  for (let raw of lines) {
    let line = raw.replace(BLOCK_START, '').trim()
    if (!line && !raw.trim()) continue
    const labelled = raw.match(LABELLED)
    if (labelled) {
      sawOptionsOrAnswer = true
      const [, key, val] = labelled
      if (key === '정답' || key === '답') answerText = val.trim()
      else if (key === '해설' || key === '풀이') explanation = val.trim()
      else if (key === '유형' || key === '문제유형') questionType = val.trim()
      else if (key === '조건') conditions.push(val.trim())
      else if (key === '배점' || key === '점수') score = Number(val.replace(/[^\d]/g, '')) || 0
      continue
    }
    const circled = raw.match(/[①-⑩]/g)
    if (circled && circled.length >= 2 && !LABELLED.test(raw)) {
      // 한 줄에 여러 선택지: "① is  ② are  ③ was"
      sawOptionsOrAnswer = true
      raw.split(/(?=[①-⑩])/).forEach(part => {
        const t = part.replace(/^\s*[①-⑩]\s*/, '').trim()
        if (t) options.push(t)
      })
      continue
    }
    const opt = raw.match(OPTION_LINE)
    if (opt && (opt[1] || opt[2] || opt[3]) && !LABELLED.test(raw)) {
      sawOptionsOrAnswer = true
      options.push((opt[4] || '').trim())
      continue
    }
    if (!sawOptionsOrAnswer) stemLines.push(line || raw.trim())
    else if (line && !explanation) explanation += (explanation ? ' ' : '') + line
  }

  const stem = stemLines.join(' ').replace(/\s+/g, ' ').trim()
  if (!stem && options.length === 0) return null

  const warnings: string[] = []
  const isMc = options.length >= 2
  let answer: number[] = []
  if (isMc) {
    answer = markersToIndices(answerText, options.length).filter(n => n <= options.length)
    if (answer.length === 0) warnings.push('정답 번호를 인식하지 못했습니다')
  } else if (!answerText) {
    warnings.push('정답을 인식하지 못했습니다')
  }

  return {
    type: isMc ? 'mc' : 'sa',
    questionType: questionType || (isMc ? '객관식' : '서답형'),
    sourceText: '',
    stem,
    options,
    answer,
    multi: answer.length > 1,
    score: score || (isMc ? 4 : 5),
    conditions,
    answerFormat: isMc ? '' : (conditions.length ? '조건을 만족하는 완전한 문장' : ''),
    acceptedAnswers: isMc ? [] : (answerText ? [answerText] : []),
    sampleAnswer: isMc ? '' : answerText,
    gradingRule: explanation || (isMc ? '' : '모범답안과 의미·문법이 일치해야 함'),
    _answerText: answerText,
    _warnings: warnings,
  }
}

export const parsePastedQuestions = (text: string): ParsedQuestion[] =>
  splitBlocks(text).map(parseBlock).filter((q): q is ParsedQuestion => q !== null)

// 저장 계약에 맞게 미리보기 전용 필드를 제거한다.
export const toContractQuestion = (q: ParsedQuestion) => {
  const { _answerText, _warnings, ...rest } = q
  void _answerText; void _warnings
  return rest
}
