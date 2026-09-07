package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

/**
 * 자료 원본의 출처. Public Showcase 는 기존 Wiki 자료를 <b>참조</b>만 하고 원본 파일을
 * 복제하지 않는다(작업지시서 §15·§39).
 */
public enum MidtermMaterialSourceType {
  /** 연결된 원본 없음 — 표현 메타데이터만(썸네일/설명). */
  NONE,
  /** {@code wiki_analysis_materials} (ADMIN 공식 분석자료, 공개 다운로드). */
  WIKI_ANALYSIS,
  /** {@code wiki_learning_materials} (회원 업로드 학습자료). */
  WIKI_LEARNING,
  /** {@code problem_generator_materials} (visibility PRIVATE/PUBLIC). */
  PG_MATERIAL
}
