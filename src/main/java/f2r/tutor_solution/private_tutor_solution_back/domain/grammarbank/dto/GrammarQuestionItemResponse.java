package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionFormat;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarRightsStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.SchoolLevel;
import java.math.BigDecimal;
import java.util.List;

/** 검색 목록 항목. stemExcerpt는 발문 앞부분 요약. */
public record GrammarQuestionItemResponse(
    Long questionId,
    SchoolLevel schoolLevel,
    GrammarQuestionFormat questionFormat,
    GrammarDifficulty difficulty,
    GrammarReviewStatus reviewStatus,
    GrammarRightsStatus rightsStatus,
    BigDecimal classificationConfidence,
    List<GrammarTagRef> tags,
    String stemExcerpt,
    String correctAnswer,
    Long materialId,
    String sourcePosition,
    String externalRef,
    SchoolLevel recommendedGrade
) {
}
