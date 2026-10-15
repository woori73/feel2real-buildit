package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTask;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EasyCollectorTaskResponse(
    Long taskId,
    Long collectorId,
    Long businessId,
    String sourceCode,
    String dataType,
    LocalDate requestedFrom,
    LocalDate requestedTo,
    String status,
    String errorSummary,
    String downloadedFilename,
    Long downloadedSize,
    String downloadedSha256,
    LocalDateTime downloadedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {
  public static EasyCollectorTaskResponse from(EasyCollectorTask task) {
    return new EasyCollectorTaskResponse(
        task.getTaskId(),
        task.getCollector().getCollectorId(),
        task.getBusiness().getBusinessId(),
        task.getSourceCode(),
        task.getDataType(),
        task.getRequestedFrom(),
        task.getRequestedTo(),
        task.getStatus().name(),
        task.getErrorSummary(),
        task.getDownloadedFilename(),
        task.getDownloadedSize(),
        task.getDownloadedSha256(),
        task.getDownloadedAt(),
        task.getStartedAt(),
        task.getCompletedAt());
  }
}
