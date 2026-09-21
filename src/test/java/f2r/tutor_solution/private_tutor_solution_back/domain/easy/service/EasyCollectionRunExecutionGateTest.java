package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EasyCollectionRunExecutionGateTest {

  private final EasyCollectionConnectionRepository connections =
      mock(EasyCollectionConnectionRepository.class);
  private final EasyCollectionRunRepository runs = mock(EasyCollectionRunRepository.class);
  private final EasyCollectionProviderReadinessPolicy readiness =
      mock(EasyCollectionProviderReadinessPolicy.class);
  private EasyCollectionRunExecutionGate gate;
  private EasyCollectionConnection connection;
  private EasyBusiness business;
  private EasyCollectionSource source;

  @BeforeEach
  void setUp() {
    reset(connections, runs, readiness);
    gate = new EasyCollectionRunExecutionGate(connections, runs, readiness, 300, 900);
    business = mock(EasyBusiness.class);
    source = EasyCollectionSource.builder()
        .code("TEST_READY").name("test").adapterType("TEST").active(true).build();
    connection = EasyCollectionConnection.builder()
        .business(business).source(source).label("main").connectionRef("opaque-ref").build();
    ReflectionTestUtils.setField(connection, "connectionId", 7L);
    when(business.getBusinessId()).thenReturn(1L);
    when(connections.findByIdWithLock(7L)).thenReturn(Optional.of(connection));
    when(readiness.isVerified(source)).thenReturn(true);
    when(runs.findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
        7L, EasyCollectionRunStatus.RUNNING)).thenReturn(List.of());
    when(runs.save(any())).thenAnswer(invocation -> {
      EasyCollectionRun saved = invocation.getArgument(0);
      if (saved.getRunId() == null) {
        ReflectionTestUtils.setField(saved, "runId", 100L);
      }
      return saved;
    });
  }

  @Test
  void activeLeaseBlocksSecondOwnerAcrossAnyCallerOrInstance() {
    EasyCollectionRun active = runningRun("owner-a",
        LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(4));
    when(runs.findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
        7L, EasyCollectionRunStatus.RUNNING)).thenReturn(List.of(active));

    assertThatThrownBy(() -> gate.claim(7L, EasyCollectionTriggerType.MANUAL, "TEST", "v1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already");
    verify(runs, never()).save(argThat(run -> run != active));
  }

  @Test
  void expiredLeaseFailsOldRunBeforeNewClaim() {
    EasyCollectionRun expired = runningRun("owner-a",
        LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusSeconds(1));
    when(runs.findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
        7L, EasyCollectionRunStatus.RUNNING)).thenReturn(List.of(expired));

    EasyCollectionRunExecutionGate.Claim claim =
        gate.claim(7L, EasyCollectionTriggerType.SCHEDULED, "TEST", "v1");

    assertThat(expired.getStatus()).isEqualTo(EasyCollectionRunStatus.FAILED);
    assertThat(expired.getCompletedAt()).isNotNull();
    assertThat(expired.getErrorSummary()).startsWith("STALE_RUN_RECOVERED:");
    assertThat(claim.leaseToken()).isNotBlank();
    assertThat(claim.runId()).isEqualTo(100L);
  }

  @Test
  void recentLegacyRunningWithoutLeaseIsNotRecovered() {
    EasyCollectionRun legacy = EasyCollectionRun.builder()
        .connection(connection).business(business).triggerType("MANUAL")
        .adapterCode("TEST").adapterVersion("v0").build();
    ReflectionTestUtils.setField(legacy, "startedAt", LocalDateTime.now().minusMinutes(2));
    when(runs.findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
        7L, EasyCollectionRunStatus.RUNNING)).thenReturn(List.of(legacy));

    assertThatThrownBy(() -> gate.claim(7L, EasyCollectionTriggerType.MANUAL, "TEST", "v1"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(legacy.getStatus()).isEqualTo(EasyCollectionRunStatus.RUNNING);
  }

  @Test
  void oldLegacyRunningWithoutLeaseIsRecovered() {
    EasyCollectionRun legacy = EasyCollectionRun.builder()
        .connection(connection).business(business).triggerType("MANUAL")
        .adapterCode("TEST").adapterVersion("v0").build();
    ReflectionTestUtils.setField(legacy, "startedAt", LocalDateTime.now().minusMinutes(16));
    when(runs.findAllByConnection_ConnectionIdAndStatusOrderByRunIdAsc(
        7L, EasyCollectionRunStatus.RUNNING)).thenReturn(List.of(legacy));

    gate.claim(7L, EasyCollectionTriggerType.MANUAL, "TEST", "v1");

    assertThat(legacy.getStatus()).isEqualTo(EasyCollectionRunStatus.FAILED);
  }

  @Test
  void heartbeatExtendsOnlyMatchingOwner() {
    EasyCollectionRun run = runningRun("owner-a", LocalDateTime.now(), LocalDateTime.now());
    ReflectionTestUtils.setField(run, "runId", 9L);
    when(runs.findByIdWithLock(9L)).thenReturn(Optional.of(run));
    LocalDateTime before = run.getLeaseExpiresAt();

    gate.heartbeat(9L, 7L, "owner-a");

    assertThat(run.getHeartbeatAt()).isNotNull();
    assertThat(run.getLeaseExpiresAt()).isAfter(before);
  }

  @Test
  void heartbeatRejectsWrongOwnerAndTerminalRun() {
    EasyCollectionRun run = runningRun("owner-a", LocalDateTime.now(), LocalDateTime.now());
    ReflectionTestUtils.setField(run, "runId", 9L);
    when(runs.findByIdWithLock(9L)).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> gate.heartbeat(9L, 7L, "owner-b"))
        .isInstanceOf(IllegalStateException.class);

    run.fail("done");
    assertThatThrownBy(() -> gate.heartbeat(9L, 7L, "owner-a"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("terminal");
  }

  private EasyCollectionRun runningRun(String token, LocalDateTime acquired,
      LocalDateTime expires) {
    EasyCollectionRun run = EasyCollectionRun.builder()
        .connection(connection).business(business).triggerType("MANUAL")
        .adapterCode("TEST").adapterVersion("v1").build();
    run.claimLease(token, acquired, expires);
    return run;
  }
}
