package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;

public record QuestionSetUpdateRequest(
    String title,
    String grammar,
    String promptPassage,
    MaterialVisibility visibility) {
}
