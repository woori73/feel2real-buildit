package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionTriggerType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 매통조 어댑터 → 공통 수집 코어 → import staging 관통 검증.
 * 저장소는 mock 인메모리 맵, provider는 mock client. 실제 HTTP 호출 없음.
 * <ul>
 *   <li>수집 → dedupe → staging handoff, 거래 중복 생성 0 보장</li>
 *   <li>동일 데이터 재수집 시 전량 중복 제외 (EXT 키)</li>
 *   <li>장부 fingerprint(IMPORTED 보호): 확정 거래와 겹치면 제외</li>
 *   <li>auto-confirm 없음: easy_transactions INSERT 발생하지 않음</li>
 * </ul>
 */
class CrefiaCardsalesIngestTest {

  private static final Long BIZ = 1L;

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
  private final EasyTransactionRepository txnRepository =
      mock(EasyTransactionRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyImportService importService =
      mock(EasyImportService.class, withSettings().strictness(Strictness.LENIENT));

  private final Map<Long, EasyCollectionConnection> connections = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRun> runs = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRawRecord> raws = new LinkedHashMap<>();
  private final List<EasyTransaction> ledger = new ArrayList<>();
  private final AtomicLong ids = new AtomicLong(30000L);

  private CrefiaCardsalesClient client;
  private EasyCollectionIngestService ingestService;

  @BeforeEach
  void setUp() {
    EasyBusiness business = EasyBusiness.builder().code("T1").name("테스트학원").active(true)
        .sortOrder(1).build();
    ReflectionTestUtils.setField(business, "businessId", BIZ);
    when(businessRepository.findById(BIZ)).thenReturn(Optional.of(business));

    EasyCollectionSource source = EasyCollectionSource.builder()
        .code("CARDSALES").name("여신금융협회 카드매출(매통조 OpenAPI)")
        .adapterType("OPENAPI").active(false).build();
    ReflectionTestUtils.setField(source, "sourceId", 10L);
    when(sourceRepository.findByCode("CARDSALES")).thenReturn(Optional.of(source));

    when(connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(
        anyLong(), anyLong(), anyString()))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && c.getSource().getSourceId().equals(inv.getArgument(1))
                && c.getLabel().equals(inv.getArgument(2)))
            .findFirst());
    when(connectionRepository.save(any(EasyCollectionConnection.class))).thenAnswer(inv -> {
      EasyCollectionConnection c = inv.getArgument(0);
      if (c.getConnectionId() == null) {
        ReflectionTestUtils.setField(c, "connectionId", ids.incrementAndGet());
      }
      connections.put(c.getConnectionId(), c);
      return c;
    });
    when(connectionRepository.findByIdWithLock(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));

    when(runRepository.save(any(EasyCollectionRun.class))).thenAnswer(inv -> {
      EasyCollectionRun r = inv.getArgument(0);
      if (r.getRunId() == null) {
        ReflectionTestUtils.setField(r, "runId", ids.incrementAndGet());
      }
      runs.put(r.getRunId(), r);
      return r;
    });
    when(rawRepository.save(any(EasyCollectionRawRecord.class))).thenAnswer(inv -> {
      EasyCollectionRawRecord raw = inv.getArgument(0);
      ReflectionTestUtils.setField(raw, "rawId", ids.incrementAndGet());
      raws.put(raw.getRawId(), raw);
      return raw;
    });
    when(rawRepository.findByDedupeKey(anyString())).thenAnswer(inv -> raws.values().stream()
        .filter(r -> r.getDedupeKey().equals(inv.getArgument(0))).findFirst());
    when(txnRepository.findByBusiness_BusinessIdAndFingerprintIsNotNull(anyLong()))
        .thenAnswer(inv -> new ArrayList<>(ledger));

    when(importService.stage(any(EasyImportBatchRequest.class))).thenAnswer(inv -> {
      EasyImportBatchRequest req = inv.getArgument(0);
      List<EasyImportRowResponse> rows = new ArrayList<>();
      for (int i = 0; i < req.rows().size(); i++) {
        rows.add(new EasyImportRowResponse(ids.incrementAndGet(), i + 1,
            null, null, null, null, null, null, null, null, null, null,
            "READY", null, null, null, null, null));
      }
      return new EasyImportBatchResponse(ids.incrementAndGet(), BIZ, "테스트학원",
          req.sourceName(), req.sourceKind(), "STAGED", rows.size(), 0, rows);
    });

