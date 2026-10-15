package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionProviderReadiness;
import java.time.LocalDateTime;

/**
 * ADMIN collection connection view. It never contains connectionRef, credentials, cursor values,
 * tokens, cookies, or raw provider payloads.
 */
public record EasyCollectionManagementResponse(
    Long connectionId,
    Long businessId,
    String label,
    String sourceCode,
    String sourceName,
    String connectionStatus,
    boolean enabled,
    boolean needsReauth,
    boolean hasConnectionRef,
    EasyCollectionProviderReadiness readiness,
    LocalDateTime lastSuccessAt,
    Long latestRunId,
    String latestRunStatus,
    LocalDateTime latestRunStartedAt,
    LocalDateTime latestRunFinishedAt,
    boolean checkpointExists
) {
  public static EasyCollectionManagementResponse from(EasyCollectionConnection connection,
      EasyCollectionRun latestRun, EasyCollectionProviderReadiness readiness,
      boolean checkpointExists) {
    return new EasyCollectionManagementResponse(
        connection.getConnectionId(),
        connection.getBusiness().getBusinessId(),
        connection.getLabel(),
        connection.getSource().getCode(),
        connection.getSource().getName(),
        connection.getStatus().name(),
        connection.getStatus() == EasyCollectionConnectionStatus.ACTIVE,
        connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH,
        connection.getConnectionRef() != null && !connection.getConnectionRef().isBlank(),
        readiness,
        connection.getLastSuccessAt(),
        latestRun == null ? null : latestRun.getRunId(),
        latestRun == null ? null : latestRun.getStatus().name(),
        latestRun == null ? null : latestRun.getStartedAt(),
        latestRun == null ? null : latestRun.getCompletedAt(),
        checkpointExists);
  }
}
