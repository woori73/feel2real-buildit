package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity;

/**
 * 감사회원 1:1 비공개 상담 "건"(스레드)의 상태. 운영자 상담함에서 한눈에 구분하려는 4가지(설계 §2).
 *
 * <p>전이는 서비스에서 결정적으로 일어난다(AI 판단 없음):
 * <ul>
 *   <li>{@link #NEW} — 회원이 첫 질문을 남기고 운영자가 아직 열지 않음(새 상담).</li>
 *   <li>{@link #AWAITING_ANSWER} — 운영자가 열었으나 아직 답변하지 않음(답변 대기).</li>
 *   <li>{@link #OPERATOR_ANSWERED} — 운영자 답변이 최신(운영자 답변 완료).</li>
 *   <li>{@link #MEMBER_FOLLOWUP} — 운영자 답변 후 회원이 추가 질문(회원 추가 질문).</li>
 * </ul>
 */
public enum ConsultationStatus {
  NEW,
  AWAITING_ANSWER,
  OPERATOR_ANSWERED,
  MEMBER_FOLLOWUP
}
