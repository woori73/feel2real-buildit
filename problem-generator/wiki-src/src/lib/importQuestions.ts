// 업로드한 문제 파일(.json / .html / .txt)을 강사 문제 DB 저장 계약으로 변환한다.
// 형식별 importer를 분리한다: JSON=구조화 데이터 직접, HTML=DOM 파서, TXT=parseQuestions 정규식.
// AI 호출 없음. HTML은 평문화하지 않고 DOM 구조/semantic을 그대로 사용한다.
import { ParsedQuestion, markersToIndices, parsePastedQuestions } from './parseQuestions'
import { isSelfContained, needsPassage, rich, sanitizeQuestionHtml, textOfHtml, WikiQuestionV1 } from './wikiQuestion'

export type ImportMeta = {
  title?: string; sourceMaterial?: string; sourcePage?: string; digitalTwin?: string
  twinPattern?: string; difficulty?: string; targetVocabulary?: string[]
}
export type ImportResult = {
  format: 'json' | 'html' | 'txt'
  questions: ParsedQuestion[]
  meta: ImportMeta
  // 파싱 신뢰도가 낮으면(구조 인식 실패) 자동 저장을 막는다.
  lowConfidence: boolean
  notes: string[]          // 예: "정답이 없는 문항 30개" — 저장 자체는 허용
}

const s = (v: unknown) => (v == null ? '' : String(v)).trim()
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : v == null || v === '' ? [] : [v])
const pick = (o: Record<string, unknown>, ...keys: string[]) => {
  for (const k of keys) if (o[k] != null && o[k] !== '') return o[k]
  return undefined
}

const emptyQuestion = (): ParsedQuestion => ({
  type: 'sa', questionType: '', sourceText: '', stem: '', options: [], answer: [], multi: false,
  score: 0, conditions: [], answerFormat: '', acceptedAnswers: [], sampleAnswer: '', gradingRule: '',
  _warnings: [],
})

const canonicalize=(q:ParsedQuestion,parts?:Partial<{instructionHtml:string;passageHtml:string;choiceHtml:string[];conditionHtml:string[];explanationHtml:string;sourceQuestionNumber:string;commonPassageId:string}>):ParsedQuestion=>{
  const passageText=q.sourceText.trim(),required=needsPassage(q.stem,q.questionType)
  const canonical:WikiQuestionV1={schema_version:'wiki-question-v1',kind:q.type,question_type:q.questionType,
    instruction:rich(parts?.instructionHtml||'',q.stem),passage:{...rich(parts?.passageHtml||'',passageText),status:passageText?'complete':required?'missing':'not_applicable'},
    choices:q.options.map((text,i)=>({...rich(parts?.choiceHtml?.[i]||'',text),number:i+1})),conditions:q.conditions.map((text,i)=>rich(parts?.conditionHtml?.[i]||'',text)),
    answer:{choice_numbers:q.answer,accepted_answers:q.acceptedAnswers,sample_answer:q.sampleAnswer},explanation:rich(parts?.explanationHtml||'',q.gradingRule),
    scoring:{score:q.score,multi:q.multi,answer_format:q.answerFormat,grading_rule:q.gradingRule},source:{source_question_number:parts?.sourceQuestionNumber,common_passage_id:parts?.commonPassageId}}
  q.canonical=canonical;q.sourceQuestionNumber=parts?.sourceQuestionNumber
  if(!isSelfContained(canonical))(q._warnings??=[]).push(canonical.passage.status==='missing'?'지문을 찾지 못해 REVIEW 필요':'문항 표시 확인 필요')
  return q
}

