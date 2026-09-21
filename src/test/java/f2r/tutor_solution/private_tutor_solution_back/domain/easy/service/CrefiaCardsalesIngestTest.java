package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionTriggerType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CrefiaCardsalesIngestTest {

  private static final Long BIZ = 1L;
  private final EasyBusinessRepository businessRepository = mock(EasyBusinessRepository.class);
  private final EasyCollectionSourceRepository sourceRepository =
      mock(EasyCollectionSourceRepository.class);
  private final EasyCollectionConnectionRepository connectionRepository =
      mock(EasyCollectionConnectionRepository.class);
  private final EasyCollectionRunRepository runRepository =
      mock(EasyCollectionRunRepository.class);
  private final EasyCollectionRawRecordRepository rawRepository =
      mock(EasyCollectionRawRecordRepository.class);
  private final EasyTransactionRepository txnRepository = mock(EasyTransactionRepository.class);
  private final EasyImportService importService = mock(EasyImportService.class);
  private final EasyCardsalesAdapter cardsalesAdapter = mock(EasyCardsalesAdapter.class);
  private final CrefiaCardsalesAdapter crefiaAdapter = mock(CrefiaCardsalesAdapter.class);

  private final Map<Long, EasyCollectionRun> runs = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(100L);
  private EasyCollectionConnection connection;
  private EasyCollectionIngestService service;

  @BeforeEach
  void setUp() {
    EasyBusiness business = EasyBusiness.builder().code("T1").name("테스트").active(true)
        .sortOrder(1).build();
    ReflectionTestUtils.setField(business, "businessId", BIZ);
    EasyCollectionSource source = EasyCollectionSource.builder()
        .code("CARDSALES").name("CREFIA").adapterType("OPENAPI").active(false).build();
    ReflectionTestUtils.setField(source, "sourceId", 10L);
    connection = EasyCollectionConnection.builder()
        .business(business).source(source).label("default").build();
    ReflectionTestUtils.setField(connection, "connectionId", 20L);
    ReflectionTestUtils.setField(connection, "connectionRef", "crefia:1234567890");

    when(businessRepository.findById(BIZ)).thenReturn(Optional.of(business));
    when(sourceRepository.findByCode("CARDSALES")).thenReturn(Optional.of(source));
    when(connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(
        BIZ, 10L, "default")).thenReturn(Optional.of(connection));
    when(connectionRepository.findByIdWithLock(20L)).thenReturn(Optional.of(connection));
    when(connectionRepository.findById(20L)).thenReturn(Optional.of(connection));
    when(runRepository.existsByConnection_ConnectionIdAndStatus(
        20L, EasyCollectionRunStatus.RUNNING)).thenReturn(false);
    when(runRepository.save(any(EasyCollectionRun.class))).thenAnswer(inv -> {
      EasyCollectionRun run = inv.getArgument(0);
      if (run.getRunId() == null) {
        ReflectionTestUtils.setField(run, "runId", ids.incrementAndGet());
      }
      runs.put(run.getRunId(), run);
      return run;
    });
    when(runRepository.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(runs.get(inv.getArgument(0))));
    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(BIZ))
        .thenReturn(List.of());
    when(crefiaAdapter.adapterCode()).thenReturn("CARDSALES");
    when(crefiaAdapter.adapterVersion()).thenReturn("test");
    when(crefiaAdapter.fetch(any(), any(), any(Integer.class))).thenReturn(List.of());

    service = new EasyCollectionIngestService(businessRepository, sourceRepository,
        connectionRepository, runRepository, rawRepository, txnRepository,
        new EasyCsvFileAdapter(), cardsalesAdapter, crefiaAdapter, importService);
  }

  private EasyCollectionIngestResponse collect(EasyCollectionTriggerType trigger) {
    return service.ingestByAdapter(BIZ, "default", "CARDSALES", trigger, null, 100);
  }

  @Test
  @DisplayName("successful fetch persists SUCCESS and MANUAL provenance")
  void success() {
    EasyCollectionIngestResponse result = collect(EasyCollectionTriggerType.MANUAL);
    assertThat(result.run().status()).isEqualTo("SUCCESS");
    assertThat(result.run().triggerType()).isEqualTo("MANUAL");
    assertThat(result.run().completedAt()).isNotNull();
    assertThat(connection.getLastSuccessAt()).isNotNull();
  }

  @Test
  @DisplayName("AUTH_REQUIRED persists FAILED and marks NEEDS_REAUTH")
  void authRequired() {
    when(crefiaAdapter.fetch(any(), any(), any(Integer.class))).thenThrow(
        new EasyCollectionException(EasyCollectionErrorCode.AUTH_REQUIRED,
            "Provider authentication is required."));
    EasyCollectionIngestResponse result = collect(EasyCollectionTriggerType.SCHEDULED);
    assertThat(result.run().status()).isEqualTo("FAILED");
    assertThat(result.run().triggerType()).isEqualTo("SCHEDULED");
    assertThat(result.run().errorSummary()).startsWith("AUTH_REQUIRED:");
    assertThat(connection.getStatus()).isEqualTo(EasyCollectionConnectionStatus.NEEDS_REAUTH);
  }

  @Test
  @DisplayName("temporary and invalid data failures do not mark NEEDS_REAUTH")
  void nonAuthFailures() {
    for (EasyCollectionErrorCode code : List.of(
        EasyCollectionErrorCode.PROVIDER_TEMPORARY, EasyCollectionErrorCode.INVALID_DATA)) {
      ReflectionTestUtils.setField(connection, "status", EasyCollectionConnectionStatus.ACTIVE);
      doThrow(new EasyCollectionException(code, "Safe provider failure."))
          .when(crefiaAdapter).fetch(any(), any(), any(Integer.class));
      EasyCollectionIngestResponse result = collect(EasyCollectionTriggerType.MANUAL);
      assertThat(result.run().status()).isEqualTo("FAILED");
      assertThat(result.run().errorSummary()).startsWith(code.name() + ":");
      assertThat(connection.getStatus()).isEqualTo(EasyCollectionConnectionStatus.ACTIVE);
    }
  }

  @Test
  @DisplayName("adapter fetch executes without an active Spring transaction")
  void fetchOutsideTransaction() {
    AtomicReference<Boolean> active = new AtomicReference<>(true);
    when(crefiaAdapter.fetch(any(), any(), any(Integer.class))).thenAnswer(inv -> {
      active.set(TransactionSynchronizationManager.isActualTransactionActive());
      return new ArrayList<CollectionParsedRow>();
    });
    collect(EasyCollectionTriggerType.MANUAL);
    assertThat(active.get()).isFalse();
  }

  @Test
  @DisplayName("unverified CREFIA adapter fails closed before network or credential access")
  void crefiaGuard() {
    CrefiaCardsalesClient client = mock(CrefiaCardsalesClient.class);
    CrefiaCredentialProvider credentials = mock(CrefiaCredentialProvider.class);
    CrefiaCardsalesAdapter guarded = new CrefiaCardsalesAdapter(client, credentials);

    assertThatThrownBy(() -> guarded.fetch("crefia:1234567890", null, 100))
        .isInstanceOf(EasyCollectionException.class)
        .extracting(e -> ((EasyCollectionException) e).errorCode())
        .isEqualTo(EasyCollectionErrorCode.CONFIGURATION_ERROR);
    verifyNoInteractions(client, credentials);
  }
}
