package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import java.util.List;

/** 수입 결과: 성공·중복·보류·실패 건수와 오류 요약. */
public record GrammarImportResultResponse(
    int total,
    int created,
    int duplicatesSkipped,
    int heldForReview,
    int failed,
    boolean isNew,
    List<String> errors
) {
}
