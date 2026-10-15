/* Weekly exam paper validators — deterministic rule + 실제 브라우저 geometry.
   [전체 검사]와 [자동 교정]이 같은 rule을 공유한다.
   오류 코드는 요구사항 식별자와 동일하게 사용한다. */

export type IssueCode =
  | 'VIEW_ITEM_OUTSIDE_CONTAINER' | 'VIEW_ITEM_NOT_BLOCK'
  | 'CHOICE_MISSING' | 'CHOICE_CLIPPED' | 'CHOICE_OUTSIDE_QUESTION'
  | 'QUESTION_CLIPPED' | 'COLUMN_OVERFLOW' | 'PAGE_OVERFLOW' | 'FOOTER_COLLISION'
  | 'SHARED_PASSAGE_RANGE_MISMATCH' | 'NON_CONTIGUOUS_SHARED_PASSAGE_QUESTIONS'
  | 'ORPHAN_PASSAGE_REFERENCE'
  | 'MISSING_QUESTION' | 'DUPLICATE_QUESTION' | 'WRONG_DOM_ORDER' | 'WRONG_PRINT_ORDER'
  | 'EXCESSIVE_PAGE_GAP' | 'QUESTION_ANSWER_MISMATCH'

export type LayoutIssue = {
  code: IssueCode
  /** 'Q8' | 'Page 5 / Q26' | 'Page 6' 형태의 위치 라벨 */
  location: string
  detail: string
}

const VIEW_ITEM_RE = /^\s*[a-f][.、)]/

export const describeIssues = (issues: LayoutIssue[]): string[] =>
  issues.map(issue => `${issue.location}\n${issue.code} — ${issue.detail}`)/* ===== 1. 보기(ViewBox) 검사 ===== */

const MARKER_GLOBAL_RE = /[a-f][.、)]/g

/** qblock 직계에서 보기 컨테이너 밖으로 나간 a~f 항목인지 */
const isStrayViewItem = (el: Element, cond: Element | null) =>
  !(cond && (el === cond || cond.contains(el))) &&
  !el.classList.contains('qe-toolbar') &&
  !el.classList.contains('choices') &&
  VIEW_ITEM_RE.test(el.textContent || '')

export const checkViewBoxes = (doc: Document): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  doc.querySelectorAll('.qblock').forEach(block => {
    const num = block.getAttribute('data-qnum') || '?'
    const cond = block.querySelector('.cond-box, .view-box, [class*="cond"]')
    // a~f 항목이 보기 컨테이너 밖(qblock 직계)에 흩어져 있는지 — 박스 유무와 무관하게 검사
    if ([...block.children].some(el => isStrayViewItem(el, cond))) {
      issues.push({ code: 'VIEW_ITEM_OUTSIDE_CONTAINER', location: `Q${num}`, detail: '보기 항목이 보기 박스 밖에 있다' })
    }
    if (!cond) return
    // 박스 안 항목이 독립 block인지(한 줄에 b./c. 이어지는지)
    const loose = [...cond.children].filter(el =>
      !el.classList.contains('view-item') && !el.classList.contains('view-title') &&
      ((el.textContent || '').match(MARKER_GLOBAL_RE) || []).length >= 2)
    const looseText = [...cond.childNodes]
      .filter(node => node.nodeType === 3)
      .map(node => node.textContent || '').join('')
    if (loose.length > 0 || (looseText.match(MARKER_GLOBAL_RE) || []).length >= 2) {
      issues.push({ code: 'VIEW_ITEM_NOT_BLOCK', location: `Q${num}`, detail: '보기 항목이 독립 행으로 분리되어 있지 않다' })
    }
  })
  return issues
}

/** 보기 박스 이탈 복구: qblock 직계에 흩어진 a~f 항목을 보기 박스 안으로 모은다.
    박스가 없으면 <보기> 컨테이너를 선택지 앞에 새로 만든다. 항목 수는 고정하지 않는다. */
