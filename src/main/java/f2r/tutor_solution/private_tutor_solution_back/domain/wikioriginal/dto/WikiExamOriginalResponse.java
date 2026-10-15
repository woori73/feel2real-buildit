package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import java.time.LocalDateTime;

public record WikiExamOriginalResponse(
    Long wikiExamOriginalId,
    String title,
    Integer grade,
    String examLabel,
    String docType,
    String docTypeLabel,
    String originalFileName,
    Long fileSize,
    boolean isVisible,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String downloadUrl
) {
  public static WikiExamOriginalResponse of(WikiExamOriginal original) {
    Long id = original.getWikiExamOriginalId();
    return new WikiExamOriginalResponse(
        id,
        original.getTitle(),
        original.getGrade(),
        original.getExamLabel(),
        original.getDocType().name(),
        original.getDocType().label(),
        original.getOriginalFileName(),
        original.getFileSize(),
        original.isVisible(),
        original.getCreatedAt(),
        original.getUpdatedAt(),
        "/api/wiki/originals/" + id + "/download"
    );
  }
}
