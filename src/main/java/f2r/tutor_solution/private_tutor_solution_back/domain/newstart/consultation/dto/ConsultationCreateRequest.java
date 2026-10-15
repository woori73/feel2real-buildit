package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

/** @param content 회원의 상담 질문 본문(빈 값·길이 초과는 서비스가 검증). @param linkDebt 본인 채무상황을 상담에 연결할지(§1). */
public record ConsultationCreateRequest(String content, boolean linkDebt) {}