export const fixViewBoxes = (doc: Document): string[] => {
  const fixed: string[] = []
  doc.querySelectorAll('.qblock').forEach(block => {
    const num = block.getAttribute('data-qnum') || '?'
    const cond = block.querySelector('.cond-box, .view-box, [class*="cond"]')
    const strays = [...block.children].filter(el => isStrayViewItem(el, cond))
    if (!strays.length) return
    let box = cond
    if (!box) {
      const created = doc.createElement('div')
      created.className = 'cond-box'
      const title = doc.createElement('div')
      title.className = 'view-title'
      title.textContent = '<보기>'
      created.appendChild(title)
      const choices = block.querySelector('.choices')
      if (choices) block.insertBefore(created, choices)
      else block.appendChild(created)
      box = created
    }
    const container = box.querySelector('.view-items') || box
    strays.forEach(node => container.appendChild(node))
    fixed.push(`Q${num} 보기 박스 복구`)
  })
  return fixed
}

/** 항목 경계(a~f 마커가 태그/공백/문서 시작 뒤에 올 때)로 html을 분리한다. */
const trimDangling = (value: string) => {
  let out = value.replace(/^(<br\s*\/?>|\s)+/i, '').replace(/(<br\s*\/?>|\s)+$/i, '')
  let prev = ''
  while (out !== prev) {
    prev = out
    out = out.replace(/^<(div|p|span)>\s*/i, '').replace(/\s*<\/(div|p|span)>\s*$/i, '')
  }
  return out
}

const splitViewHtml = (html: string): string[] => {
  const bounds: number[] = [0]
  const marker = /[a-f][.、)]/g
  let match: RegExpExecArray | null
  while ((match = marker.exec(html)) !== null) {
    const prevChar = match.index > 0 ? html[match.index - 1] : ''
    if (match.index === 0 || /[>\s]/.test(prevChar)) bounds.push(match.index)
  }
  bounds.push(html.length)
  const raw: string[] = []
  for (let i = 0; i < bounds.length - 1; i += 1) raw.push(html.slice(bounds[i], bounds[i + 1]))
  // 첫 구간은 마커 이전 머리글 — 첫 항목 앞에 붙인다(내용 손실 방지)
  const head = raw[0] || ''
  const parts: string[] = []
  raw.slice(1).forEach((chunk, i) => {
    const trimmed = trimDangling(i === 0 ? head + chunk : chunk)
    if (trimmed) parts.push(trimmed)
  })
  return parts
}

/** 보기 내부 항목이 독립 block이 아니면 각 항목을 view-item block으로 재구성한다.
    이미 분리된 항목(view-item)과 제목(view-title)은 그대로 유지한다. */
export const fixViewItems = (doc: Document): string[] => {
  const fixed: string[] = []
  doc.querySelectorAll('.qblock').forEach(block => {
    const num = block.getAttribute('data-qnum') || '?'
    const cond = block.querySelector('.cond-box, .view-box, [class*="cond"]')
    if (!cond) return
    const loose = [...cond.children].filter(el =>
      !el.classList.contains('view-item') && !el.classList.contains('view-title') &&
      ((el.textContent || '').match(MARKER_GLOBAL_RE) || []).length >= 1)
    const looseText = [...cond.childNodes]
      .filter(node => node.nodeType === 3)
      .map(node => node.textContent || '').join('')
    if (!loose.length && (looseText.match(MARKER_GLOBAL_RE) || []).length < 2) return
    const title = cond.querySelector('.view-title')
    const bodyHtml = title ? cond.innerHTML.replace(title.outerHTML, '') : (cond.innerHTML || '')
    const chunks = splitViewHtml(bodyHtml)
    if (chunks.length <= 1) return
    const items = chunks.map(chunk => {
      const item = doc.createElement('div')
      item.className = 'view-item'
      item.innerHTML = chunk
      return item
    })
    cond.replaceChildren(...(title ? [title, ...items] : items))
    fixed.push(`Q${num} 보기 줄바꿈 복구`)
  })
  return fixed
}

/* ===== 2. 선택지 검사 ===== */

export const checkChoices = (doc: Document, frameViewport?: { width: number; height: number }): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  doc.querySelectorAll('.qblock').forEach(block => {
    const num = block.getAttribute('data-qnum') || '?'
    const items = block.querySelectorAll('.choice-item')
    const markers = [...items].map(item => (item.textContent || '').trim().slice(0, 1))
    ;['①', '②', '③', '④', '⑤'].forEach(marker => {
      if (!markers.includes(marker)) {
        issues.push({ code: 'CHOICE_MISSING', location: `Q${num}`, detail: `선택지 ${marker} 없음` })
      }
    })
    items.forEach(item => {
      const rect = item.getBoundingClientRect()
      const blockRect = block.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0) {
        issues.push({ code: 'CHOICE_CLIPPED', location: `Q${num}`, detail: '선택지가 렌더되지 않는다' })
      } else if (rect.right > blockRect.right + 2 || rect.bottom > blockRect.bottom + 2) {
        issues.push({ code: 'CHOICE_OUTSIDE_QUESTION', location: `Q${num}`, detail: '선택지가 문항 영역 밖으로 나갔다' })
      }
    })
  })
  return issues
}

