package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialLink;

/**
 * 문항에 연결된 학습자료 1건. 사람이 눈으로 구별할 수 있는 정보만 담는다(내부 ID·enum 은
 * 화면에 노출하지 않는다 — {@code materialKind}/{@code materialId} 는 관리자 삭제용).
 */
public record QuestionWikiMaterialLinkResponse(
    Long linkId,
    String materialKind,
    Long materialId,
    String kindLabel,
    String label,
    String title,
    String description,
    String fileName,
    String viewUrl) {

  public static QuestionWikiMaterialLinkResponse of(
      QuestionWikiMaterialLink link, String title, String description, String fileName) {
    QuestionWikiMaterialKind kind = link.getMaterialKind();
    return new QuestionWikiMaterialLinkResponse(
        link.getLinkId(),
        kind.name(),
        link.getMaterialId(),
        kindLabel(kind),
        link.getLabel(),
        title,
        description,
        fileName,
        viewUrl(kind, link.getMaterialId()));
  }

  public static String kindLabel(QuestionWikiMaterialKind kind) {
    return switch (kind) {
      case EXAM_ORIGINAL -> "모의고사 원본";
      case ANALYSIS_MATERIAL -> "학습자료";
    };
  }

  public static String viewUrl(QuestionWikiMaterialKind kind, Long materialId) {
    return switch (kind) {
      case EXAM_ORIGINAL -> "/api/wiki/originals/" + materialId + "/download";
      case ANALYSIS_MATERIAL -> "/api/wiki/analysis-materials/" + materialId + "/download";
    };
  }
}
