package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

class EasyCollectionSchedulerServiceTest {

  private static final Long BIZ = 1L;
  private static final String CONN_REF = "cardsales-connected-account-001";

  private final EasyBusinessRepository businessRepository =
      mock(EasyBusinessRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionSourceRepository sourceRepository =
      mock(EasyCollectionSourceRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionConnectionRepository connectionRepository =
      mock(EasyCollectionConnectionRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionRunRepository runRepository =
      mock(EasyCollectionRunRepository.class, withSettings().strictness(Strictness.LENIENT));

  private final EasyCollectionIngestService ingestService =
      mock(EasyCollectionIngestService.class, withSettings().strictness(Strictness.LENIENT));

  private final Map<Long, EasyBusiness> businesses = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionSource> sources = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionConnection> connections = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRun> runs = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(10000L);

  private EasyCollectionSchedulerService schedulerService;

  @BeforeEach
  void setUp() {
    EasyBusiness business = EasyBusiness.builder().code("T1").name("테스트학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(business, "businessId", BIZ);
    businesses.put(BIZ, business);

    EasyCollectionSource cardsalesSource = EasyCollectionSource.builder()
        .code("CARDSales").name("카드사 승인/매입/입금").adapterType("OPENAPI").active(true).build();
    ReflectionTestUtils.setField(cardsalesSource, "sourceId", 2L);
    sources.put(2L, cardsalesSource);

    when(businessRepository.findById(BIZ)).thenReturn(Optional.of(business));
    when(sourceRepository.findAllByActiveTrue()).thenReturn(List.of(cardsalesSource));
    when(sourceRepository.findByCode("CARDSales")).thenReturn(Optional.of(cardsalesSource));

    when(connectionRepository.save(any(EasyCollectionConnection.class))).thenAnswer(inv -> {
      EasyCollectionConnection c = inv.getArgument(0);
      ReflectionTestUtils.setField(c, "connectionId", ids.incrementAndGet());
      connections.put(c.getConnectionId(), c);
      return c;
    });
    when(connectionRepository.findBySource_SourceIdAndStatus(2L, EasyCollectionConnectionStatus.ACTIVE))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getSource().getSourceId() == 2L
                && c.getStatus() == EasyCollectionConnectionStatus.ACTIVE)
            .toList());
    when(connectionRepository.findByBusiness_BusinessId(BIZ))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getBusiness().getBusinessId().equals(BIZ))
            .toList());
    when(connectionRepository.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));

    when(runRepository.save(any(EasyCollectionRun.class))).thenAnswer(inv -> {
      EasyCollectionRun r = inv.getArgument(0);
      ReflectionTestUtils.setField(r, "runId", ids.incrementAndGet());
      runs.put(r.getRunId(), r);
      return r;
    });
    when(runRepository.existsByConnection_ConnectionIdAndStatus(anyLong(), any()))
        .thenReturn(false);
    when(runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(anyLong()))
        .thenReturn(Optional.empty());

    EasyCollectionRunResponse mockRunResponse = new EasyCollectionRunResponse(
        999L, 200L, BIZ, "CARDSales", "SCHEDULED", "SUCCESS",
        "CARDSales-2026-09-20", "CARDSales", "v1-skeleton",
        6, 6, 0, 0, null, null,
        LocalDateTime.now(), LocalDateTime.now());
    when(ingestService.ingestByAdapter(anyLong(), anyString(), anyString(), any(), anyInt()))
        .thenReturn(new EasyCollectionIngestResponse(mockRunResponse, List.of()));