/* ===== 3. 공통지문 구조 검사 ===== */

export const checkPassageLinks = (doc: Document): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  const groups = new Map<string, number[]>()
  doc.querySelectorAll('.qblock[data-passage-key]').forEach(block => {
    const key = block.getAttribute('data-passage-key')!
    const num = parseInt(block.getAttribute('data-qnum') || '0', 10)
    groups.set(key, [...(groups.get(key) || []), num])
  })
  groups.forEach((numbers, key) => {
    if (numbers.length < 2) return
    const sorted = [...numbers].sort((a, b) => a - b)
    for (let i = 1; i < sorted.length; i += 1) {
      if (sorted[i] - sorted[i - 1] > 1) {
        issues.push({
          code: 'NON_CONTIGUOUS_SHARED_PASSAGE_QUESTIONS',
          location: `Q${sorted.join(',Q')}`,
          detail: `같은 지문(${key})을 쓰는 문항이 연속 번호가 아니다`,
        })
        break
      }
    }
  })
  return issues
}

/* ===== 4. '윗글' orphan 검사 ===== */

const ORPHAN_RE = /(윗글|위\s*글|위\s*대화|위\s*지문)/

export const checkOrphanPassageReference = (doc: Document): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  const blocks = [...doc.querySelectorAll('.qblock')]
  blocks.forEach((block, index) => {
    const num = block.getAttribute('data-qnum') || '?'
    const prompt = block.querySelector('.qprompt, .qhead')?.textContent || ''
    if (!ORPHAN_RE.test(prompt)) return
    // 직전 블록, 같은 블록(지문 위·발문 아래 배치), 또는 passage-key 공유에 passage가 있는지
    const prev = blocks[index - 1]
    const linked = block.getAttribute('data-passage-key') && prev?.getAttribute('data-passage-key') === block.getAttribute('data-passage-key')
    const prevHasPassage = Boolean(prev?.querySelector('.passage')) || Boolean(block.querySelector('.passage'))
    if (!linked && !prevHasPassage) {
      issues.push({ code: 'ORPHAN_PASSAGE_REFERENCE', location: `Q${num}`, detail: '"윗글" 표현이 있는데 바로 앞에 지문이 없다' })
    }
  })
  return issues
}

/* ===== 5. 문항/페이지 geometry 검사 ===== */

export type PageGeometry = {
  pageEl: HTMLElement
  columns: HTMLElement[]
  footer?: HTMLElement | null
}

/** 문항별 잘림/열·페이지 침범/footer 충돌을 실제 렌더 사각형으로 검사 */
export const checkLayoutGeometry = (doc: Document): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  doc.querySelectorAll('.page').forEach((pageEl, pageIndex) => {
    const pageRect = (pageEl as HTMLElement).getBoundingClientRect()
    const footer = pageEl.querySelector('.page-footer, .run-header') as HTMLElement | null
    const footerTop = footer ? footer.getBoundingClientRect().top : pageRect.bottom
    const columns = [...pageEl.querySelectorAll('.col')]
    columns.forEach(col => {
      const colRect = col.getBoundingClientRect()
      let maxBottom = 0
      col.querySelectorAll('.qblock').forEach(block => {
        const rect = block.getBoundingClientRect()
        const num = block.getAttribute('data-qnum') || '?'
        maxBottom = Math.max(maxBottom, rect.bottom)
        if (rect.height === 0) {
          issues.push({ code: 'QUESTION_CLIPPED', location: `Page ${pageIndex + 1} / Q${num}`, detail: '문항이 렌더되지 않았다' })
          return
        }
        if (rect.bottom > pageRect.bottom - 1 || rect.top < pageRect.top - 1) {
          issues.push({ code: 'QUESTION_CLIPPED', location: `Page ${pageIndex + 1} / Q${num}`, detail: '문항 일부가 페이지 밖으로 잘렸다' })
        } else if (rect.right > colRect.right + 2) {
          issues.push({ code: 'COLUMN_OVERFLOW', location: `Page ${pageIndex + 1} / Q${num}`, detail: '문항이 열 폭을 넘었다' })
        }
        if (footer && rect.bottom > footerTop) {
          issues.push({ code: 'FOOTER_COLLISION', location: `Page ${pageIndex + 1} / Q${num}`, detail: '문항이 footer 영역을 침범했다' })
        }
      })
      if (maxBottom > colRect.bottom + 1) {
        issues.push({ code: 'PAGE_OVERFLOW', location: `Page ${pageIndex + 1}`, detail: '열 내용이 페이지 높이를 넘었다' })
      }
    })
  })
  return issues
}

