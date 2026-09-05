package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

/**
 * AI 제안 생성이 실패했을 때(provider 미구성, provider 오류, 빈/손상된 응답, 허용 길이 초과 등).
 * 사유가 그대로 사용자에게 보여야 하므로 전용 예외를 쓴다({@link VideoUploadRejectedException}과
 * 같은 패턴). 이 예외가 발생해도 Academy 데이터나 builderDraft는 전혀 건드리지 않는다 — 생성
 * 단계는 순수 조회/외부 호출이라 실패해도 되돌릴 상태가 없다.
 */
public class AcademyAiGenerationException extends RuntimeException {
  public AcademyAiGenerationException(String message) {
    super(message);
  }
}
