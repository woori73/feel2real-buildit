package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import java.time.LocalDateTime;

/** 수집 run 상태 + 카운터 + 연결된 import batch — 관리자 최소 상태 확인용. */
public record EasyCollectionRunResponse(
    Long runId,
    Long connectionId,
    Long businessId,
    String sourceCode,
    String triggerType,
    String status,
    String sourceFilename,
    String adapterCode,
    String adapterVersion,
    int fetchedCount,
    int acceptedCount,
    int duplicateCount,
    int errorCount,
    Long importBatchId,
    String errorSummary,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {
  public static EasyCollectionRunResponse from(EasyCollectionRun run) {
    return new EasyCollectionRunResponse(
        run.getRunId(),
        run.getConnection().getConnectionId(),
        run.getBusiness().getBusinessId(),
        run.getConnection().getSource().getCode(),
        run.getTriggerType(),
        run.getStatus().name(),
        run.getSourceFilename(),
        run.getAdapterCode(),
        run.getAdapterVersion(),
        run.getFetchedCount(),
        run.getAcceptedCount(),
        run.getDuplicateCount(),
        run.getErrorCount(),
        run.getImportBatchId(),
        run.getErrorSummary(),
        run.getStartedAt(),
        run.getCompletedAt());
  }
}
