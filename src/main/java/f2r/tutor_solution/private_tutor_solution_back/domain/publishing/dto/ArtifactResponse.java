package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingArtifact;
import java.nio.file.Path;
import java.time.LocalDateTime;

/** 산출물 응답 (Qoder API_INTEGRATION.md 갭-G4: fileName 포함). */
public record ArtifactResponse(
    Long artifactId,
    String format,
    String standard,
    String fileName,
    Long fileSize,
    Integer pageCount,
    LocalDateTime createdAt
) {

  public static ArtifactResponse from(PublishingArtifact a) {
    String fileName = null;
    try {
      fileName = Path.of(a.getFilePath()).getFileName().toString();
    } catch (Exception ignored) {
    }
    return new ArtifactResponse(
        a.getId(),
        a.getFormat(),
        a.getStandard(),
        fileName,
        a.getFileSize(),
        a.getPageCount(),
        a.getCreatedAt()
    );
  }
}
