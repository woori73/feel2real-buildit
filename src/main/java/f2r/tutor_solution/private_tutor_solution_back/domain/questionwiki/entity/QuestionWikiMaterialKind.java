package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

/** 문항에 연결되는 기존 Wiki 자료의 종류. */
public enum QuestionWikiMaterialKind {
  /** {@code wiki_exam_originals} — 모의고사 원본(문제지/정답지). */
  EXAM_ORIGINAL,
  /** {@code wiki_analysis_materials} — 분석 자료 문서. */
  ANALYSIS_MATERIAL
}
