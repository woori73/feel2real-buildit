package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionCheckpoint;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionDatasetType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionTriggerType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEvidenceReview;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTaxClassification;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionCheckpointRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 운영 원칙 "수집 → 원본 보존 → 최소 중복 방지 → 중앙 장부 저장 → 자동분류 → 예외 검토" 검증.
 * 모든 데이터는 합성(synthetic)이다. 실제 금융자료 수집 결과가 아니다.
 * DB 대신 Map 하네스 + 실제 EasyImportService/EasyJournalService를 쓰고, 트랜잭션 롤백은 스냅샷으로 모사한다.
 */
class EasyCollectionLedgerSaveTest {

  private static final Long BIZ = 1L;
  private static final Long OTHER_BIZ = 2L;
  private static final String HEADER = "거래일,구분,금액,거래처,적요,결제수단,승인번호";
  private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

  private final EasyShopPipelineHarness pipeline = new EasyShopPipelineHarness();
  private final EasyCollectionSourceRepository sourceRepository = lenient(EasyCollectionSourceRepository.class);
  private final EasyCollectionConnectionRepository connectionRepository = lenient(EasyCollectionConnectionRepository.class);
  private final EasyCollectionRunRepository runRepository = lenient(EasyCollectionRunRepository.class);
  private final EasyCollectionRawRecordRepository rawRepository = lenient(EasyCollectionRawRecordRepository.class);
  private final EasyCollectionCheckpointRepository checkpointRepository = lenient(EasyCollectionCheckpointRepository.class);

  private final Map<Long, EasyCollectionSource> sources = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionConnection> connections = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRun> runs = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRawRecord> raws = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionCheckpoint> checkpoints = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(9000L);

  private final FakeBankAdapter bank = new FakeBankAdapter();
  private EasyCollectionIngestService service;

  private static <T> T lenient(Class<T> type) {
    return mock(type, withSettings().strictness(Strictness.LENIENT));
  }

  @BeforeEach
  void setUp() {
    pipeline.seedBusiness(BIZ, "ACADEMY", "테스트학원");
    pipeline.seedBusiness(OTHER_BIZ, "PUBLISHING", "테스트출판");
    addSource(1L, "EXCEL_FILE");
    addSource(2L, FakeBankAdapter.CODE);

    when(sourceRepository.findByCode(any())).thenAnswer(inv -> sources.values().stream()
        .filter(s -> s.getCode().equals(inv.getArgument(0))).findFirst());
    when(connectionRepository.save(any(EasyCollectionConnection.class))).thenAnswer(inv -> {
      EasyCollectionConnection c = inv.getArgument(0);
      if (c.getConnectionId() == null) {
        ReflectionTestUtils.setField(c, "connectionId", ids.incrementAndGet());
      }
      connections.put(c.getConnectionId(), c);
      return c;
    });
    when(connectionRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));
    when(connectionRepository.findByIdWithLock(any()))
        .thenAnswer(inv -> Optional.ofNullable(connections.get(inv.getArgument(0))));
    when(connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(any(), any(), any()))
        .thenAnswer(inv -> connections.values().stream()
            .filter(c -> c.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && c.getSource().getSourceId().equals(inv.getArgument(1))
                && c.getLabel().equals(inv.getArgument(2)))
            .findFirst());
    when(runRepository.save(any(EasyCollectionRun.class))).thenAnswer(inv -> {
      EasyCollectionRun r = inv.getArgument(0);
      if (r.getRunId() == null) {
        ReflectionTestUtils.setField(r, "runId", ids.incrementAndGet());
      }
      runs.put(r.getRunId(), r);
      return r;
    });
    when(runRepository.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(runs.get(inv.getArgument(0))));
    when(runRepository.findAllByBusiness_BusinessIdOrderByRunIdDesc(any()))
        .thenAnswer(inv -> runs.values().stream()
            .filter(r -> r.getBusiness().getBusinessId().equals(inv.getArgument(0)))
            .sorted(Comparator.comparing(EasyCollectionRun::getRunId).reversed()).toList());
    when(rawRepository.save(any(EasyCollectionRawRecord.class))).thenAnswer(inv -> {
      EasyCollectionRawRecord raw = inv.getArgument(0);
      if (raws.values().stream().anyMatch(x -> x.getDedupeKey().equals(raw.getDedupeKey()))) {
        throw new IllegalStateException("dedupe_key UNIQUE 충돌: " + raw.getDedupeKey());
      }
      ReflectionTestUtils.setField(raw, "rawId", ids.incrementAndGet());
      raws.put(raw.getRawId(), raw);
      return raw;
    });
    when(rawRepository.findByDedupeKey(any())).thenAnswer(inv -> raws.values().stream()
        .filter(r -> r.getDedupeKey().equals(inv.getArgument(0))).findFirst());
    when(checkpointRepository.findByConnection_ConnectionIdAndDatasetType(any(), any()))
        .thenAnswer(inv -> checkpoints.values().stream()
            .filter(c -> c.getConnection().getConnectionId().equals(inv.getArgument(0))
                && c.getDatasetType() == inv.getArgument(1))
            .findFirst());
    when(checkpointRepository.save(any(EasyCollectionCheckpoint.class))).thenAnswer(inv -> {
      EasyCollectionCheckpoint c = inv.getArgument(0);
      if (c.getCheckpointId() == null) {
        ReflectionTestUtils.setField(c, "checkpointId", ids.incrementAndGet());
      }
      checkpoints.put(c.getCheckpointId(), c);
      return c;
    });
    when(pipeline.txnRepository.findByBusiness_BusinessIdAndDirectionAndCounterparty(any(), any(), any()))
        .thenAnswer(inv -> pipeline.txns.values().stream()
            .filter(t -> t.getBusiness().getBusinessId().equals(inv.getArgument(0))
                && t.getDirection() == inv.getArgument(1)
                && t.getCounterparty().equals(inv.getArgument(2)))
            .toList());

