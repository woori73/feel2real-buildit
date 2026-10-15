package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

/** ADMIN 전용 테스트 데이터 정리 결과. */
public record TestCleanupResult(
    Long deletedConsultationId,
    long deletedMessageCount,
    Long deletedUserId,
    String deletedLoginId) {}
