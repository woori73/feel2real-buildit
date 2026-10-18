package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

/** AI/강사가 수정한 문항을 원본과 별개의 Question으로 등록하는 요청. */
public record GrammarVariantCreateRequest(
    String questionText,
    List<String> choices,
    String correctAnswer,
    String explanation,
    String changeReason
) {
}