// ── 공통: 파싱된 문항에 검증 플래그를 매긴다 ──────────────────────────────
const validate = (q: ParsedQuestion): ParsedQuestion => {
  const w = q._warnings ?? (q._warnings = [])
  const stemLen = q.stem.replace(/[_\s→·]/g, '').length
  const structural = stemLen < 2 || (q.type === 'mc' && q.options.length < 2)
  if (stemLen < 2) w.push('문제 본문 확인 필요')
  if (q.type === 'mc' && q.options.length < 2) w.push('선택지 확인 필요')
  q._structuralIssue = Boolean(q._structuralIssue) || structural
  if (!q._answerText && q.answer.length === 0 && !q.sampleAnswer) {
    q._missingAnswer = true
    if (!w.includes('정답 미등록')) w.push('정답 미등록')
  }
  return q
}

const finalize = (format: ImportResult['format'], questions: ParsedQuestion[], meta: ImportMeta): ImportResult => {
  const qs = questions.map(validate)
  const structuralFails = qs.filter(q => q._structuralIssue).length
  const missing = qs.filter(q => q._missingAnswer).length
  const lowConfidence = qs.length === 0 || structuralFails / qs.length > 0.4
  const notes: string[] = []
  if (missing > 0) notes.push(`정답이 없는 문항 ${missing}개 — 저장은 가능하지만 정답/해설은 비어 있습니다.`)
  if (lowConfidence)
    notes.push(qs.length === 0
      ? '이 HTML에서 문항 구조를 찾지 못했습니다. BuildIt/Claude가 만든 시험지 형식인지 확인해 주세요.'
      : `${qs.length}개 문항 영역을 발견했지만 문항 구조를 정확히 인식하지 못했습니다.`)
  return { format, questions: qs, meta, lowConfidence, notes }
}

// ── JSON ─────────────────────────────────────────────────────────────────
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

  if (isMc) {
    if (Array.isArray(ansRaw)) q.answer = ansRaw.map(Number).filter(n => n >= 1 && n <= options.length)
    else q.answer = markersToIndices(s(ansRaw), options.length)
    q.multi = q.answer.length > 1
    q.gradingRule = explanation
    q._answerText = s(ansRaw)
  } else {
    const sample = s(pick(raw, 'sampleAnswer', 'sample_answer', 'modelAnswer')) || s(ansRaw)
    q.sampleAnswer = sample
    q.acceptedAnswers = arr(pick(raw, 'acceptedAnswers', 'accepted_answers')).map(s).filter(Boolean)
    if (!q.acceptedAnswers.length && sample) q.acceptedAnswers = [sample]
    q.gradingRule = explanation
    q._answerText = sample
  }
  const canonicalRaw=raw.canonical_json||raw.canonical
  if(canonicalRaw&&typeof canonicalRaw==='object')q.canonical=canonicalRaw as WikiQuestionV1
  return q.canonical?q:canonicalize(q,{passageHtml:s(pick(raw,'passage_html','passageHtml')),instructionHtml:s(pick(raw,'instruction_html','instructionHtml'))})
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
  return finalize('json', questions, jsonMeta(root))
}

// ── HTML (DOM 기반) ──────────────────────────────────────────────────────
const ROMAN_RE = /^\s*(?:\[[^\]]*\]\s*)?[Ⅰ-Ⅹⅰ-ⅹIVXLCDMivx]+\s*[.．,]\s*/
const SCORE_RE = /각?\s*(\d+)\s*점/
const MC_HINT_RE = /고르시오|고르시요|고르세요|알맞은 것을|적절한 것을|틀린 것을|어색한 것을/
const CIRCLED = '①②③④⑤⑥⑦⑧⑨⑩'

type Sec = { instruction: string; note: string; score: number; mc: boolean; not: boolean; roman: string }

const cleanText = (el: Element): { text: string; blanks: number } => {
  const c = el.cloneNode(true) as Element
  c.querySelectorAll('b.qn, .qn, .choices, .choice, .cond, .essay-box, .hint, .answer, [data-answer]').forEach(n => n.remove())
  let blanks = 0
  c.querySelectorAll('.blank, .answer-line').forEach(n => { blanks++; n.replaceWith(' ____ ') })
  const text = (c.textContent || '')
    .replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').replace(/\s+([.,?!)])/g, '$1').trim()
  return { text, blanks }
}

