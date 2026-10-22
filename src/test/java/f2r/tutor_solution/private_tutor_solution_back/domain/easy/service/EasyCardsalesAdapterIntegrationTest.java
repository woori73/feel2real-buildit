package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportRowResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionTriggerType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportRowStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyAccountMappingRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportBatchRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyImportRowRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService.ClassificationMatch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

/**
 * Cardsales 어댑터 통합 검증 — PHASE 2 준비②.
 * <ul>
 *   <li>ingestParsedRows 공통 코어를 통한 staging handoff 검증</li>
 *   <li>동일 record 재수집 idempotency (EXT 키 기반)</li>
 *   <li>서로 다른 connectionRef 간 false merge 방지</li>
 *   <li>auto-confirm 없음: 트랜잭션 직접 INSERT 금지</li>
 *   <li>raw ↔ import row ↔ (나중 transaction) 역추적 체인 유지</li>
 * </ul>
 */
class EasyCardsalesAdapterIntegrationTest {

  private static final Long BIZ = 1L;
  private static final String CONN_REF_A = "cardsales-connected-account-A";
  private static final String CONN_REF_B = "cardsales-connected-account-B";

  private final EasyBusinessRepository businessRepository =
      mock(EasyBusinessRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionSourceRepository sourceRepository =
      mock(EasyCollectionSourceRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionConnectionRepository connectionRepository =
      mock(EasyCollectionConnectionRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionRunRepository runRepository =
      mock(EasyCollectionRunRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionRawRecordRepository rawRepository =
      mock(EasyCollectionRawRecordRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyImportBatchRepository batchRepository =
      mock(EasyImportBatchRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyImportRowRepository rowRepository =
      mock(EasyImportRowRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyTransactionRepository txnRepository =
      mock(EasyTransactionRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyAccountService accountService =
      mock(EasyAccountService.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyJournalService journalService =
      mock(EasyJournalService.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyAccountMappingRepository mappingRepository =
      mock(EasyAccountMappingRepository.class, withSettings().strictness(Strictness.LENIENT));

  private final Map<Long, EasyBusiness> businesses = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionSource> sources = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionConnection> connections = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRun> runs = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRawRecord> raws = new LinkedHashMap<>();
  private final Map<Long, EasyImportBatch> batches = new LinkedHashMap<>();
  private final Map<Long, EasyImportRow> rows = new LinkedHashMap<>();
  private final Map<Long, EasyTransaction> txns = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(20000L);

  private EasyCollectionIngestService ingestService;
  private EasyImportService importService;
  private EasyCardsalesAdapter adapter;

  @BeforeEach
  void setUp() {
    EasyBusiness business = EasyBusiness.builder().code("T1").name("테스트학원").active(true).sortOrder(1).build();
    ReflectionTestUtils.setField(business, "businessId", BIZ);
    businesses.put(BIZ, business);

    EasyCollectionSource cardsalesSource = EasyCollectionSource.builder()
        .code("CARDSales").name("카드사 승인/매입/입금").adapterType("CARDSales").active(true).build();
    ReflectionTestUtils.setField(cardsalesSource, "sourceId", 2L);
    sources.put(2L, cardsalesSource);

    when(businessRepository.findById(BIZ)).thenReturn(Optional.of(business));
    when(businessRepository.findAll()).thenReturn(List.of(business));

    when(sourceRepository.findByCode(anyString())).thenAnswer(inv -> sources.values().stream()
        .filter(s -> s.getCode().equals(inv.getArgument(0))).findFirst());
    when(sourceRepository.findAll()).thenAnswer(inv -> new ArrayList<>(sources.values()));

    when(connectionRepository.save(any(EasyCollectionConnection.class))).thenAnswer(inv -> {
      EasyCollectionConnection c = inv.getArgument(0);
      ReflectionTestUtils.setField(c, "connectionId", ids.incrementAndGet());
      connections.put(c.getConnectionId(), c);
      return c;
    });
    when(connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(anyLong(), anyLong(), anyString()))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && c.getSource().getSourceId().equals(inv.getArgument(1))
                && c.getLabel().equals(inv.getArgument(2)))
            .findFirst());
    when(connectionRepository.findByIdWithLock(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));
    when(connectionRepository.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));

    when(runRepository.save(any(EasyCollectionRun.class))).thenAnswer(inv -> {
      EasyCollectionRun r = inv.getArgument(0);
      if (r.getRunId() == null) {
        ReflectionTestUtils.setField(r, "runId", ids.incrementAndGet());
      }
      runs.put(r.getRunId(), r);
      return r;
    });
    when(runRepository.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(runs.get(inv.getArgument(0))));

    when(rawRepository.save(any(EasyCollectionRawRecord.class))).thenAnswer(inv -> {
      EasyCollectionRawRecord raw = inv.getArgument(0);
      boolean collide = raws.values().stream()
          .anyMatch(x -> x.getDedupeKey().equals(raw.getDedupeKey()));
      if (collide) {
        throw new IllegalStateException("dedupe_key UNIQUE 충돌: " + raw.getDedupeKey());
      }
      ReflectionTestUtils.setField(raw, "rawId", ids.incrementAndGet());
      raws.put(raw.getRawId(), raw);
      return raw;
    });
    when(rawRepository.findByDedupeKey(anyString())).thenAnswer(inv -> raws.values().stream()
        .filter(r -> r.getDedupeKey().equals(inv.getArgument(0))).findFirst());

    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(anyLong()))
        .thenAnswer(inv -> txns.values().stream()
            .filter(t -> t.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && t.getFingerprint() != null).toList());

    when(batchRepository.save(any(EasyImportBatch.class))).thenAnswer(inv -> {
      EasyImportBatch batch = inv.getArgument(0);
      ReflectionTestUtils.setField(batch, "batchId", ids.incrementAndGet());
      batches.put(batch.getBatchId(), batch);
      return batch;
    });
    when(batchRepository.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(batches.get(inv.getArgument(0))));

    when(rowRepository.save(any(EasyImportRow.class))).thenAnswer(inv -> {
      EasyImportRow row = inv.getArgument(0);
      if (row.getRowId() == null) {
        ReflectionTestUtils.setField(row, "rowId", ids.incrementAndGet());
      }
      rows.put(row.getRowId(), row);
      return row;
    });
    when(rowRepository.findByBatch_BatchIdOrderByRowNoAsc(anyLong())).thenAnswer(inv -> rows.values()
        .stream().filter(r -> r.getBatch() != null
            && r.getBatch().getBatchId().equals(inv.getArgument(0)))
        .sorted(Comparator.comparingInt(EasyImportRow::getRowNo)).toList());
    when(rowRepository.findByBatch_BatchIdAndStatusOrderByRowNoAsc(anyLong(), any())).thenAnswer(inv -> rows.values().stream()
            .filter(r -> r.getBatch() != null
                && r.getBatch().getBatchId().equals(inv.getArgument(0))
                && r.getStatus() == inv.getArgument(1))
            .sorted(Comparator.comparingInt(EasyImportRow::getRowNo)).toList());

    when(accountService.classify(anyString(), anyString(), anyLong(), any()))
        .thenReturn(Optional.empty());
    when(mappingRepository.findFirstByExternalCode(anyString()))
        .thenReturn(Optional.empty());

    adapter = new EasyCardsalesAdapter();
    importService = new EasyImportService(
        batchRepository, rowRepository, businessRepository, txnRepository, accountService,
        mappingRepository, journalService,
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository.class),
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository.class),
        org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService.class));
    ingestService = new EasyCollectionIngestService(businessRepository, sourceRepository,
        connectionRepository, runRepository, rawRepository, txnRepository,
        new EasyCsvFileAdapter(), adapter, new CrefiaCardsalesAdapter(), importService);
  }

  private void setupConnection(String label, String connectionRef, Long connectionId) {
    EasyCollectionConnection conn = EasyCollectionConnection.builder()
        .business(businesses.get(BIZ))
        .source(sources.get(2L))
        .label(label)
        .build();
    ReflectionTestUtils.setField(conn, "connectionId", connectionId);
    ReflectionTestUtils.setField(conn, "connectionRef", connectionRef);
    connections.put(connectionId, conn);
    when(connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(BIZ, 2L, label))
        .thenReturn(Optional.of(conn));
  }

  @Test
  @DisplayName("통합: Cardsales 어댑터 → ingestParsedRows → import staging handoff 성공")
  void adapterToIngestToStagingHandoff() {
    setupConnection("default", CONN_REF_A, 200L);

    EasyCollectionIngestResponse response = ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);

    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(response.run().fetchedCount()).isEqualTo(6);
    assertThat(response.run().acceptedCount()).isEqualTo(6);
    assertThat(response.run().duplicateCount()).isZero();
    assertThat(response.run().importBatchId()).isNotNull();

    Long batchId = response.run().importBatchId();
    assertThat(raws.values()).hasSize(6);
    assertThat(raws.values()).allSatisfy(raw -> {
      assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.IMPORT_LINKED);
      assertThat(raw.getImportBatchId()).isEqualTo(batchId);
      assertThat(raw.getImportRowId()).isNotNull();
      assertThat(raw.getConnection()).isNotNull();
      assertThat(raw.getConnection().getConnectionRef()).isEqualTo(CONN_REF_A);
      assertThat(raw.getDedupeKey()).startsWith("CARDSales:");
    });
  }

  @Test
  @DisplayName("통합: 동일 connectionRef 재수집 시 전건 중복(idempotency)")
  void sameConnectionRefReingest_allDuplicate() {
    setupConnection("default", CONN_REF_A, 200L);

    EasyCollectionIngestResponse first = ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);
    assertThat(first.run().acceptedCount()).isEqualTo(6);
    Long firstBatchId = first.run().importBatchId();

    EasyCollectionIngestResponse second = ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);
    assertThat(second.run().status()).isEqualTo("SUCCESS");
    assertThat(second.run().acceptedCount()).isZero();
    assertThat(second.run().duplicateCount()).isEqualTo(6);
    assertThat(second.run().importBatchId()).isNull();
    assertThat(batches).hasSize(1);
    assertThat(batches.keySet()).containsExactly(firstBatchId);
  }

  @Test
  @DisplayName("통합: 서로 다른 connectionRef 간 false merge 방지")
  void differentConnectionRef_noFalseMerge() {
    setupConnection("account-A", CONN_REF_A, 200L);
    setupConnection("account-B", CONN_REF_B, 201L);

    EasyCollectionIngestResponse responseA = ingestService.ingestByAdapter(BIZ, "account-A", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);
    assertThat(responseA.run().acceptedCount()).isEqualTo(6);
    Long batchIdA = responseA.run().importBatchId();

    EasyCollectionIngestResponse responseB = ingestService.ingestByAdapter(BIZ, "account-B", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);
    assertThat(responseB.run().acceptedCount()).isEqualTo(6);
    assertThat(responseB.run().importBatchId()).isNotNull();
    assertThat(responseB.run().importBatchId()).isNotEqualTo(batchIdA);

    List<EasyCollectionRawRecord> allRaw = new ArrayList<>(raws.values());
    assertThat(allRaw).hasSize(12);
    long countA = allRaw.stream().filter(r -> r.getConnection().getConnectionRef().equals(CONN_REF_A)).count();
    long countB = allRaw.stream().filter(r -> r.getConnection().getConnectionRef().equals(CONN_REF_B)).count();
    assertThat(countA).isEqualTo(6);
    assertThat(countB).isEqualTo(6);
  }

  @Test
  @DisplayName("통합: auto-confirm 없음 — 트랜잭션 직접 INSERT 금지 검증")
  void noAutoConfirm_noDirectTransactionInsert() {
    setupConnection("default", CONN_REF_A, 200L);

    ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);

    assertThat(txns).isEmpty();
  }

  @Test
  @DisplayName("통합: raw ↔ import row 역추적 체인 — dedupeKey 저장 및 import 링크")
  void rawImportRowTraceChain() {
    setupConnection("default", CONN_REF_A, 200L);

    EasyCollectionIngestResponse response = ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);
    Long batchId = response.run().importBatchId();

    assertThat(raws.values()).allSatisfy(raw -> {
      assertThat(raw.getDedupeKey()).isNotBlank();
      assertThat(raw.getDedupeKey()).startsWith("CARDSales:1:200:EXT:");
      assertThat(raw.getImportBatchId()).isEqualTo(batchId);
      assertThat(raw.getImportRowId()).isNotNull();
      assertThat(raw.getConnection().getConnectionRef()).isEqualTo(CONN_REF_A);
    });
  }

  @Test
  @DisplayName("통합: externalId 기반 1차 dedupe(EXT 키) — 동일 externalId 재수집 시 중복")
  void externalIdBasedDedupe() {
    setupConnection("default", CONN_REF_A, 200L);

    ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);
    int rawCountAfterFirst = raws.size();

    ingestService.ingestByAdapter(BIZ, "default", "CARDSales", EasyCollectionTriggerType.MANUAL, null, 5000);

    assertThat(raws.size()).isEqualTo(rawCountAfterFirst);
    assertThat(raws.values()).allSatisfy(raw -> 
        assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.IMPORT_LINKED));
  }
}