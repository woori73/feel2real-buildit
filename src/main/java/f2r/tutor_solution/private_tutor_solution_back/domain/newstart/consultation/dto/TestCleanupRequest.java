package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

/**
 * ADMIN 전용 테스트 데이터 정리 요청. 세 식별값을 명시적으로 받아 서버에서 재검증한다.
 *
 * @param consultationId 삭제 대상 상담 ID
 * @param userId 삭제 대상 회원의 userId
 * @param testLoginId 삭제 대상 회원의 loginId (테스트 계정 prefix 검증용)
 */
public record TestCleanupRequest(Long consultationId, Long userId, String testLoginId) {}
