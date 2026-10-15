package f2r.tutor_solution.private_tutor_solution_back.domain.source.entity;

/**
 * Source 분석 워크플로 상태 (MVP §5.0).
 *
 * <p>상태 머신:
 * <pre>
 *   PENDING       — 업로드 직후, 분석 대기
 *   ANALYZING     — LLM 1차 패스 진행 중
 *   PENDING_REVIEW — 시험지로 분류되어 학생 검증(메타+채점) 대기
 *   COMPLETED     — 분석 종료, 채팅에 사용 가능
 *   FAILED        — 분석 실패
 * </pre>
 *
 * <p>채팅(LLM 호출)은 모든 첨부 source가 {@link #COMPLETED}일 때만 허용된다.
 */
public enum ProcessingStatus {
  PENDING,
  ANALYZING,
  PENDING_REVIEW,
  COMPLETED,
  FAILED
}
