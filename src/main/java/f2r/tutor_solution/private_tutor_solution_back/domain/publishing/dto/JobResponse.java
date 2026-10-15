package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import java.time.LocalDateTime;

/** 출판 작업 응답 (Qoder 명세 4.1 계약). */
public record JobResponse(
    Long jobId,
    String title,
    String template,
    String status,
    int progress,
    String currentStep,
    String error,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

  public static JobResponse from(PublishingJob job) {
    return new JobResponse(
        job.getId(),
        job.getTitle(),
        job.getTemplate(),
        job.getStatus().name(),
        job.getProgress(),
        job.getCurrentStep(),
        job.getError(),
        job.getCreatedAt(),
        job.getUpdatedAt()
    );
  }
}
