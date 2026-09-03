package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialKind;

public record QuestionWikiMaterialLinkRequest(
    QuestionWikiMaterialKind materialKind,
    Long materialId,
    String label) {}
