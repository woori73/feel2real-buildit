package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.config.KstTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import java.time.LocalDateTime;

/** 수집 run 상태 + 카운터 + 연결된 import batch + 중앙 장부 저장 결과(수집 N / 장부 저장 N / 중복 N / 검토 N / 분류 N / 실패 N). */
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
    LocalDateTime completedAt,
    /** 기관 API 응답 수신 시각 — 장부 저장 완료와 다를 수 있다. */
    LocalDateTime fetchedAt,
    /** 중앙 장부 저장까지 끝난 마지막 조회일. */
    java.time.LocalDate ledgerSavedThrough,
    LocalDateTime ledgerSavedAt,
    int ledgerSavedCount,
    int ledgerReviewCount,
    int ledgerClassifiedCount
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
        KstTime.toKst(run.getStartedAt()),
        KstTime.toKst(run.getCompletedAt()),
        KstTime.toKst(run.getFetchedAt()),
        run.getLedgerSavedThrough(),
        KstTime.toKst(run.getLedgerSavedAt()),
        run.getLedgerSavedCount(),
        run.getLedgerReviewCount(),
        run.getLedgerClassifiedCount());
  }
}
