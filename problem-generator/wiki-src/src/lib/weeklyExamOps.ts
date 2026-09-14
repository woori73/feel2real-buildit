/* Weekly exam paper structural operations.
   1차 범위: AI API 없음. 사람이 직접 편집하고, 반복되는 구조 오류는
   deterministic rule + 실제 브라우저 geometry로 검사/교정한다.
   모든 operation은 향후 AI가 같은 op를 호출할 수 있도록 순수 함수로 분리한다. */

export type QuestionRef = {
  /** 영구 식별자. displayNumber와 분리 — 이동해도 불변. */
  questionId: string
  /** 현재 표시 번호(1부터). */
  displayNumber: number
}

export type AnswerMap = Record<string, string> // questionId → 정답 (예: 'q-a1b2' → '③')

export type LinkedQuestionsMap = Record<string, number[]> // passageKey → questionId 목록

/* ===== Stable question identity ===== */

/** 문항 블록에 stable questionId를 부여한다(없는 것만). data-question-id로 기록. */
export const assignStableIds = (doc: Document, existing: string[] = []): QuestionRef[] => {
  const refs: QuestionRef[] = []
  let seq = existing.length
  doc.querySelectorAll('.qblock').forEach(block => {
    let id = block.getAttribute('data-question-id')
    if (!id) {
      do { seq += 1; id = `q-${seq.toString(36).padStart(4, '0')}-${Math.random().toString(36).slice(2, 8)}` }
      while (existing.includes(id))
      block.setAttribute('data-question-id', id)
    }
    const num = parseInt(block.getAttribute('data-qnum') || '0', 10) || refs.length + 1
    refs.push({ questionId: id, displayNumber: num })
  })
  return refs
}

/* ===== 문항 이동 + 재번호화 + 정답/해설 동기화 ===== */

/** 이동 후 표시 순서(doc 순서)대로 1..N 재번호화한다. answerMap은 questionId 기준이라 그대로 유효. */
export const renumberQuestions = (doc: Document): { oldToNew: Record<string, number> } => {
  const oldToNew: Record<string, number> = {}
  doc.querySelectorAll('.qblock').forEach((block, index) => {
    const id = block.getAttribute('data-question-id') || ''
    const oldNum = parseInt(block.getAttribute('data-qnum') || '0', 10)
    oldToNew[id || `#${oldNum}`] = index + 1
    block.setAttribute('data-qnum', String(index + 1))
    const numEl = block.querySelector('.qnum')
    if (numEl) numEl.textContent = `${index + 1}.`
  })
  return { oldToNew }
}

/** displayNumber 기준 매핑(안내용): oldNumber → newNumber */
export const buildNumberMapping = (docBefore: Document, docAfter: Document): Record<number, number> => {
  const idToOld = new Map<string, number>()
  docBefore.querySelectorAll('.qblock').forEach(block => {
    const id = block.getAttribute('data-question-id')
    if (id) idToOld.set(id, parseInt(block.getAttribute('data-qnum') || '0', 10))
  })
  const mapping: Record<number, number> = {}
  docAfter.querySelectorAll('.qblock').forEach(block => {
    const id = block.getAttribute('data-question-id')
    if (id && idToOld.has(id)) mapping[idToOld.get(id)!] = parseInt(block.getAttribute('data-qnum') || '0', 10)
  })
  return mapping
}

/** 문항 클러스터(공통지문 그룹) 이동: anchor 문항을 target 앞/뒤로, 클러스터 전체를 함께 이동. */
export const moveQuestion = (doc: Document, questionId: string, direction: 'up' | 'down'): boolean => {
  const block = doc.querySelector(`.qblock[data-question-id="${questionId}"]`)
  if (!block) return false
  const sibling = direction === 'up' ? block.previousElementSibling : block.nextElementSibling
  if (!sibling || !sibling.classList.contains('qblock')) return false
  if (direction === 'up') block.parentNode!.insertBefore(block, sibling)
  else block.parentNode!.insertBefore(sibling, block)
  return true
}

/** 위/아래 문항이 같은 passage를 공유하면 같이 이동시킨다(공통지문 분리 방지). */
export const moveCluster = (doc: Document, questionId: string, direction: 'up' | 'down'): boolean => {
  const block = doc.querySelector(`.qblock[data-question-id="${questionId}"]`)
  if (!block) return false
  const passageKey = block.getAttribute('data-passage-key')
  const cluster = passageKey
    ? [...doc.querySelectorAll(`.qblock[data-passage-key="${passageKey}"]`)]
    : [block]
  if (cluster.length === 0) return false
  const first = cluster[0]
  const last = cluster[cluster.length - 1]
  if (direction === 'up') {
    let cursor: Element | null = first.previousElementSibling
    while (cursor && cursor.classList.contains('qblock') && passageKey && cursor.getAttribute('data-passage-key') === passageKey) {
      cursor = cursor.previousElementSibling
    }
    if (!cursor || !cursor.classList.contains('qblock')) return false
    first.parentNode!.insertBefore(first, cursor) // cluster 전체를 cursor 앞으로 순서 유지 이동
    let current = first
    for (const node of cluster.slice(1)) {
      current.parentNode!.insertBefore(node, current.nextSibling ? current.nextSibling : null)
      current = node
    }
    return true
  }
  let cursor: Element | null = last.nextElementSibling
  while (cursor && cursor.classList.contains('qblock') && passageKey && cursor.getAttribute('data-passage-key') === passageKey) {
    cursor = cursor.nextElementSibling
  }
  if (!cursor || !cursor.classList.contains('qblock')) return false
  last.parentNode!.insertBefore(cursor, first) // cursor 블록을 cluster 앞으로 → 사실상 cluster를 cursor 뒤로
  // 위 이동으로 cursor가 first 앞에 오므로, cluster가 cursor 뒤에 오도록 재배치
  let anchor: Element | null = cursor
  for (const node of cluster) {
    anchor.parentNode!.insertBefore(node, anchor.nextSibling)
    anchor = node
  }
  return true
}

