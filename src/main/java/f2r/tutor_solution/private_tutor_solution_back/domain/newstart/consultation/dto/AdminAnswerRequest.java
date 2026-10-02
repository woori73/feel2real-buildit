package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto;

/** 운영자가 회원에게 보내는 답변(§5). [회원에게 답변] 경로로만 들어오며, AI 조사 결과와 별개다. */
public record AdminAnswerRequest(String content) {}
