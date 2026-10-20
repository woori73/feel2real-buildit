package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import java.time.LocalDateTime;

public record WikiExamOriginalResponse(
    Long wikiExamOriginalId,
    String title,
    Integer grade,
    Integer examYear,
    Integer examMonth,
    String examLabel,
    String docType,
    String docTypeLabel,
    String originalFileName,
    String fileExtension,
    Long fileSize,
    boolean isVisible,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String downloadUrl,
    String viewUrl
) {
  public static WikiExamOriginalResponse of(WikiExamOriginal original) {
    Long id = original.getWikiExamOriginalId();
    String ext = extensionOf(original.getOriginalFileName());
    return new WikiExamOriginalResponse(
        id,
        original.getTitle(),
        original.getGrade(),
        original.getExamYear(),
        original.getExamMonth(),
        original.getExamLabel(),
        original.getDocType().name(),
        original.getDocType().label(),
        original.getOriginalFileName(),
        ext,
        original.getFileSize(),
        original.isVisible(),
        original.getCreatedAt(),
        original.getUpdatedAt(),
        "/api/wiki/originals/" + id + "/download",
        "/api/wiki/originals/" + id + "/view"
    );
  }

  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return null;
    }
    int dot = fileName.lastIndexOf('.');
    return dot < 0 || dot == fileName.length() - 1
        ? null
        : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }
}
