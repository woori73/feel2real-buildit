import type { SchoolNode } from './types'
import { storageKey } from './workspaceStorage'

// 마지막으로 선택한 시험 Context(학교/학년/연도/학기/시험)를 기억한다. UI 편의 전용이며
// 인증·권한 판정에는 절대 쓰지 않는다. storageKey()가 workspace(legacy / user-<id>)별로
// 키를 분리하므로 다른 사용자·다른 workspace의 Context가 섞이지 않는다. admin이 workspace를
// 전환하면 AdminWorkspaceSelector가 전체 리로드하며 storageKey namespace가 함께 바뀐다.
export type ExamContext = { school: string; grade: string; year: number; semester: string; exam: string }

const KEY = 'f2r_wiki_exam_context'

export function loadExamContext(): Partial<ExamContext> {
  try {
    const raw = JSON.parse(localStorage.getItem(storageKey(KEY)) || '{}')
    return raw && typeof raw === 'object' ? raw as Partial<ExamContext> : {}
  } catch {
    return {}
  }
}

export function saveExamContext(context: ExamContext): void {
  try {
    if (context.school && context.grade) localStorage.setItem(storageKey(KEY), JSON.stringify(context))
  } catch {
    /* localStorage 불가(사파리 프라이빗 등)여도 Context 선택 자체는 동작한다 */
  }
}

export const EXAM_VALUES = ['전체', '중간고사', '기말고사'] as const

// 저장된 시험 Context를 학교 트리와 대조해 복원한다. 학교/학년이 더 이상 없으면 안전한 기본값으로만
// fallback 한다(연도·학기·시험은 트리와 무관한 고정 목록이라 값만 검증한다).
export function resolveSavedContext(
  saved: Partial<ExamContext>, nodes: SchoolNode[], fallbackYear: number,
): ExamContext {
  const node = nodes.find(n => n.school === saved.school) || nodes[0]
  const grade = node?.grades.find(g => g.grade === saved.grade)?.grade || node?.grades[0]?.grade || ''
  return {
    school: node?.school || '',
    grade,
    year: typeof saved.year === 'number' && Number.isFinite(saved.year) ? saved.year : fallbackYear,
    semester: saved.semester === '1학기' || saved.semester === '2학기' ? saved.semester : '1학기',
    exam: (EXAM_VALUES as readonly string[]).includes(saved.exam || '') ? (saved.exam as string) : '전체',
  }
}
