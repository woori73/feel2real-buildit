package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingVersion;
import java.time.LocalDateTime;

/** 버전(스냅샷) 응답. */
public record VersionResponse(
    Long versionId,
    String label,
    LocalDateTime createdAt
) {

  public static VersionResponse from(PublishingVersion v) {
    return new VersionResponse(v.getId(), v.getLabel(), v.getCreatedAt());
  }
}
