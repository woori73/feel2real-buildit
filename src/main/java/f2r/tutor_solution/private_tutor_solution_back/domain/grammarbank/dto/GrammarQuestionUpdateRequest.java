package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

/** 문제 본문·선택지·정답·해설 편집 요청. null 필드는 유지. */
public record GrammarQuestionUpdateRequest(
    String questionText,
    List<String> choices,
    String correctAnswer,
    String explanation,
    Integer point
) {
}
