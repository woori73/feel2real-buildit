package f2r.tutor_solution.private_tutor_solution_back.domain.community.dto;

/**
 * 질문 작성/수정 요청.
 * boardType=FREE|QNA|SUBJECT. SUBJECT일 때만 subjectId 필수(그 외 무시). content=Tiptap json/html.
 */
public record CommunityPostRequest(String boardType, Integer subjectId, String title, String contentJson, String contentHtml) {}
