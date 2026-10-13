package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingArtifact;
import java.time.LocalDateTime;

/** 산출물 응답 (Qoder 명세 4.4 계약). */
public record ArtifactResponse(
    Long artifactId,
    String format,
    String standard,
    Long fileSize,
    Integer pageCount,
    LocalDateTime createdAt
) {

  public static ArtifactResponse from(PublishingArtifact a) {
    return new ArtifactResponse(
        a.getId(),
        a.getFormat(),
        a.getStandard(),
        a.getFileSize(),
        a.getPageCount(),
        a.getCreatedAt()
    );
  }
}