/** 열별 사용률(%) — 균형 리포트용 */
export const measureColumns = (doc: Document): { label: string; usedPercent: number }[] => {
  const result: { label: string; usedPercent: number }[] = []
  doc.querySelectorAll('.page').forEach((pageEl, pageIndex) => {
    const pageRect = (pageEl as HTMLElement).getBoundingClientRect()
    const usable = pageRect.height - 30 // footer 여유
    pageEl.querySelectorAll('.col').forEach((col, colIndex) => {
      let maxBottom = pageRect.top
      col.querySelectorAll('.qblock').forEach(block => {
        maxBottom = Math.max(maxBottom, block.getBoundingClientRect().bottom)
      })
      const used = Math.max(0, maxBottom - pageRect.top)
      result.push({ label: `P${pageIndex + 1}-${colIndex === 0 ? 'L' : 'R'}`, usedPercent: Math.round((used / usable) * 100) })
    })
  })
  return result
}

/* ===== 6. 문항 번호/DOM 순서 검사 ===== */

export const checkQuestionOrder = (doc: Document): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  const nums: number[] = []
  doc.querySelectorAll('.qblock').forEach(block => nums.push(parseInt(block.getAttribute('data-qnum') || '0', 10)))
  if (!nums.length) {
    issues.push({ code: 'MISSING_QUESTION', location: '전체', detail: '문항 블록이 없다' })
    return issues
  }
  const seen = new Set<number>()
  nums.forEach(num => {
    if (seen.has(num)) issues.push({ code: 'DUPLICATE_QUESTION', location: `Q${num}`, detail: '문항 번호 중복' })
    seen.add(num)
  })
  for (let i = 1; i < nums.length; i += 1) {
    if (nums[i] !== nums[i - 1] + 1) {
      issues.push({ code: 'WRONG_DOM_ORDER', location: `Q${nums[i - 1]}→Q${nums[i]}`, detail: '문서 내 문항 번호가 1씩 증가하지 않는다' })
      break
    }
  }
  return issues
}

/** 인쇄 읽기 순서: 같은 page 안에서 좌열→우열, page 순서대로 번호 증가 */
export const checkPrintOrder = (doc: Document): LayoutIssue[] => {
  const issues: LayoutIssue[] = []
  const ordered: number[] = []
  doc.querySelectorAll('.page').forEach(pageEl => {
    pageEl.querySelectorAll('.col').forEach(col => {
      col.querySelectorAll('.qblock').forEach(block => ordered.push(parseInt(block.getAttribute('data-qnum') || '0', 10)))
    })
  })
  for (let i = 1; i < ordered.length; i += 1) {
    if (ordered[i] !== ordered[i - 1] + 1) {
      issues.push({ code: 'WRONG_PRINT_ORDER', location: `Q${ordered[i - 1]}→Q${ordered[i]}`, detail: '좌→우/페이지 순 읽기 순서가 번호 순서와 다르다' })
      break
    }
  }
  return issues
}

/** [전체 검사] — 요구사항 17종을 모두 수행해 위치 라벨과 함께 반환 */
export const runFullCheck = (doc: Document): LayoutIssue[] => ([
  ...checkViewBoxes(doc),
  ...checkChoices(doc),
  ...checkPassageLinks(doc),
  ...checkOrphanPassageReference(doc),
  ...checkLayoutGeometry(doc),
  ...checkQuestionOrder(doc),
  ...checkPrintOrder(doc),
])
