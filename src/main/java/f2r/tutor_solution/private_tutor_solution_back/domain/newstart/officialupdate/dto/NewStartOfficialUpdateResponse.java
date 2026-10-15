package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import java.time.LocalDateTime;

public record NewStartOfficialUpdateResponse(
    Long updateId,
    String sourceType,
    String sourceName,
    String title,
    String summary,
    String originalUrl,
    String officialDocumentId,
    LocalDateTime publishedAt,
    LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    String category,
    String relevancePriority,
    String reviewStatus,
    boolean featured,
    LocalDateTime discoveredAt,
    String reviewedBy,
    LocalDateTime reviewedAt) {

  public static NewStartOfficialUpdateResponse from(NewStartOfficialUpdate update) {
    return new NewStartOfficialUpdateResponse(
        update.getUpdateId(),
        update.getSourceType().name(),
        update.getSourceName(),
        update.getTitle(),
        update.getSummary(),
        update.getOriginalUrl(),
        update.getOfficialDocumentId(),
        update.getPublishedAt(),
        update.getEffectiveFrom(),
        update.getEffectiveTo(),
        update.getCategory().name(),
        update.getRelevancePriority().name(),
        update.getReviewStatus().name(),
        update.isFeatured(),
        update.getDiscoveredAt(),
        update.getReviewedBy(),
        update.getReviewedAt());
  }
}