    service = new EasyCollectionIngestService(pipeline.businessRepository, sourceRepository,
        connectionRepository, runRepository, rawRepository, pipeline.txnRepository,
        new EasyCsvFileAdapter(), new EasyCardsalesAdapter(), new CrefiaCardsalesAdapter(),
        pipeline.importService);
    ReflectionTestUtils.setField(service, "periodAdapters", List.of(bank));
    ReflectionTestUtils.setField(service, "checkpointRepository", checkpointRepository);
    ReflectionTestUtils.setField(service, "transactionManager", new SnapshotTransactionManager());
  }

  // ---------------------------------------------------------------- 장부 저장

  @Test
  @DisplayName("given 계정을 모르는 실거래 when 수집 then 미분류·REVIEW_REQUIRED로 중앙 장부에 저장된다")
  void unclassifiedRealTransactionIsSavedToLedger() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.rows.add(bankRow(TODAY, "103000", 0, 35_000, "알수없는상호", "인터넷", "기타출금", 1_000_000));

    // when
    var response = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(response.run().ledgerSavedCount()).isEqualTo(1);
    assertThat(response.run().ledgerReviewCount()).isEqualTo(1);
    assertThat(response.run().ledgerClassifiedCount()).isZero();
    EasyTransaction txn = onlyTxn();
    assertThat(txn.getAccount()).isNull();
    assertThat(txn.getCategory()).isEqualTo("미분류");
    assertThat(txn.getTaxClassification()).isEqualTo(EasyTaxClassification.REVIEW_REQUIRED);
    assertThat(txn.getEvidenceReview()).isEqualTo(EasyEvidenceReview.NEEDS_REVIEW);
    assertThat(raws.values()).allSatisfy(raw ->
        assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.LEDGER_SAVED));
  }

  @Test
  @DisplayName("given 날짜를 해석하지 못한 행 when 수집 then 원본·사유만 보존하고 장부에는 임의 값으로 저장하지 않는다")
  void unparsableRowIsPreservedButNotSaved() {
    // given
    byte[] csv = csv("2026-13-40,수입,100000,학부형C,날짜오류,계좌이체,EXT-BAD",
        "2026-09-01,수입,300000,학부형D,수강료,계좌이체,EXT-OK");

    // when
    var response = service.ingestCsv(BIZ, "default", "bad.csv", csv);

    // then
    assertThat(response.run().status()).isEqualTo("PARTIAL");
    assertThat(response.run().errorCount()).isEqualTo(1);
    assertThat(response.run().ledgerSavedCount()).isEqualTo(1);
    assertThat(pipeline.txnCount()).isEqualTo(1);
    assertThat(raws.values()).anySatisfy(raw -> {
      assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.ERROR);
      assertThat(raw.getRawPayload()).contains("EXT-BAD");
      assertThat(raw.getNormalizeError()).isNotBlank();
    });
  }

  @Test
  @DisplayName("given 같은 자료 when 세 번 수집 then 장부에는 한 번만 저장된다(멱등)")
  void repeatedCollectionIsIdempotent() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.rows.add(bankRow(TODAY, "090000", 250_000, 0, "김학부", "타행이체", "11월 수강료", 1_250_000));
    bank.rows.add(bankRow(TODAY, "120000", 0, 88_000, "문구상", "체크", "복사용지", 1_162_000));

    // when
    for (int i = 0; i < 3; i++) {
      service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
          EasyCollectionTriggerType.SCHEDULED, null, 5000);
    }

    // then
    assertThat(pipeline.txnCount()).isEqualTo(2);
    assertThat(raws).hasSize(2);
    EasyCollectionRun last = latestRun();
    assertThat(last.getLedgerSavedCount()).isZero();
    assertThat(last.getDuplicateCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("given 날짜·금액·거래처가 같은 서로 다른 입금 2건 when 수집 then 둘 다 장부에 남는다")
  void sameAmountDifferentTransactionsArePreserved() {
    // given — 같은 날 같은 학부모가 10만원씩 두 번 입금(시각·잔액이 다르다)
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.rows.add(bankRow(TODAY, "100000", 100_000, 0, "박학부", "타행이체", "수강료", 500_000));
    bank.rows.add(bankRow(TODAY, "150000", 100_000, 0, "박학부", "타행이체", "수강료", 600_000));

    // when
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(pipeline.txnCount()).isEqualTo(2);
    assertThat(pipeline.txns.values()).extracting(EasyTransaction::getFingerprint)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("given 수기로 이미 입력한 거래 1건 when 같은 내용 2건 수집 then 1건만 제외하고 초과분은 저장한다")
  void manualLedgerOverlapExcludesOnlyMatchingCount() {
    // given
    EasyBusiness business = pipeline.businesses.get(BIZ);
    pipeline.txnRepository.save(EasyTransaction.builder()
        .business(business).occurredOn(LocalDate.of(2026, 9, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(15_000L)
        .counterparty("편의점").description("간식비").payMethod("카드")
        .fingerprint(EasyFingerprint.of(BIZ, LocalDate.of(2026, 9, 5), "EXPENSE", 15_000L,
            "편의점", "간식비", "카드"))
        .build());
    String twice = "2026-09-05,지출,15000,편의점,간식비,카드,";

    // when
    var response = service.ingestCsv(BIZ, "default", "twice.csv", csv(twice, twice));

    // then
    assertThat(response.run().duplicateCount()).isEqualTo(1);
    assertThat(response.run().ledgerSavedCount()).isEqualTo(1);
    assertThat(pipeline.txnCount()).isEqualTo(2);
  }

  // ---------------------------------------------------------------- 중복 회계 방지·연결

  @Test
  @DisplayName("given 카드대금 출금·카드사 정산입금 when 수집 then 저장은 하되 비용·매출로 다시 계산하지 않는다")
  void cardPaymentAndSettlementAreNotDoubleCounted() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.rows.add(bankRow(TODAY, "080000", 0, 1_200_000, "신한카드", "자동이체", "카드대금", 3_000_000));
    bank.rows.add(bankRow(TODAY, "090000", 980_000, 0, "KB국민카드", "입금", "가맹점정산", 3_980_000));

    // when
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(pipeline.txnCount()).isEqualTo(2);
    EasyTransaction payment = txnByCounterparty("신한카드");
    EasyTransaction settlement = txnByCounterparty("KB국민카드");
    assertThat(payment.getTaxClassification()).isEqualTo(EasyTaxClassification.NOT_EXPENSE);
    assertThat(payment.getTaxClassification().excludesProfit(payment.getDirection())).isTrue();
    assertThat(settlement.getTaxClassification()).isEqualTo(EasyTaxClassification.NOT_INCOME);
    assertThat(settlement.getTaxClassification().excludesProfit(settlement.getDirection())).isTrue();
    assertThat(settlement.getMemo()).contains("카드매출과 대사");
    assertThat(settlement.getCategory()).isEqualTo("카드매출 정산(검토)");
    assertThat(payment.getCategory()).isEqualTo("카드대금 결제(검토)");
  }

  @Test
  @DisplayName("given 카드 사용 원거래 when 전체취소·부분취소 수집 then 역분개 행으로 저장되고 원거래와 연결된다")
  void cancellationAndPartialCancellationLinkToOriginal() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "카드");
    bank.rows.add(cardRow("APP-1", null, TODAY, 50_000, "교보문고"));
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);
    Long originalId = txnByAmount(50_000).getTxnId();

    // when — 다음 수집에서 부분취소 2만원, 그다음 나머지 3만원 취소
    bank.rows.add(cardRow("CXL-1", "APP-1", TODAY, 20_000, "교보문고"));
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);
    bank.rows.add(cardRow("CXL-2", "APP-1", TODAY, 30_000, "교보문고"));
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(pipeline.txnCount()).isEqualTo(3);
    EasyTransaction partial = txnByAmount(20_000);
    EasyTransaction rest = txnByAmount(30_000);
    assertThat(partial.isReversal()).isTrue();
    assertThat(rest.isReversal()).isTrue();
    assertThat(partial.getOriginalTxnId()).isEqualTo(originalId);
    assertThat(rest.getOriginalTxnId()).isEqualTo(originalId);
    // 원거래는 수정하지 않는다
    assertThat(txnByAmount(50_000).isReversal()).isFalse();
  }

  @Test
  @DisplayName("given 원거래를 아직 받지 못한 취소 when 수집 then 버리지 않고 저장하되 검토 메모를 남긴다")
  void cancellationWithoutOriginalIsSavedForReview() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "카드");
    bank.rows.add(cardRow("CXL-9", "APP-MISSING", TODAY, 10_000, "문구점"));

    // when
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    EasyTransaction txn = onlyTxn();
    assertThat(txn.getOriginalTxnId()).isNull();
    assertThat(txn.getMemo()).contains("취소 원거래를 아직 찾지 못했습니다");
    assertThat(txn.getEvidenceReview()).isEqualTo(EasyEvidenceReview.NEEDS_REVIEW);
  }

  // ---------------------------------------------------------------- 복구·페이지·커서

  @Test
  @DisplayName("given 기관 응답은 받았지만 장부 저장 실패 when 재시도 then 커서가 그대로라 같은 구간을 다시 저장하고 중복이 없다")
  void ledgerSaveFailureAfterApiResponseIsRecoverable() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.rows.add(bankRow(TODAY, "090000", 300_000, 0, "이학부", "타행이체", "수강료", 300_000));
    AtomicInteger failures = new AtomicInteger(1);
    when(pipeline.batchRepository.save(any())).thenAnswer(inv -> {
      if (failures.getAndDecrement() > 0) {
        throw new IllegalStateException("DB write failed (synthetic)");
      }
      var batch = (f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyImportBatch)
          inv.getArgument(0);
      if (batch.getBatchId() == null) {
        ReflectionTestUtils.setField(batch, "batchId", ids.incrementAndGet());
      }
      pipeline.batches.put(batch.getBatchId(), batch);
      return batch;
    });

    // when
    var failed = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);
    // then — 응답 수신 시각은 남지만 저장 완료일·커서는 없다
    assertThat(failed.run().status()).isEqualTo("FAILED");
    assertThat(failed.run().errorSummary()).startsWith("LEDGER_SAVE_FAILED");
    assertThat(failed.run().fetchedAt()).isNotNull();
    assertThat(failed.run().ledgerSavedThrough()).isNull();
    assertThat(pipeline.txnCount()).isZero();
    assertThat(raws).isEmpty();
    assertThat(checkpoints).isEmpty();
    assertThat(conn.getLastErrorCode()).isEqualTo("LEDGER_SAVE_FAILED");

    // when — 재시도
    var retried = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);
    service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(retried.run().status()).isEqualTo("SUCCESS");
    assertThat(retried.run().ledgerSavedCount()).isEqualTo(1);
    assertThat(retried.run().ledgerSavedThrough()).isEqualTo(TODAY);
    assertThat(pipeline.txnCount()).isEqualTo(1);
    assertThat(savedThrough(conn)).isEqualTo(TODAY.toString());
    assertThat(conn.getLastErrorCode()).isNull();
  }

  @Test
  @DisplayName("given 2,000행을 넘는 4,500건 when 수집 then 2,000행 단위 배치 3개로 나눠 전부 저장한다")
  void moreThanTwoThousandRowsAreSavedInChunks() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    for (int i = 0; i < 4500; i++) {
      bank.rows.add(bankRow(TODAY, String.format("%06d", i), 1_000 + i, 0, "입금자" + i,
          "타행이체", "수강료", 10_000_000L + i));
    }

    // when
    var response = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(response.run().fetchedCount()).isEqualTo(4500);
    assertThat(response.run().ledgerSavedCount()).isEqualTo(4500);
    assertThat(pipeline.batches).hasSize(3);
    assertThat(pipeline.batches.values()).allSatisfy(b -> assertThat(b.getTotalRows()).isLessThanOrEqualTo(2000));
    assertThat(pipeline.txnCount()).isEqualTo(4500);
  }

  @Test
  @DisplayName("given 한 구간이 요청 한도를 넘음 when 수집 then 구간을 반으로 나눠 다시 조회해 빠짐없이 저장한다")
  void windowOverLimitIsSplitAndRefetched() {
    // given — 3일 전부터 수집, 하루 2건씩 = 3일 구간 8건 > 한도 3
    bank.lookbackDays = 3;
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    for (int d = 0; d <= 3; d++) {
      LocalDate day = TODAY.minusDays(3 - d);
      bank.rows.add(bankRow(day, "090000", 10_000 + d, 0, "A" + d, "이체", "수강료", 1_000 + d));
      bank.rows.add(bankRow(day, "100000", 20_000 + d, 0, "B" + d, "이체", "수강료", 2_000 + d));
    }

    // when
    var response = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 3);

    // then
    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(pipeline.txnCount()).isEqualTo(8);
    assertThat(bank.calls.size()).isGreaterThan(1);
    assertThat(bank.calls).allSatisfy(window ->
        assertThat(window[1].toEpochDay() - window[0].toEpochDay()).isLessThanOrEqualTo(31));
  }

  @Test
  @DisplayName("given 은행 인증 만료 when 수집 then 재인증 필요로 표시하고 실패로 끝내며 커서는 그대로다")
  void authExpiryMarksNeedsReauthWithoutRetryLoop() {
    // given
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.failure = new EasyCollectionException(EasyCollectionErrorCode.AUTH_REQUIRED,
        "Bank authentication must be renewed (CF-12806).");

    // when
    var response = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.SCHEDULED, null, 5000);

    // then
    assertThat(response.run().status()).isEqualTo("FAILED");
    assertThat(conn.getStatus()).isEqualTo(EasyCollectionConnectionStatus.NEEDS_REAUTH);
    assertThat(conn.getLastErrorCode()).isEqualTo("AUTH_REQUIRED");
    assertThat(bank.calls).hasSize(1);
    assertThat(checkpoints).isEmpty();
  }

  // ---------------------------------------------------------------- 분류 우선순위

  @Test
  @DisplayName("given 과거 장부에서 같은 거래처가 한 계정으로만 쓰임 when 수집 then 그 계정으로 분류해 저장한다")
  void historicalClassificationIsAppliedFirst() {
    // given — 분개 엔진이 쓰는 자금정산 계정(V976)과 과거 이지샵 계정
    pipeline.seedAccount(12L, "SETTLEMENT", "자금정산",
        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection.ASSET);
    pipeline.seedAccount(501L, "E-RENT", "임차료",
        f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyAccountDirection.EXPENSE);
    pipeline.txnRepository.save(EasyTransaction.builder()
        .business(pipeline.businesses.get(BIZ)).occurredOn(LocalDate.of(2025, 1, 5))
        .direction(EasyTxnDirection.EXPENSE).amount(1_500_000L)
        .counterparty("건물주홍길동").description("1월 임대료")
        .account(pipeline.accounts.get(501L)).sourceKind("EASYSHOP")
        .build());
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    bank.rows.add(bankRow(TODAY, "090000", 0, 1_500_000, "건물주홍길동", "인터넷", "11월 임대료", 100_000));

    // when
    var response = service.ingestByAdapter(BIZ, conn.getLabel(), FakeBankAdapter.CODE,
        EasyCollectionTriggerType.MANUAL, null, 5000);

    // then
    assertThat(response.run().ledgerClassifiedCount()).isEqualTo(1);
    EasyTransaction saved = pipeline.txns.values().stream()
        .filter(t -> t.getDescription().equals("11월 임대료")).findFirst().orElseThrow();
    assertThat(saved.getAccount().getName()).isEqualTo("임차료");
  }

  // ---------------------------------------------------------------- 격리

  @Test
  @DisplayName("given 다른 사업체 run when 장부 저장을 요청 then 거부되고 장부는 그대로다")
  void businessIsolationOnLedgerSave() {
    // given
    var csvResponse = service.ingestCsv(OTHER_BIZ, "default", "pub.csv",
        csv("2026-09-01,수입,10000,서점,책판매,계좌이체,EXT-P1"));
    Long otherBatchId = csvResponse.run().importBatchId();
    EasyCollectionConnection conn = bankConnection(BIZ, "운영계좌");
    EasyCollectionRun foreignRun = runRepository.save(EasyCollectionRun.builder()
        .connection(conn).business(pipeline.businesses.get(BIZ)).triggerType("MANUAL")
        .adapterCode("FILE_CSV").adapterVersion("v1").build());
    long before = pipeline.txnCount();

    // when / then
    assertThatThrownBy(() -> pipeline.importService.saveCollectedBatch(foreignRun, otherBatchId,
        Map.of(), Map.of(), key -> null))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(pipeline.txnCount()).isEqualTo(before);
  }

  @Test
  @DisplayName("given 원문에 출판사 확정 표시가 있는 학원 수집 거래 when 저장 then 학원 장부에만 남는다(사업체 혼합 금지)")
  void collectedRowIsNeverReroutedByRawMarker() {
    // given
    var row = new CollectionParsedRow(1, Map.of(), "{\"business\":\"PUBLISHING_CONFIRMED\"}",
        LocalDate.of(2026, 10, 1), "EXPENSE", 12_345L, "synthetic", "marker", "TRANSFER",
        null, null, null, null, "mk-1", null);

    // when
    service.ingestFinancialDraft(BIZ, java.util.UUID.randomUUID().toString(),
        "FIN_" + "a".repeat(24), List.of(row));

    // then
    assertThat(pipeline.txns.values()).singleElement()
        .satisfies(t -> assertThat(t.getBusiness().getBusinessId()).isEqualTo(BIZ));
  }

  // ---------------------------------------------------------------- MCP 사이트 직접 받기

  private EasySiteCollectionService siteService() {
    return new EasySiteCollectionService(service, mock(EasyTutorAccessService.class),
        pipeline.businessRepository);
  }

  private static final f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal
      SITE_ADMIN = new f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider
          .AuthenticatedPrincipal(1L, "admin", "ADMIN");

  private static EasySiteCollectionService.SiteRowsRequest siteRequest(String site, String dataset,
      String key, List<EasySiteCollectionService.SiteRow> rows) {
    long total = rows.stream().mapToLong(EasySiteCollectionService.SiteRow::amount).sum();
    return new EasySiteCollectionService.SiteRowsRequest(BIZ, site, dataset, "합성-계좌", "2026-10-01",
        "2026-10-31", rows.size(), total, key, rows);
  }

  @Test
  @DisplayName("given 사용자가 은행 사이트에서 받은 입출금 when MCP로 저장 then 중앙 장부에 저장되고 카드 정산입금은 매출로 다시 잡지 않는다")
  void siteBankRowsAreSavedToLedger() {
    // given
    var rows = List.of(
        new EasySiteCollectionService.SiteRow("2026-10-02", "IN", 300_000L, "김학부", "10월 수강료", null, 1_300_000L, null),
        new EasySiteCollectionService.SiteRow("2026-10-05", "IN", 980_000L, "KB국민카드", "가맹점정산", null, 2_280_000L, null),
        new EasySiteCollectionService.SiteRow("2026-10-06", "OUT", 88_000L, "문구상", "복사용지", null, 2_192_000L, null));

    // when
    var response = siteService().save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-1", rows));

    // then
    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(response.run().adapterCode()).isEqualTo(EasyCollectionIngestService.SITE_ADAPTER_CODE);
    assertThat(response.run().ledgerSavedCount()).isEqualTo(3);
    assertThat(pipeline.txnCount()).isEqualTo(3);
    assertThat(txnByCounterparty("KB국민카드").getTaxClassification())
        .isEqualTo(EasyTaxClassification.NOT_INCOME);
    assertThat(raws.values()).allSatisfy(raw -> {
      assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.LEDGER_SAVED);
      assertThat(raw.getRawPayload()).contains("\"provider\":\"MCP_SITE\"");
    });
  }

  @Test
  @DisplayName("given 같은 idempotencyKey 재전송 when 저장 then 다시 저장하지 않고, 같은 키에 다른 내용이면 거절한다")
  void siteRowsAreIdempotentPerKey() {
    // given
    var rows = List.of(new EasySiteCollectionService.SiteRow("2026-10-02", "IN", 300_000L, "김학부", "수강료", null, null, null));
    var changed = List.of(new EasySiteCollectionService.SiteRow("2026-10-02", "IN", 310_000L, "김학부", "수강료", null, null, null));
    var sites = siteService();
    var first = sites.save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-2", rows));

    // when
    var again = sites.save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-2", rows));

    // then
    assertThat(again.run().runId()).isEqualTo(first.run().runId());
    assertThat(pipeline.txnCount()).isEqualTo(1);
    assertThatThrownBy(() -> sites.save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-2", changed)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("다른 내용");
    assertThat(pipeline.txnCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 같은 기간 파일을 새 키로 다시 보냄 when 저장 then 원본 키로 중복을 막아 장부는 그대로다")
  void sameSiteRowsWithNewKeyAreDeduplicated() {
    // given
    var rows = List.of(new EasySiteCollectionService.SiteRow("2026-10-02", "IN", 300_000L, "김학부", "수강료", null, 1_300_000L, null));
    var sites = siteService();
    sites.save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-3", rows));

    // when
    var second = sites.save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-4", rows));

    // then
    assertThat(second.run().duplicateCount()).isEqualTo(1);
    assertThat(pipeline.txnCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 카드사 사이트의 사용·부분취소 when 저장 then 취소는 원거래와 연결된 역분개로 남는다")
  void siteCardCancellationIsLinkedReversal() {
    // given
    var sites = siteService();
    sites.save(SITE_ADMIN, siteRequest("SHINHAN_CARD", "CARD_PURCHASE", "site-key-5", List.of(
        new EasySiteCollectionService.SiteRow("2026-10-02", "OUT", 50_000L, "교보문고", null, "12345678", null, null))));
    Long originalId = txnByAmount(50_000).getTxnId();

    // when
    sites.save(SITE_ADMIN, siteRequest("SHINHAN_CARD", "CARD_PURCHASE", "site-key-6", List.of(
        new EasySiteCollectionService.SiteRow("2026-10-04", "IN", 20_000L, "교보문고", null, "12345678", null, "2026-10-02"))));

    // then
    EasyTransaction partial = txnByAmount(20_000);
    assertThat(partial.isReversal()).isTrue();
    assertThat(partial.getOriginalTxnId()).isEqualTo(originalId);
    assertThat(txnByAmount(50_000).isReversal()).isFalse();
  }

  @Test
  @DisplayName("given 결제선생 수납 완료와 페이민트 정산입금 when 둘 다 저장 then 수강료는 수입, 정산입금은 수입에서 빠진다")
  void payssamTuitionAndSettlementAreNotDoubleCounted() {
    // given
    var sites = siteService();
    sites.save(SITE_ADMIN, siteRequest("PAYSSAM", "TUITION_PAYMENT", "site-key-7", List.of(
        new EasySiteCollectionService.SiteRow("2026-10-05", "IN", 300_000L, "합성학생", "10월 수강료", "P0001", null, null))));

    // when
    sites.save(SITE_ADMIN, siteRequest("KB_BANK", "BANK_ACCOUNT", "site-key-8", List.of(
        new EasySiteCollectionService.SiteRow("2026-10-08", "IN", 297_000L, "(주)페이민트", "결제선생 정산", null, 1_297_000L, null))));

    // then
    EasyTransaction tuition = txnByAmount(300_000);
    EasyTransaction settlement = txnByAmount(297_000);
    assertThat(tuition.getDirection()).isEqualTo(EasyTxnDirection.INCOME);
    assertThat(tuition.getTaxClassification() == null
        || !tuition.getTaxClassification().excludesProfit(tuition.getDirection())).isTrue();
    assertThat(settlement.getTaxClassification()).isEqualTo(EasyTaxClassification.NOT_INCOME);
    assertThat(settlement.getCategory()).isEqualTo("결제선생 정산(검토)");
  }

  // ================================================================ helpers

  private void addSource(Long id, String code) {
    EasyCollectionSource source = EasyCollectionSource.builder()
        .code(code).name(code).adapterType("TEST").active(true).build();
    ReflectionTestUtils.setField(source, "sourceId", id);
    sources.put(id, source);
  }

  private EasyCollectionConnection bankConnection(Long businessId, String label) {
    EasyCollectionConnection conn = EasyCollectionConnection.builder()
        .business(pipeline.businesses.get(businessId)).source(sources.get(2L)).label(label).build();
    conn.updateConnectionRef("cid-synthetic-" + label);
    conn.updateProviderConfig("{\"organization\":\"0004\",\"clientType\":\"B\",\"account\":\"123456789012\"}");
    return connectionRepository.save(conn);
  }

  private static byte[] csv(String... rows) {
    return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private CollectionParsedRow bankRow(LocalDate day, String time, long in, long out,
      String counterparty, String kind, String memo, long balance) {
    ObjectNode raw = new ObjectMapper().createObjectNode();
    raw.put("resAccountTrDate", day.toString().replace("-", ""));
    raw.put("resAccountTrTime", time);
    raw.put("resAccountIn", String.valueOf(in));
    raw.put("resAccountOut", String.valueOf(out));
    raw.put("resAccountDesc1", counterparty);
    raw.put("resAccountDesc2", kind);
    raw.put("resAccountDesc3", memo);
    raw.put("resAfterTranBalance", String.valueOf(balance));
    var judged = EasyBankTransactionClassifier.classify(in > 0, in > 0 ? in : out, counterparty,
        kind + " " + memo);
    if (judged.disposition() != null) raw.put("primary_disposition", judged.disposition());
    if (judged.reviewNote() != null) raw.put("review_note", judged.reviewNote());
    String externalId = "TR:" + day + time + in + out + balance + counterparty;
    return new CollectionParsedRow(0, Map.of(), raw.toString(), day, in > 0 ? "INCOME" : "EXPENSE",
        in > 0 ? in : out, counterparty, memo, kind, null, null, null, null, externalId, null);
  }

  private CollectionParsedRow cardRow(String approvalNo, String cancelOf, LocalDate day,
      long amount, String merchant) {
    ObjectNode raw = new ObjectMapper().createObjectNode();
    raw.put("approvalNo", approvalNo);
    if (cancelOf != null) raw.put("original_external_id", cancelOf);
    raw.put("merchant", merchant);
    return new CollectionParsedRow(0, Map.of(), raw.toString(), day, "EXPENSE", amount,
        merchant, cancelOf == null ? "카드 사용" : "카드 취소", "카드", null, null, null, null,
        approvalNo, null);
  }

  private EasyTransaction onlyTxn() {
    assertThat(pipeline.txns).hasSize(1);
    return pipeline.txns.values().iterator().next();
  }

  private EasyTransaction txnByCounterparty(String counterparty) {
    return pipeline.txns.values().stream().filter(t -> t.getCounterparty().equals(counterparty))
        .findFirst().orElseThrow();
  }

  private EasyTransaction txnByAmount(long amount) {
    return pipeline.txns.values().stream().filter(t -> t.getAmount() == amount)
        .findFirst().orElseThrow();
  }

  private EasyCollectionRun latestRun() {
    return runs.values().stream().max(Comparator.comparing(EasyCollectionRun::getRunId)).orElseThrow();
  }

  private String savedThrough(EasyCollectionConnection conn) {
    return checkpoints.values().stream()
        .filter(c -> c.getConnection() == conn
            && c.getDatasetType() == EasyCollectionDatasetType.BANK_TRANSACTION)
        .map(EasyCollectionCheckpoint::getCheckpointValue).findFirst().orElse(null);
  }

  /** 합성 기관 어댑터 — 요청 구간에 속한 행만 돌려준다. */
  static final class FakeBankAdapter implements EasyPeriodCollectionAdapter {
    static final String CODE = "FAKE_BANK";
    final List<CollectionParsedRow> rows = new ArrayList<>();
    final List<LocalDate[]> calls = new ArrayList<>();
    int lookbackDays = 0;
    RuntimeException failure;

    @Override public String adapterCode() { return CODE; }
    @Override public String adapterVersion() { return "test"; }
    @Override public List<String> configKeys() { return List.of(); }
    @Override public void validateConnection(String connectionRef) {}
    @Override public int maxPeriodDays() { return 31; }
    @Override public int initialLookbackDays() { return lookbackDays; }

    @Override
    public List<CollectionParsedRow> fetchPeriod(String connectionRef, String providerConfig,
        LocalDate from, LocalDate to, int limit) {
      calls.add(new LocalDate[] {from, to});
      if (failure != null) {
        throw failure;
      }
      return rows.stream()
          .filter(r -> !r.occurredOn().isBefore(from) && !r.occurredOn().isAfter(to))
          .toList();
    }
  }

  /** 롤백을 Map 스냅샷 복원으로 모사한다(실제 DB 트랜잭션의 원자성 검증용). */
  final class SnapshotTransactionManager implements PlatformTransactionManager {
    private final Deque<List<Map<Long, ?>>> stack = new ArrayDeque<>();
    private final List<Map<Long, ?>> tracked() {
      return List.of(raws, runs, checkpoints, pipeline.txns, pipeline.rows, pipeline.batches);
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      List<Map<Long, ?>> copy = new ArrayList<>();
      for (Map<Long, ?> map : tracked()) {
        copy.add(new LinkedHashMap<>(map));
      }
      stack.push(copy);
      return new SimpleTransactionStatus(true);
    }

    @Override
    public void commit(TransactionStatus status) {
      stack.pop();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void rollback(TransactionStatus status) {
      List<Map<Long, ?>> snapshot = stack.pop();
      List<Map<Long, ?>> live = tracked();
      for (int i = 0; i < live.size(); i++) {
        Map target = live.get(i);
        target.clear();
        target.putAll(snapshot.get(i));
      }
    }
  }

  @SuppressWarnings("unused")
  private static Function<String, Long> noTxn() {
    return key -> null;
  }
}
