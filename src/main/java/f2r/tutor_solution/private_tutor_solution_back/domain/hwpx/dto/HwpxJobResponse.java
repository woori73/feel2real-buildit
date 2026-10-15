package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import java.time.LocalDateTime;
import lombok.Getter;

/** HWPX 작업 응답. */
@Getter
public class HwpxJobResponse {
  private final Long id;
  private final String title;
  private final String sourceFormat;
  private final String status;
  private final int progress;
  private final String currentStep;
  private final String error;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public HwpxJobResponse(HwpxJob job) {
    this.id = job.getId();
    this.title = job.getTitle();
    this.sourceFormat = job.getSourceFormat();
    this.status = job.getStatus().name();
    this.progress = job.getProgress();
    this.currentStep = job.getCurrentStep();
    this.error = job.getError();
    this.createdAt = job.getCreatedAt();
    this.updatedAt = job.getUpdatedAt();
  }
}
