package f2r.tutor_solution.private_tutor_solution_back.domain.community.dto;

/** 답변 작성/수정 요청. Tiptap json/html. */
public record AnswerRequest(String contentJson, String contentHtml) {}
