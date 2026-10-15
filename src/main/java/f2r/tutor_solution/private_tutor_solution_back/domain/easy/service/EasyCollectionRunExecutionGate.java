package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-backed execution gate shared by scheduler and manual collection paths.
 *
 * <p>The connection row lock is the multi-instance source of truth. Readiness, stale recovery,
 * active-run detection and the new claim occur in one transaction while that lock is held.</p>
 */
@Service
public class EasyCollectionRunExecutionGate {

  public static final String STALE_SUMMARY =
      "STALE_RUN_RECOVERED: execution lease expired before completion.";

  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionProviderReadinessPolicy readinessPolicy;
  private final long leaseSeconds;
  private final long legacyStaleSeconds;

  public EasyCollectionRunExecutionGate(
      EasyCollectionConnectionRepository connectionRepository,
      EasyCollectionRunRepository runRepository,
      EasyCollectionProviderReadinessPolicy readinessPolicy,
      @Value("${easy.collection.lease-seconds:300}") long leaseSeconds,
      @Value("${easy.collection.legacy-stale-seconds:900}") long legacyStaleSeconds) {
    this.connectionRepository = connectionRepository;
    this.runRepository = runRepository;
    this.readinessPolicy = readinessPolicy;
    this.leaseSeconds = Math.max(30L, leaseSeconds);
    this.legacyStaleSeconds = Math.max(this.leaseSeconds, legacyStaleSeconds);
  }

  public EasyCollectionProviderReadiness readiness(EasyCollectionConnection connection) {
    if (connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      return EasyCollectionProviderReadiness.NEEDS_REAUTH;
    }
    if (connection.getStatus() != EasyCollectionConnectionStatus.ACTIVE
        || !connection.getSource().isActive()) {
      return EasyCollectionProviderReadiness.DISABLED;
    }
    if (connection.getConnectionRef() == null || connection.getConnectionRef().isBlank()) {
      return EasyCollectionProviderReadiness.NOT_CONFIGURED;
    }
    if (!readinessPolicy.isVerified(connection.getSource())) {
      return EasyCollectionProviderReadiness.UNVERIFIED;
    }
    return EasyCollectionProviderReadiness.READY;
  }

  @Transactional
  public Claim claim(Long connectionId, EasyCollectionTriggerType triggerType,
      String adapterCode, String adapterVersion) {
    EasyCollectionConnection connection = connectionRepository.findByIdWithLock(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Collection connection not found."));
    EasyCollectionProviderReadiness readiness = readiness(connection);
    if (readiness != EasyCollectionProviderReadiness.READY) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Collection provider is not ready: " + readiness.name());
    }

    LocalDateTime now = LocalDateTime.now();
    List<EasyCollectionRun> running = runRepository
        .findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
            connectionId, EasyCollectionRunStatus.RUNNING);
    for (EasyCollectionRun run : running) {
      if (isStale(run, now)) {
        run.fail(STALE_SUMMARY);
        runRepository.save(run);
      } else {
        throw new IllegalStateException("A collection run is already in progress.");
      }
    }

    String token = UUID.randomUUID().toString();
    EasyCollectionRun run = EasyCollectionRun.builder()
        .connection(connection)
        .business(connection.getBusiness())
        .triggerType(triggerType.name())
        .sourceFilename(adapterCode + "-" + now.toLocalDate())
        .adapterCode(adapterCode)
        .adapterVersion(adapterVersion)
        .build();
    run.claimLease(token, now, now.plusSeconds(leaseSeconds));
    run = runRepository.save(run);
    return new Claim(run.getRunId(), connection.getBusiness().getBusinessId(), connectionId,
        connection.getConnectionRef(), connection.getSource().getCode(), adapterCode, token);
  }

  @Transactional
  public void heartbeat(Long runId, Long connectionId, String token) {
    lockConnection(connectionId);
    EasyCollectionRun run = lockedRun(runId);
    if (run.getStatus() != EasyCollectionRunStatus.RUNNING) {
      throw new IllegalStateException("Cannot heartbeat a terminal collection run.");
    }
    if (!run.isOwnedBy(token)) {
      throw new IllegalStateException("Collection run ownership token does not match.");
    }
    LocalDateTime now = LocalDateTime.now();
    run.heartbeat(token, now, now.plusSeconds(leaseSeconds));
    runRepository.save(run);
  }

  @Transactional
  public void requireOwnedRunning(Long runId, Long connectionId, String token) {
    lockConnection(connectionId);
    EasyCollectionRun run = lockedRun(runId);
    if (run.getStatus() != EasyCollectionRunStatus.RUNNING || !run.isOwnedBy(token)) {
      throw new IllegalStateException("Collection run ownership was lost.");
    }
  }

  boolean isStale(EasyCollectionRun run, LocalDateTime now) {
    if (run.getLeaseExpiresAt() != null) {
      return run.getLeaseExpiresAt().isBefore(now);
    }
    return run.getStartedAt() != null
        && run.getStartedAt().isBefore(now.minusSeconds(legacyStaleSeconds));
  }

  private void lockConnection(Long connectionId) {
    connectionRepository.findByIdWithLock(connectionId)
        .orElseThrow(() -> new IllegalStateException("Collection connection not found."));
  }

  private EasyCollectionRun lockedRun(Long runId) {
    return runRepository.findByIdWithLock(runId)
        .orElseThrow(() -> new IllegalStateException("Collection run not found."));
  }

  public record Claim(Long runId, Long businessId, Long connectionId, String connectionRef,
                      String sourceCode, String adapterCode, String leaseToken) {}
}