const splitChoices = (raw: string): string[] =>
  raw.replace(/\u00a0/g, ' ').split(/\s*(?=[①-⑩])|\n/)
    .map(p => p.replace(/^[\s①-⑩]+/, '').replace(/^[A-Ea-e][.)]\s*/, '').replace(/<br\s*\/?>/gi, '').trim())
    .filter(Boolean)

const choicesOf = (el: Element): string[] => {
  const box = el.querySelector('.choices, .choice')
  if (box) {
    // <br> 로 나뉜 문장형 선택지 지원
    const withNl = (box.innerHTML || '').replace(/<br\s*\/?>/gi, '\n')
    const tmp = el.ownerDocument.createElement('div'); tmp.innerHTML = withNl
    return splitChoices((tmp.textContent || '').replace(/\u00a0/g, ' '))
  }
  const lis = [...el.querySelectorAll('ol > li, ul > li')]
  if (lis.length >= 2) return lis.map(li => (li.textContent || '').trim()).filter(Boolean)
  return []
}

const wordBankOf = (text: string): string[] => {
  const m = text.match(/(?:<보기>|\[보기\]|보기\s*[:：>])\s*(.+)/)
  if (!m) return []
  return m[1].split(/[/·,]|\s{2,}/).map(w => w.trim()).filter(w => w && !/^[①-⑩]/.test(w)).slice(0, 12)
}

const explanationOf = (el: Element): string => {
  const node = el.querySelector('.explanation, .commentary, [data-explanation]')
  if (node) return (node.textContent || '').trim()
  const m = (el.textContent || '').match(/(?:해설|풀이)\s*[:：]\s*(.+)/)
  return m ? m[1].trim() : ''
}

