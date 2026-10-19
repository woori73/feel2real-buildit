package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarReviewStatus;
import java.util.List;

/** 자동 분류 검수 판정. APPROVED/REJECTED만 허용. tagIds가 있으면 태그도 함께 교체. */
public record GrammarReviewRequest(
    GrammarReviewStatus status,
    List<Long> tagIds,
    Long primaryTagId
) {
}