    client = mock(CrefiaCardsalesClient.class);
    when(client.fetchApprovals(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("승인번호", "AP123", "승인금액", "100000", "거래일자", "20260901",
            "가맹점명", "압구정에듀홀릭")));
    when(client.fetchPurchases(any(), any(), anyInt())).thenReturn(List.of(
        Map.of("가맹점번호", "M001", "승인번호", "AP123", "매출금액", "100000",
            "수수료", "2000", "지급금액", "98000", "거래일자", "20260901",
            "매입일자", "20260902", "카드사명", "신한카드")));
    when(client.fetchDeposits(any(), any(), anyInt())).thenReturn(List.of());

    CrefiaCardsalesAdapter crefia = new CrefiaCardsalesAdapter(client,
        () -> new CrefiaCredentialProvider.Credentials("id", "secret"));
    ingestService = new EasyCollectionIngestService(businessRepository, sourceRepository,
        connectionRepository, runRepository, rawRepository, txnRepository,
        new EasyCsvFileAdapter(), new EasyCardsalesAdapter(), crefia, importService);
  }

  private void setupConnection(String label, String connectionRef, Long connectionId) {
    EasyCollectionSource source = sourceRepository.findByCode("CARDSALES").orElseThrow();
    EasyBusiness business = businessRepository.findById(BIZ).orElseThrow();
    EasyCollectionConnection conn = EasyCollectionConnection.builder()
        .business(business).source(source).label(label).build();
    ReflectionTestUtils.setField(conn, "connectionId", connectionId);
    ReflectionTestUtils.setField(conn, "connectionRef", connectionRef);
    connections.put(connectionId, conn);
  }

  @Test
  @DisplayName("관통: 매통조 수집 → staging handoff, 확정 거래 생성 없음")
  void ingestToStaging() {
    setupConnection("default", "crefia:1234567890", 300L);

    EasyCollectionIngestResponse response =
        ingestService.ingestByAdapter(BIZ, "default", "CARDSALES", EasyCollectionTriggerType.MANUAL, null, 5000);

    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(response.run().fetchedCount()).isEqualTo(2);
    assertThat(response.run().acceptedCount()).isEqualTo(2);
    assertThat(response.run().duplicateCount()).isZero();
    assertThat(raws.values()).allSatisfy(raw ->
        assertThat(raw.getDedupeKey()).startsWith("CARDSALES:"));
    verify(importService).stage(any(EasyImportBatchRequest.class));
    // auto-confirm 없음 — 장부에 직접 INSERT하지 않는다
    verify(txnRepository, never()).save(any(EasyTransaction.class));
  }

  @Test
  @DisplayName("중복 수집 방지: 동일 데이터 재수집 시 전량 중복 제외, 분개·원장 추가 없음")
  void duplicateFetchExcluded() {
    setupConnection("default", "crefia:1234567890", 300L);

    EasyCollectionIngestResponse first =
        ingestService.ingestByAdapter(BIZ, "default", "CARDSALES", EasyCollectionTriggerType.MANUAL, null, 5000);
    EasyCollectionIngestResponse second =
        ingestService.ingestByAdapter(BIZ, "default", "CARDSALES", EasyCollectionTriggerType.MANUAL, null, 5000);

    assertThat(first.run().acceptedCount()).isEqualTo(2);
    assertThat(second.run().acceptedCount()).isZero();
    assertThat(second.run().duplicateCount()).isEqualTo(2);
    assertThat(raws).hasSize(2);
  }

  @Test
  @DisplayName("IMPORTED 보호: 장부 확정 거래와 겹치는 수집 행은 제외")
  void ledgerFingerprintProtected() {
    setupConnection("default", "crefia:1234567890", 300L);

    // 장부에 이미 확정된 거래 1건 (첫 승인 행과 동일 fingerprint)
    CrefiaCardsalesAdapter adapter = new CrefiaCardsalesAdapter(client,
        () -> new CrefiaCredentialProvider.Credentials("id", "secret"));
    List<CollectionParsedRow> rows = adapter.fetch("crefia:1234567890", null, 5000);
    CollectionParsedRow first = rows.get(0);
    String fingerprint = EasyFingerprint.of(BIZ, first.occurredOn(), first.direction(),
        first.amount(), first.counterparty(), first.description(), first.payMethod());
    EasyTransaction confirmed = mock(EasyTransaction.class);
    when(confirmed.getFingerprint()).thenReturn(fingerprint);
    ledger.add(confirmed);

    EasyCollectionIngestResponse response =
        ingestService.ingestByAdapter(BIZ, "default", "CARDSALES", EasyCollectionTriggerType.MANUAL, null, 5000);

    assertThat(response.run().fetchedCount()).isEqualTo(2);
    assertThat(response.run().acceptedCount()).isEqualTo(1);
    assertThat(response.run().duplicateCount()).isEqualTo(1);
  }
}