const answerOf = (el: Element, options: string[]): { answer: number[]; sample: string } => {
  const explicit = el.querySelector('.answer:not(.answer-line), [data-answer]')
  let raw = explicit ? (explicit.getAttribute('data-answer') || explicit.textContent || '').trim() : ''
  if (!raw) {
    const m = (el.textContent || '').match(/정답\s*[:：]\s*([^\n(]+)/)
    if (m) raw = m[1].trim()
  }
  if (!raw) {
    // 채워진 blank/answer-line 안의 텍스트
    const filled = [...el.querySelectorAll('.blank, .answer-line')].map(n => (n.textContent || '').trim()).filter(Boolean)
    if (filled.length) raw = filled.join(' ')
  }
  if (!raw) return { answer: [], sample: '' }
  const idx = options.length ? markersToIndices(raw, options.length) : []
  return { answer: idx, sample: idx.length ? '' : raw }
}

const questionTypeLabel = (sec: Sec, isMc: boolean, hasEssay: boolean): string => {
  if (hasEssay || /영작|문장을 쓰/.test(sec.instruction)) return '조건 영작'
  if (sec.not) return '어색한 어휘 찾기 (NOT형)'
  if (isMc) return '빈칸 어휘 선택 (객관식)'
  if (/우리말 뜻/.test(sec.instruction)) return '단어 뜻 쓰기'
  if (/영어 단어를 쓰|영단어/.test(sec.instruction)) return '영단어 쓰기'
  if (/첫 ?글자/.test(sec.instruction) || /첫 ?글자/.test(sec.note)) return '문장 빈칸 (첫글자 힌트)'
  return '문장 빈칸 서답형'
}

const buildHtmlQuestion = (el: Element, sec: Sec): ParsedQuestion => {
  const q = emptyQuestion()
  const options = choicesOf(el)
  const isMc = options.length >= 2 || (sec.mc && options.length >= 2)
  const hasEssay = !!el.querySelector('.essay-box')
  const { text, blanks } = cleanText(el)
  const passageEl=el.querySelector('.passage,[data-passage],.reading,blockquote')

  let stem = text
  let sourceText = ''
  const arrowMatch = text.match(/^(.+?)\s*→\s*_*\s*$/)
  if (arrowMatch) {          // "emission → ____"  또는 "축적하다, 쌓이다 → ____"
    stem = arrowMatch[1].trim()
    sourceText = sec.instruction
  } else if (!stem && sec.instruction) {   // Ⅵ 처럼 발문이 선택지 위 헤더에만 있는 경우
    stem = sec.instruction
  } else if (sec.instruction && stem && sec.instruction !== stem) {
    sourceText = sec.instruction
  }

  q.type = (isMc || (sec.mc && options.length >= 2)) ? 'mc' : 'sa'
  q.stem = stem
  q.sourceText = passageEl?(passageEl.textContent||'').trim():(sourceText === stem ? '' : sourceText)
  q.options = options
  q.score = sec.score || (q.type === 'mc' ? 4 : hasEssay ? 5 : 2)
  q.questionType = questionTypeLabel(sec, q.type === 'mc', hasEssay)

  const cond = el.querySelector('.cond')
  if (cond) q.conditions = (cond.textContent || '').replace(/^\s*조건\s*[:：]\s*/, '')
    .split(/\s*[①-⑩]\s*|(?: ){2,}|;\s*/).map(x => x.trim()).filter(Boolean)
  if (hasEssay) q.answerFormat = '주어-동사를 갖춘 완전한 영어 문장'

  const { answer, sample } = answerOf(el, options)
  const exp = explanationOf(el)
  if (q.type === 'mc') { q.answer = answer; q.multi = answer.length > 1; q.gradingRule = exp; q._answerText = answer.length ? answer.join(',') : sample }
  else { q.sampleAnswer = sample; q.acceptedAnswers = sample ? [sample] : []; q.gradingRule = exp; q._answerText = sample }
  const number=(el.querySelector(':scope > .qn,:scope > b.qn')?.textContent||el.getAttribute('data-question-id')||'').match(/\d+/)?.[0]||''
  const choiceEls=[...el.querySelectorAll('.choices .choice,.choices li,ol.choices>li,ul.choices>li')]
  return canonicalize(q,{instructionHtml:passageEl?'':sanitizeQuestionHtml(el.querySelector('.question-text,.stem,[data-question-text]')?.innerHTML||''),passageHtml:passageEl?.innerHTML||'',choiceHtml:choiceEls.map(n=>n.innerHTML),explanationHtml:el.querySelector('.explanation,.commentary,[data-explanation]')?.innerHTML||'',sourceQuestionNumber:number})
}

const fromDataNode = (el: Element): ParsedQuestion => {
  const q = emptyQuestion()
  const options = choicesOf(el).length ? choicesOf(el)
    : (el.getAttribute('data-choices') || '').split('|').map(x => x.trim()).filter(Boolean)
  const declared = (el.getAttribute('data-question-type') || '').toLowerCase()
  q.type = declared === 'mc' || (declared !== 'sa' && options.length >= 2) ? 'mc' : 'sa'
  q.options = options
  q.score = Number(el.getAttribute('data-score')) || (q.type === 'mc' ? 4 : 5)
  q.questionType = el.getAttribute('data-question-type') || (q.type === 'mc' ? '객관식' : '서답형')
  const stemEl = el.querySelector('.question-text, .stem, [data-question-text]') || el
  q.stem = cleanText(stemEl).text
  const dataAns = el.getAttribute('data-answer') || ''
  if (q.type === 'mc') { q.answer = markersToIndices(dataAns, options.length); q.multi = q.answer.length > 1; q._answerText = dataAns }
  else { q.sampleAnswer = dataAns; q.acceptedAnswers = dataAns ? [dataAns] : []; q._answerText = dataAns }
  const tw = el.getAttribute('data-target-word')
  if (tw && !q.stem.includes(tw)) q.sourceText = `대상 표현: ${tw}`
  q.gradingRule = el.querySelector('.explanation, [data-explanation]')?.textContent?.trim() || ''
  const conditionEls=[...el.querySelectorAll('.cond,[data-condition]')]
  q.conditions=conditionEls.map(n=>(n.textContent||'').replace(/^\s*조건\s*[:：]?\s*/,'').trim()).filter(Boolean)
  const passageEl=el.querySelector('.passage,[data-passage],.reading,blockquote')
  if(passageEl)q.sourceText=(passageEl.textContent||'').trim()
  const number=(el.getAttribute('data-question-id')||el.querySelector('.qn')?.textContent||'').match(/\d+/)?.[0]||''
  const choiceEls=[...el.querySelectorAll('.choices .choice,.choices li,ol.choices>li,ul.choices>li')]
  return canonicalize(q,{instructionHtml:stemEl===el?'':stemEl.innerHTML,passageHtml:passageEl?.innerHTML||'',choiceHtml:choiceEls.map(n=>n.innerHTML),conditionHtml:conditionEls.map(n=>n.innerHTML),explanationHtml:el.querySelector('.explanation,[data-explanation]')?.innerHTML||'',sourceQuestionNumber:number})
}

const htmlMeta = (doc: Document): ImportMeta => {
  const title = doc.querySelector('title, h1')?.textContent?.trim()
  const subtitle = doc.querySelector('.subtitle, .sub')?.textContent?.replace(/\u00a0/g, ' ') || ''
  const theme = (doc.querySelector('.theme-cell span, [data-source-material]')?.textContent
    || doc.querySelector('[data-source-material]')?.getAttribute('data-source-material') || '').replace(/\u00a0/g, ' ').trim()
  const dataMat = doc.querySelector('[data-source-material]')?.getAttribute('data-source-material')?.trim()
  const material = dataMat
    || theme.replace(/\s*—?\s*Page\s*\d+.*$/i, '').replace(/vocabulary\s*test/i, '').replace(/[-–—]\s*$/, '').trim()
    || subtitle.match(/(?:material|자료)\s*[:：]\s*([^·|]+)/i)?.[1]?.trim()
  const page = (theme.match(/Page\s*(\d+)/i) || subtitle.match(/Page\s*(\d+)/i)
    || (doc.querySelector('[data-source-page]')?.getAttribute('data-source-page') || '').match(/(\d+)/))?.[1]
  return {
    title: title || undefined,
    sourceMaterial: material || undefined,
    sourcePage: page || undefined,
  }
}

const importHtml = (text: string): ImportResult => {
  const doc = new DOMParser().parseFromString(text, 'text/html')
  doc.querySelectorAll('script, style, noscript').forEach(n => n.remove())
  const meta = htmlMeta(doc)
  doc.querySelectorAll('table.head, .head, .page-no, .subtitle, thead').forEach(n => n.remove())

  // 1) data-* machine-readable markup 우선
  const dataNodes = [...doc.querySelectorAll('[data-question-id], [data-question]')]
  if (dataNodes.length >= 1) {
    const common=new Map<string,Element>();doc.querySelectorAll('[data-common-passage-id]').forEach(n=>common.set(n.getAttribute('data-common-passage-id')||'',n))
    const rows=dataNodes.map(el=>{const q=fromDataNode(el),ref=el.getAttribute('data-passage-ref')||'';if(ref&&common.has(ref)&&q.canonical){const node=common.get(ref)!;q.sourceText=(node.textContent||'').trim();q.canonical.passage={...rich(node.innerHTML),status:'complete'};q.canonical.source.common_passage_id=ref}return q})
    return finalize('html',rows,meta)
  }

  // 2) 반복 문항 container: b.qn 을 가진 .q / .question / .question-item
  const questions: ParsedQuestion[] = []
  let sec: Sec = { instruction: '', note: '', score: 0, mc: false, not: false, roman: '' }
  let secHadNumbered = false
  let pending: Element[] = []
  let activeCommon:Element|null=null

  const flushChallenge = () => {
    if (!pending.length || secHadNumbered) { pending = []; return }
    const parts = pending.map(p => cleanText(p).text).filter(Boolean)
    const joined = parts.join('\n')
    const options = wordBankOf(joined)
    const q = emptyQuestion()
    q.type = options.length >= 2 ? 'mc' : 'sa'
    q.options = options
    q.multi = /정답\s*2|공통으로/.test(sec.instruction + joined)
    q.stem = parts.filter(p => !/^(?:<보기>|\[보기\]|보기\s*[:：>])/.test(p)).join(' ').trim() || sec.instruction
    q.sourceText = sec.instruction && sec.instruction !== q.stem ? sec.instruction : ''
    q.score = sec.score || 5
    q.questionType = '도전 문제 (공통 어휘)'
    q.answerFormat = q.type === 'mc' ? '①·② 각각 <보기>에서 선택' : ''
    questions.push(q)
    pending = []
  }

  const walk = [...doc.body.querySelectorAll('h2.sec, .sec, .sec-note, .common-passage, [data-common-passage], .passage-range, .q, .question, .question-item')]
  for (const el of walk) {
    if(el.matches('.common-passage,[data-common-passage],.passage-range')){activeCommon=el;continue}
    if (el.matches('.sec-note')) { sec.note = (el.textContent || '').trim(); continue }
    if (el.matches('h2.sec, .sec')) {
      flushChallenge()
      activeCommon=null
      const raw = (el.textContent || '').replace(/\u00a0/g, ' ').trim()
      sec = {
        roman: (raw.match(ROMAN_RE)?.[0] || '').trim(),
        instruction: raw.replace(ROMAN_RE, '').replace(/\s*\(각?\s*\d+\s*점\)\s*$/, '').replace(/^\[[^\]]*\]\s*/, '').trim(),
        note: '', score: Number(raw.match(SCORE_RE)?.[1]) || 0,
        mc: MC_HINT_RE.test(raw), not: /어색한 것을|틀린 것을/.test(raw),
      }
      secHadNumbered = false
      continue
    }
    const hasQn = !!(el.querySelector(':scope > b.qn') || el.querySelector(':scope > .qn'))
    if (hasQn || el.matches('.question, .question-item')) {
      flushChallenge()
      secHadNumbered = true
      const q=buildHtmlQuestion(el, sec)
      if(q.canonical&&activeCommon){const id=(activeCommon.getAttribute('data-common-passage')||activeCommon.previousElementSibling?.textContent||'common').trim();q.sourceText=(activeCommon.textContent||'').trim();q.canonical.passage={...rich(activeCommon.innerHTML),status:'complete'};q.canonical.source.common_passage_id=id}
      questions.push(q)
    } else {
      pending.push(el) // 번호 없는 .q/p.q → 도전문제 후보로 모은다
    }
  }
  flushChallenge()

  return finalize('html', questions, meta)
}

// ── TXT ──────────────────────────────────────────────────────────────────
const importTxt = (text: string): ImportResult =>
  finalize('txt', parsePastedQuestions(text).map(q=>canonicalize(q)), {})

// ── 진입점 ───────────────────────────────────────────────────────────────
export const importQuestionFile = (filename: string, text: string): ImportResult => {
  const ext = filename.split('.').pop()?.toLowerCase()
  const head = text.trimStart().slice(0, 400)
  if (ext === 'json' || (!ext && (head.startsWith('{') || head.startsWith('[')))) {
    try { return importJson(text) } catch { /* JSON 아니면 아래로 */ }
  }
  if (ext === 'html' || ext === 'htm' || /<html|<body|<section|<div|<table/i.test(head)) {
    return importHtml(text)
  }
  return importTxt(text)
}
