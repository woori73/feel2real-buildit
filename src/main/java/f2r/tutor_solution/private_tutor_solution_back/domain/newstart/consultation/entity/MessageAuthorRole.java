package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity;

/**
 * 상담 스레드 메시지의 작성 주체.
 *
 * <p>{@link #AI_RESEARCH}는 [지피지기에게 조사시키기] 결과로 <b>운영자에게만</b> 보인다(설계 §5).
 * 회원 스레드 조회에서는 항상 제외하며, 회원에게 자동 전송되지 않는다 — 운영자가 읽고 수정·보완해
 * {@link #OPERATOR} 답변으로 보내야 회원에게 전달된다.
 */
public enum MessageAuthorRole {
  MEMBER,
  OPERATOR,
  AI_RESEARCH;

  /** 회원 스레드에 노출되는 작성 주체인가. AI_RESEARCH는 운영자 전용이라 false. */
  public boolean visibleToMember() {
    return this != AI_RESEARCH;
  }
}
