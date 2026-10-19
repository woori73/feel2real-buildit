package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionFormat;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarRightsStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.SchoolLevel;
import java.util.List;

/** 분류 메타 수정 요청. null 필드는 변경하지 않는다. */
public record GrammarProfileUpdateRequest(
    SchoolLevel schoolLevel,
    GrammarQuestionFormat questionFormat,
    GrammarDifficulty difficulty,
    String keywords,
    GrammarRightsStatus rightsStatus,
    List<Long> tagIds,
    Long primaryTagId,
    SchoolLevel recommendedGrade
) {
}