    schedulerService = new EasyCollectionSchedulerService(
        businessRepository, sourceRepository, connectionRepository, runRepository, ingestService);
  }

  private void setupConnection(String label, String connectionRef, EasyCollectionConnectionStatus status, Long connectionId) {
    EasyCollectionConnection conn = EasyCollectionConnection.builder()
        .business(businesses.get(BIZ))
        .source(sources.get(2L))
        .label(label)
        .build();
    ReflectionTestUtils.setField(conn, "connectionId", connectionId);
    ReflectionTestUtils.setField(conn, "connectionRef", connectionRef);
    conn.updateConnectionRef(connectionRef);
    if (status == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      conn.markNeedsReauth();
    } else if (status == EasyCollectionConnectionStatus.DISABLED) {
      conn.disable();
    }
    connections.put(connectionId, conn);
    when(connectionRepository.findBySource_SourceIdAndStatus(2L, EasyCollectionConnectionStatus.ACTIVE))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getSource().getSourceId() == 2L
                && c.getStatus() == EasyCollectionConnectionStatus.ACTIVE)
            .toList());
    when(connectionRepository.findByBusiness_BusinessId(BIZ))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getBusiness().getBusinessId().equals(BIZ))
            .toList());
  }

  @Test
  @DisplayName("수동 트리거 — 즉시 수집 실행, triggerType=MANUAL")
  void triggerManualCollection_success() {
    setupConnection("default", CONN_REF, EasyCollectionConnectionStatus.ACTIVE, 200L);

    EasyCollectionRunResponse run = schedulerService.triggerManualCollection(BIZ, 200L);

    assertThat(run).isNotNull();
    assertThat(run.status()).isEqualTo("SUCCESS");
    verify(ingestService).ingestByAdapter(BIZ, "default", "CARDSales", null, 5000);
  }

  @Test
  @DisplayName("수동 트리거 — connectionRef 없으면 예외")
  void triggerManualConnection_missingRef_throws() {
    EasyCollectionConnection conn = EasyCollectionConnection.builder()
        .business(businesses.get(BIZ))
        .source(sources.get(2L))
        .label("default")
        .build();
    ReflectionTestUtils.setField(conn, "connectionId", 200L);
    conn.updateConnectionRef("");
    connections.put(200L, conn);

    assertThatThrownBy(() -> schedulerService.triggerManualCollection(BIZ, 200L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("connectionRef가 설정되지 않았습니다");
  }

  @Test
  @DisplayName("수동 트리거 — NEEDS_REAUTH 상태면 예외")
  void triggerManualConnection_needsReauth_throws() {
    setupConnection("default", CONN_REF, EasyCollectionConnectionStatus.NEEDS_REAUTH, 200L);

    assertThatThrownBy(() -> schedulerService.triggerManualCollection(BIZ, 200L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("재인증 필요");
  }

  @Test
  @DisplayName("수동 트리거 — 이미 실행 중인 run 있으면 예외")
  void triggerManualConnection_alreadyRunning_throws() {
    setupConnection("default", CONN_REF, EasyCollectionConnectionStatus.ACTIVE, 200L);
    when(runRepository.existsByConnection_ConnectionIdAndStatus(200L, EasyCollectionRunStatus.RUNNING))
        .thenReturn(true);

    assertThatThrownBy(() -> schedulerService.triggerManualCollection(BIZ, 200L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("이미 실행 중");
  }

  @Test
  @DisplayName("자동 크론 — ACTIVE 연결만 순차 실행, NEEDS_REAUTH/DISABLED 건너뜀")
  void runScheduledCollections_skipsNonActive() {
    setupConnection("account-A", CONN_REF, EasyCollectionConnectionStatus.ACTIVE, 200L);
    setupConnection("account-B", "ref-B", EasyCollectionConnectionStatus.NEEDS_REAUTH, 201L);
    setupConnection("account-C", "ref-C", EasyCollectionConnectionStatus.DISABLED, 202L);

    schedulerService.runScheduledCollections();

    verify(ingestService).ingestByAdapter(BIZ, "account-A", "CARDSales", null, 5000);
    verify(ingestService, never()).ingestByAdapter(anyLong(), eq("account-B"), anyString(), any(), anyInt());
    verify(ingestService, never()).ingestByAdapter(anyLong(), eq("account-C"), anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("자동 크론 — connectionRef 없으면 건너뜀")
  void runScheduledCollections_skipsMissingRef() {
    EasyCollectionConnection conn = EasyCollectionConnection.builder()
        .business(businesses.get(BIZ))
        .source(sources.get(2L))
        .label("no-ref")
        .build();
    ReflectionTestUtils.setField(conn, "connectionId", 203L);
    conn.updateConnectionRef("");
    connections.put(203L, conn);
    when(connectionRepository.findBySource_SourceIdAndStatus(2L, EasyCollectionConnectionStatus.ACTIVE))
        .thenReturn(List.of(conn));

    schedulerService.runScheduledCollections();

    verify(ingestService, never()).ingestByAdapter(anyLong(), eq("no-ref"), anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("자동 크론 — 이전 run이 RUNNING이면 건너뜀")
  void runScheduledCollections_skipsRunningRun() {
    setupConnection("default", CONN_REF, EasyCollectionConnectionStatus.ACTIVE, 200L);
    when(runRepository.existsByConnection_ConnectionIdAndStatus(200L, EasyCollectionRunStatus.RUNNING))
        .thenReturn(true);

    schedulerService.runScheduledCollections();

    verify(ingestService, never()).ingestByAdapter(anyLong(), anyString(), anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("자동 크론 — 401/인증 에러 시 연결을 NEEDS_REAUTH로 변경")
  void runScheduledCollections_authError_marksNeedsReauth() {
    setupConnection("default", CONN_REF, EasyCollectionConnectionStatus.ACTIVE, 200L);
    when(ingestService.ingestByAdapter(anyLong(), anyString(), anyString(), any(), anyInt()))
        .thenThrow(new IllegalStateException("401 Unauthorized: 인증 실패"));

    schedulerService.runScheduledCollections();

    EasyCollectionConnection conn = connections.get(200L);
    assertThat(conn.getStatus()).isEqualTo(EasyCollectionConnectionStatus.NEEDS_REAUTH);
  }

  @Test
  @DisplayName("연결 상태 요약 — 최신 run 상태와 카운트 포함")
  void getConnectionStatusSummary_includesLatestRun() {
    setupConnection("default", CONN_REF, EasyCollectionConnectionStatus.ACTIVE, 200L);
    EasyCollectionRun latest = EasyCollectionRun.builder()
        .connection(connections.get(200L))
        .business(businesses.get(BIZ))
        .triggerType("SCHEDULED")
        .sourceFilename("CARDSales-2026-09-20")
        .adapterCode("CARDSales")
        .adapterVersion("v1-skeleton")
        .build();
    ReflectionTestUtils.setField(latest, "runId", 300L);
    ReflectionTestUtils.setField(latest, "status", EasyCollectionRunStatus.SUCCESS);
    ReflectionTestUtils.setField(latest, "acceptedCount", 6);
    ReflectionTestUtils.setField(latest, "duplicateCount", 0);
    ReflectionTestUtils.setField(latest, "errorCount", 0);
    runs.put(300L, latest);
    when(runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(200L))
        .thenReturn(Optional.of(latest));

    List<EasyCollectionSchedulerService.ConnectionStatusSummary> summaries =
        schedulerService.getConnectionStatusSummary(BIZ);

    assertThat(summaries).hasSize(1);
    EasyCollectionSchedulerService.ConnectionStatusSummary s = summaries.get(0);
    assertThat(s.connectionId()).isEqualTo(200L);
    assertThat(s.status()).isEqualTo("ACTIVE");
    assertThat(s.latestRunStatus()).isEqualTo("SUCCESS");
    assertThat(s.latestAcceptedCount()).isEqualTo(6);
    assertThat(s.latestDuplicateCount()).isZero();
    assertThat(s.latestErrorCount()).isZero();
    assertThat(s.hasConnectionRef()).isTrue();
  }
}