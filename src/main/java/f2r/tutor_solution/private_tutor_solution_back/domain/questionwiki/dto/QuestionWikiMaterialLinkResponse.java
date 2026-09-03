package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialLink;

public record QuestionWikiMaterialLinkResponse(
    Long linkId,
    String materialKind,
    Long materialId,
    String label,
    String downloadUrl) {

  public static QuestionWikiMaterialLinkResponse from(QuestionWikiMaterialLink link) {
    return new QuestionWikiMaterialLinkResponse(
        link.getLinkId(),
        link.getMaterialKind().name(),
        link.getMaterialId(),
        link.getLabel(),
        downloadUrl(link.getMaterialKind(), link.getMaterialId()));
  }

  private static String downloadUrl(QuestionWikiMaterialKind kind, Long materialId) {
    return switch (kind) {
      case EXAM_ORIGINAL -> "/api/wiki/originals/" + materialId + "/download";
      case ANALYSIS_MATERIAL -> "/api/wiki/analysis-materials/" + materialId + "/download";
    };
  }
}
