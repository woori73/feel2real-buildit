package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWikiMaterialLink;

import java.util.Locale;

/**
 * 문항에 연결된 학습자료 1건. 사람이 눈으로 구별할 수 있는 정보만 담는다(내부 ID·enum 은
 * 화면에 노출하지 않는다 — {@code materialKind}/{@code materialId} 는 관리자 삭제용).
 *
 * <p>{@code previewKind == "HTML"} 이면 화면에 {@code [HTML]} 배지를 붙이고 {@code [보기]} 는
 * {@code previewUrl} 을 sandboxed iframe 으로 연다(다운로드 대신 격리 미리보기).
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
    String viewUrl,
    String previewKind,
    String previewUrl,
    String aiText) {

  public static QuestionWikiMaterialLinkResponse of(
      QuestionWikiMaterialLink link, String title, String description, String fileName) {
    QuestionWikiMaterialKind kind = link.getMaterialKind();
    boolean html = isHtml(fileName);
    return new QuestionWikiMaterialLinkResponse(
        link.getLinkId(),
        kind.name(),
        link.getMaterialId(),
        kindLabel(kind),
        link.getLabel(),
        title,
        description,
        fileName,
        viewUrl(kind, link.getMaterialId()),
        html ? "HTML" : null,
        html ? "/api/wiki/questions/materials/" + link.getLinkId() + "/preview" : null,
        null);
  }

  public QuestionWikiMaterialLinkResponse withAiText(String text) {
    return new QuestionWikiMaterialLinkResponse(
        linkId, materialKind, materialId, kindLabel, label, title, description, fileName,
        viewUrl, previewKind, previewUrl, text);
  }

  public static boolean isHtml(String fileName) {
    if (fileName == null) {
      return false;
    }
    String lower = fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".html") || lower.endsWith(".htm");
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
