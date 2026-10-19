package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionFormat;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarRightsStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.SchoolLevel;
import java.math.BigDecimal;
import java.util.List;

/** 문제 상세 + 원본(passage/material) + 변형본 이력. */
public record GrammarQuestionDetailResponse(
    Long questionId,
    String questionType,
    SchoolLevel schoolLevel,
    GrammarQuestionFormat questionFormat,
    GrammarDifficulty difficulty,
    GrammarReviewStatus reviewStatus,
    GrammarRightsStatus rightsStatus,
    BigDecimal classificationConfidence,
    String autoClassification,
    String keywords,
    List<GrammarTagRef> tags,
    String questionText,
    List<String> choices,
    String correctAnswer,
    String explanation,
    String questionLabel,
    Integer point,
    Long passageId,
    String passageTitle,
    String passageContent,
    Long materialId,
    String materialFileName,
    String sourcePosition,
    String externalRef,
    List<GrammarVariantResponse> variants,
    SchoolLevel recommendedGrade,
    java.util.Map<String, Object> reviewMetadata
) {
}
