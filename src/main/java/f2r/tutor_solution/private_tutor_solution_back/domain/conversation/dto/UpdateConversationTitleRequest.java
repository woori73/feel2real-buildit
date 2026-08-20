package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

/** 대화명(title) 수정 요청. 학생이 종합 현황판 제목 inline 편집에서 보낸다. */
public record UpdateConversationTitleRequest(String title) {}
