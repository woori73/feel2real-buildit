import type { StoredQuestion } from './types'
import type { WikiQuestionV1 } from './wikiQuestion'

// 강사 문제 DB와 교과서 문제 DB는 서로 다른 Question Source 일 뿐, 이후 Chrome 변형 pipeline 은 하나다.
export type SourceKind = 'teacher' | 'textbook' | 'all'

export const questionSourceOf = (q: Pick<StoredQuestion, 'source_kind'>): 'teacher' | 'textbook' =>
  q.source_kind === 'textbook' ? 'textbook' : 'teacher'

// 가운데 Preview 위에 표시하는 출처 한 줄.
export const questionOrigin = (q: StoredQuestion): string => {
  if (questionSourceOf(q) === 'textbook') {
    return ['교과서 DB', q.publisher, q.lesson, q.page ? `p.${q.page}` : '', q.question_number ? `${q.question_number}번` : '', `QID ${q.id}`]
      .filter(Boolean).join(' · ')
  }
  return ['강사 문제 DB', [q.school, q.grade].filter(Boolean).join(''), `QID ${q.id}`].filter(Boolean).join(' · ')
}

type ExamContext = { school?: string; grade?: string; year?: number; semester?: string; exam?: string }

// 변형본 저장 시 원본 → 파생 provenance. 교과서 원본은 절대 덮어쓰지 않는다(별도 generated_test row).
export const buildTransformProvenance = (
  source: StoredQuestion,
  context: ExamContext,
  extra: { digital_twin_id?: number | null; material_ids?: number[]; insight_ids?: number[]; revision_note?: string },
) => {
  const kind = questionSourceOf(source)
  // PDF 페이지와 교과서 Question ID를 한 필드에 섞지 않는다 — 각각 독립 key로 보존한다.
  const textbookPage = kind === 'textbook' ? (typeof source.page === 'number' ? source.page : null) : null
  const provenance = {
    source_kind: kind,
    source_question_id: source.id,                       // 교과서면 textbook_questions.id (원본 문항)
    textbook_material_id: kind === 'textbook' ? source.textbook_material_id ?? null : null,  // 원본 PDF
    source_page: textbookPage,                           // 원본 PDF 페이지 번호 (number)
    source_question_number: kind === 'textbook' ? source.question_number ?? '' : source.source_question_number ?? '',  // 교과서 문항번호
    digital_twin_id: extra.digital_twin_id ?? null,
    material_ids: extra.material_ids ?? [],
    insight_ids: extra.insight_ids ?? [],
    revision_note: extra.revision_note ?? '',
    current_context: { school: context.school, grade: context.grade, year: context.year, semester: context.semester, exam: context.exam },
    workbench: 'chrome-question-transform',
  }
  const source_material = kind === 'textbook'
    ? ['교과서', source.publisher, source.lesson, textbookPage ? `p.${textbookPage}` : '', `문항번호 ${source.question_number || '-'}`].filter(Boolean).join(' · ')
    : `기존 문제 QID ${source.id}`
  // generated_tests.source_page 는 실제 원본 PDF 페이지 의미를 유지한다.
  const source_page = kind === 'textbook' ? (textbookPage != null ? String(textbookPage) : '') : `question:${source.id}`
  return { kind, provenance, source_material, source_page }
}

// 변형 canonical 의 source 블록에도 출처를 새긴다(원본 문항 참조를 잃지 않게).
export const stampCanonicalSource = (canonical: WikiQuestionV1, source: StoredQuestion, context: ExamContext): WikiQuestionV1 => {
  const kind = questionSourceOf(source)
  return {
    ...canonical,
    source: {
      ...canonical.source,
      school: context.school, grade: context.grade, year: context.year, semester: context.semester, exam: context.exam,
      source_kind: kind,
      source_material_id: kind === 'textbook' ? source.textbook_material_id : canonical.source?.source_material_id,
      source_page: kind === 'textbook' ? String(source.page ?? '') : `question:${source.id}`,
      source_question_number: kind === 'textbook' ? (source.question_number || '') : (source.source_question_number || ''),
      publisher: kind === 'textbook' ? source.publisher : undefined,
      lesson: kind === 'textbook' ? source.lesson : undefined,
    },
  }
}
