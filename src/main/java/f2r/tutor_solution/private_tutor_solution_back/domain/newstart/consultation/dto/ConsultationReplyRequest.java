package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

/** 회원이 기존 상담 스레드에 잇는 추가 질문(§2). 발신자는 서버 principal에서만 결정된다. */
public record ConsultationReplyRequest(String content) {}