/* ===== 보기(ViewBox) deterministic 교정 ===== */

/** 연속된 a~f 항목이 보기 박스 밖으로 나간 경우 박스 안으로 복구. */
export const normalizeViewBox = (doc: Document): string[] => {
  const fixed: string[] = []
  doc.querySelectorAll('.qblock').forEach(block => {
    const num = block.getAttribute('data-qnum') || '?'
    const cond = block.querySelector('.cond-box, .view-box, [class*="view"]')
    if (!cond) return
    // cond-box 다음에 이어지는 a./b./c. 항목 노드를 박스 안으로
    let next = cond.nextElementSibling
    const moved: Element[] = []
    while (next) {
      const text = next.textContent || ''
      if (/^\s*[a-f][.、)]/.test(text)) { moved.push(next); next = next.nextElementSibling; continue }
      break
    }
    if (moved.length) {
      const anchor = cond.lastElementChild
      moved.forEach(node => {
        node.querySelectorAll?.('*').forEach(() => undefined)
        cond.appendChild(node.ownerDocument.createTextNode(''))
        cond.appendChild(node)
      })
      if (anchor) anchor.parentNode?.insertBefore(anchor, null)
      fixed.push(`Q${num} 보기 박스 복구`)
    }
  })
  return fixed
}

/** 보기 내부 항목이 한 줄에 붙어 있으면 각 항목을 독립 block으로 분리. */
export const normalizeViewItems = (doc: Document): string[] => {
  const fixed: string[] = []
  doc.querySelectorAll('.cond-box, .view-box, [class*="view"]').forEach(box => {
    if (box.querySelector('.view-item')) return
    const html = box.innerHTML || ''
    if (!/[b-f][.、)][^.]*[.、]/.test(html)) return
    // a. ... b. ... 형태를 <div class="view-item"> 단위로 분리
    const parts: string[] = html
      .split(/(?=(?:^|\s|<br\s*\/?>)?[a-f]\.\s)/)
      .filter((part: string) => /[a-f]\.\s/.test(part) || html.split(/(?=(?:^|\s|<br\s*\/?>)?[a-f]\.\s)/).length === 1)
    if (parts.length <= 1) return
    box.innerHTML = parts.map((part: string) => `<div class="view-item">${part.replace(/^<br\s*\/?>/, '').trim()}</div>`).join('')
    const block = box.closest('.qblock')
    fixed.push(`Q${block?.getAttribute('data-qnum') || '?'} 보기 줄바꿈 복구`)
  })
  return fixed
}

/* ===== 무결성 검사 (question↔answer↔explanation 1:1) ===== */

export type IntegrityIssue =
  | { code: 'MISSING_ANSWER'; questionId: string }
  | { code: 'MISSING_EXPLANATION'; questionId: string }
  | { code: 'DUPLICATE_ANSWER'; questionId: string }
  | { code: 'QUESTION_ANSWER_MISMATCH'; questionId: string; detail: string }

export const validateExamIntegrity = (
  refs: QuestionRef[],
  answers: AnswerMap,
  explanations: AnswerMap,
): IntegrityIssue[] => {
  const issues: IntegrityIssue[] = []
  const seen = new Set<string>()
  refs.forEach(ref => {
    if (!answers[ref.questionId]) issues.push({ code: 'MISSING_ANSWER', questionId: ref.questionId })
    if (!explanations[ref.questionId]) issues.push({ code: 'MISSING_EXPLANATION', questionId: ref.questionId })
    if (seen.has(ref.questionId)) issues.push({ code: 'DUPLICATE_ANSWER', questionId: ref.questionId })
    seen.add(ref.questionId)
  })
  // 정답 중 문항에 없는 것 = mismatch
  Object.keys(answers).forEach(id => {
    if (!seen.has(id)) issues.push({ code: 'QUESTION_ANSWER_MISMATCH', questionId: id, detail: '문항 목록에 없는 정답' })
  })
  return issues
}

/* ===== 공통지문 linked range 자동 생성 ===== */

/** [6,7] → '6~7', [10,11,12,13] → '10~13' */
export const linkedRangeLabel = (numbers: number[]): string => {
  if (!numbers.length) return ''
  const sorted = [...numbers].sort((a, b) => a - b)
  return sorted.length === 1 ? `${sorted[0]}` : `${sorted[0]}~${sorted[sorted.length - 1]}`
}
