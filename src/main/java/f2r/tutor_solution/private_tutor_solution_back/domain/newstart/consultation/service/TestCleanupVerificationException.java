package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

/**
 * 테스트 데이터 정리 검증 실패 — 호출자 트랜잭션을 롤백시켜 아무것도 삭제되지 않게 한다.
 * 메시지에 식별값·개인정보를 포함하지 않는다.
 */
public class TestCleanupVerificationException extends RuntimeException {
  public TestCleanupVerificationException(String message) {
    super(message);
  }
}
