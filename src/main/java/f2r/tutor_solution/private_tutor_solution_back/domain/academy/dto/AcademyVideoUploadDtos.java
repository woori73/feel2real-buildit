package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public final class AcademyVideoUploadDtos {
  private AcademyVideoUploadDtos() {}

  public record InitiateRequest(
      @NotBlank String filename,
      @Min(1) @Max(209_715_200L) long declaredSizeBytes) {}

  /** uploadUrl is an opaque, TLS-only bearer capability. */
  public record PartCapability(int partNumber, String uploadUrl) {}

  public record InitiateResponse(
      String sessionId,
      LocalDateTime serverNow,
      LocalDateTime deadlineAt,
      long partSizeBytes,
      List<PartCapability> parts) {}

  public record CompletedPart(
      @Min(1) int partNumber,
      @NotBlank String eTag,
      String checksum) {}

  public record CompleteRequest(@NotEmpty List<@Valid CompletedPart> parts) {}

  public record SessionResponse(
      String sessionId,
      VideoUploadSessionState state,
      Long acceptedVideoId,
      String errorCode,
      Integer retryAfterSeconds) {}

  /** Safe refresh-recovery projection. Storage capabilities are deliberately excluded. */
  public record SessionObservation(
      String sessionId,
      VideoUploadSessionState state,
      Long acceptedVideoId,
      String errorCode,
      Integer retryAfterSeconds,
      LocalDateTime serverNow,
      LocalDateTime deadlineAt) {}
}
