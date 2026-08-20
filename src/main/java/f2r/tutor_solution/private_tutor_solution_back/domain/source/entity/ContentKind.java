package f2r.tutor_solution.private_tutor_solution_back.domain.source.entity;

/**
 * Source 자료 분류 — MVP §4.5 / §5.0.
 *
 * <ul>
 *   <li>{@link #GENERAL} — 학습 자료 (시험지 아님). 추출 없음, 즉시 COMPLETED.</li>
 *   <li>{@link #EXAM_BLANK} — 시험지(채점 흔적 없음). 학생 폼에서 "시험지"로 선택 시 일단 이 값으로 저장된다.
 *       LLM 분석 후 채점 흔적 감지 시 {@link #EXAM_GRADED}로 promote된다. questions INSERT 후 즉시 COMPLETED.</li>
 *   <li>{@link #EXAM_GRADED} — 채점된 시험지. LLM이 student_answer/is_correct를 추출할 수 있는 경우.
 *       PENDING_REVIEW로 전이되어 학생 검증을 거친 뒤 student_answers/student_scores INSERT.</li>
 *   <li>{@link #UNCLASSIFIED} — 폼이 비어 들어왔을 때의 폴백. 정상 흐름에선 사용되지 않는다.</li>
 * </ul>
 */
public enum ContentKind {
  GENERAL,
  EXAM_BLANK,
  EXAM_GRADED,
  UNCLASSIFIED
}
