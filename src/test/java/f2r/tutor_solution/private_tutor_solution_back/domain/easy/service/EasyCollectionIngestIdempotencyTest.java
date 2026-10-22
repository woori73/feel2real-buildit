package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 * PHASE 1 수집 파이프라인 멱등성 증명 — TEST A~F + 3단계 dedupe.
 * DB 없는 Map 하네스(EasyShopPipelineHarness 재사용)로 raw UNIQUE 키 충돌 시
 * 즉석에서 실패하도록 걸어 기존 import 경계가 중복 없이 유지됨을 보인다.
 * 자동 collect ≠ 자동 confirm: 이 테스트는 절대 confirm하지 않는다.
 */
class EasyCollectionIngestIdempotencyTest {

  private static final Long BIZ = 1L;
  private static final String HEADER = "거래일,구분,금액,거래처,적요,결제수단,승인번호";

  private final EasyShopPipelineHarness pipeline = new EasyShopPipelineHarness();
  private final EasyCollectionSourceRepository sourceRepository =
      mock(EasyCollectionSourceRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionConnectionRepository connectionRepository =
      mock(EasyCollectionConnectionRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionRunRepository runRepository =
      mock(EasyCollectionRunRepository.class, withSettings().strictness(Strictness.LENIENT));
  private final EasyCollectionRawRecordRepository rawRepository =
      mock(EasyCollectionRawRecordRepository.class, withSettings().strictness(Strictness.LENIENT));

  private final Map<Long, EasyCollectionSource> sources = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionConnection> connections = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRun> runs = new LinkedHashMap<>();
  private final Map<Long, EasyCollectionRawRecord> raws = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong(5000L);

  private EasyCollectionIngestService service;

  @BeforeEach
  void setUp() {
    pipeline.seedBusiness(BIZ, "T1", "테스트학원");
    EasyCollectionSource source = EasyCollectionSource.builder()
        .code("EXCEL_FILE").name("파일 업로드").adapterType("FILE_CSV").active(true).build();
    ReflectionTestUtils.setField(source, "sourceId", 1L);
    sources.put(1L, source);

    // Cardsales source for adapter tests
    EasyCollectionSource cardsalesSource = EasyCollectionSource.builder()
        .code("CARDSales").name("카드사 승인/매입/입금").adapterType("CARDSales").active(true).build();
    ReflectionTestUtils.setField(cardsalesSource, "sourceId", 2L);
    sources.put(2L, cardsalesSource);

    when(sourceRepository.findByCode(any())).thenAnswer(inv -> sources.values().stream()
        .filter(s -> s.getCode().equals(inv.getArgument(0))).findFirst());
    when(sourceRepository.findAll()).thenAnswer(inv -> new ArrayList<>(sources.values()));
    when(connectionRepository.save(any(EasyCollectionConnection.class))).thenAnswer(inv -> {
      EasyCollectionConnection c = inv.getArgument(0);
      ReflectionTestUtils.setField(c, "connectionId", ids.incrementAndGet());
      connections.put(c.getConnectionId(), c);
      return c;
    });
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
      // DB UNIQUE(dedupe_key) 모사 — 같은 키로 두 번 save되면 foundation 로직 결함으로 본다.
      boolean collide = raws.values().stream()
          .anyMatch(x -> x.getDedupeKey().equals(raw.getDedupeKey()));
      if (collide) {
        throw new IllegalStateException("dedupe_key UNIQUE 충돌: " + raw.getDedupeKey());
      }
      ReflectionTestUtils.setField(raw, "rawId", ids.incrementAndGet());
      raws.put(raw.getRawId(), raw);
      return raw;
    });
    when(rawRepository.findByDedupeKey(any())).thenAnswer(inv -> raws.values().stream()
        .filter(r -> r.getDedupeKey().equals(inv.getArgument(0))).findFirst());
    when(rawRepository.findAllByRun_RunIdOrderByRawIdAsc(any())).thenAnswer(inv -> raws.values()
        .stream().filter(r -> r.getRun().getRunId().equals(inv.getArgument(0)))
        .sorted(Comparator.comparing(EasyCollectionRawRecord::getRawId)).toList());

    service = new EasyCollectionIngestService(pipeline.businessRepository, sourceRepository,
        connectionRepository, runRepository, rawRepository, pipeline.txnRepository,
        new EasyCsvFileAdapter(), new EasyCardsalesAdapter(), new CrefiaCardsalesAdapter(),
        pipeline.importService);
  }

  private static byte[] csv(String... rows) {
    return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private static final String ROW_A = "2026-09-01,수입,1000000,학부형A,9월 수강료,계좌이체,EXT-001";
  private static final String ROW_B = "2026-09-02,지출,50000,문구상,복합기 용지,카드,EXT-002";
  private static final String ROW_C = "2026-09-03,지출,300000,kt,통신비,계좌이체,EXT-003";

  @Test
  @DisplayName("TEST A — 최초 수집 3행: 전량 import staging 인계 + raw IMPORT_LINKED")
  void testA_firstIngest_handsOffAllRows() {
    var response = service.ingestCsv(BIZ, "default", "sept.csv", csv(ROW_A, ROW_B, ROW_C));

    assertThat(response.run().status()).isEqualTo("SUCCESS");
    assertThat(response.run().fetchedCount()).isEqualTo(3);
    assertThat(response.run().acceptedCount()).isEqualTo(3);
    assertThat(response.run().duplicateCount()).isZero();
    assertThat(response.run().importBatchId()).isNotNull();
    Long batchId = response.run().importBatchId();
    assertThat(pipeline.rows.values())
        .filteredOn(r -> r.getBatch().getBatchId().equals(batchId)).hasSize(3);
    assertThat(raws.values()).allSatisfy(raw -> {
      assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.IMPORT_LINKED);
      assertThat(raw.getImportBatchId()).isEqualTo(batchId);
      assertThat(raw.getImportRowId()).isNotNull();
    });
    // 수취만 있고 확정은 없다 — 자동 confirm 금지 원칙.
    assertThat(pipeline.txnCount()).isZero();
  }

  @Test
  @DisplayName("TEST B — 동일 파일 재업로드(파일명 무관): 전건 중복, 새 배치 없음")
  void testB_sameFileReupload_allDuplicate() {
    service.ingestCsv(BIZ, "default", "sept.csv", csv(ROW_A, ROW_B, ROW_C));
    int batchesBefore = pipeline.batches.size();

    var second = service.ingestCsv(BIZ, "default", "sept-copy.csv", csv(ROW_A, ROW_B, ROW_C));

    assertThat(second.run().status()).isEqualTo("SUCCESS");
    assertThat(second.run().acceptedCount()).isZero();
    assertThat(second.run().duplicateCount()).isEqualTo(3);
    assertThat(second.run().importBatchId()).isNull();
    assertThat(pipeline.batches).hasSize(batchesBefore);
  }

  @Test
  @DisplayName("TEST C — 승인번호 동일·내용 변경: EXT 키 우선으로 중복 제외")
  void testC_sameExternalId_differentContent_stillDuplicate() {
    service.ingestCsv(BIZ, "default", "v1.csv", csv(ROW_C));
    var changed = "2026-09-03,지출,300000,KT텔레콤,9월 통신요금 정정,카드,EXT-003";

    var second = service.ingestCsv(BIZ, "default", "v2.csv", csv(changed));

    assertThat(second.run().acceptedCount()).isZero();
    assertThat(second.run().duplicateCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("TEST D — 행 순서 변경 재업로드: 전건 중복")
  void testD_reorderedRows_allDuplicate() {
    service.ingestCsv(BIZ, "default", "v1.csv", csv(ROW_A, ROW_B, ROW_C));
    var second = service.ingestCsv(BIZ, "default", "v1.csv", csv(ROW_C, ROW_A, ROW_B));

    assertThat(second.run().acceptedCount()).isZero();
    assertThat(second.run().duplicateCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("TEST E — 1행 추가 후 재업로드: 신규 1행만 accepted")
  void testE_oneRowAdded_onlyNewAccepted() {
    service.ingestCsv(BIZ, "default", "v1.csv", csv(ROW_A, ROW_B));
    String rowD = "2026-09-04,수입,800000,학부형B,10월 수강료 예약,계좌이체,EXT-004";

    var second = service.ingestCsv(BIZ, "default", "v2.csv", csv(ROW_A, ROW_B, rowD));

    assertThat(second.run().acceptedCount()).isEqualTo(1);
    assertThat(second.run().duplicateCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("TEST F — 동일 튜플 2건(서로 다른 실제 거래): 나란히 accepted")
  void testF_identicalTuples_bothAccepted() {
    String twice = "2026-09-05,지출,15000,편의점,간식비,카드,";
    var response = service.ingestCsv(BIZ, "default", "twice.csv", csv(twice, twice));

    assertThat(response.run().acceptedCount()).isEqualTo(2);
    assertThat(response.run().duplicateCount()).isZero();
    assertThat(response.run().importBatchId()).isNotNull();

    var again = service.ingestCsv(BIZ, "default", "twice.csv", csv(twice, twice));
    assertThat(again.run().acceptedCount()).isZero();
    assertThat(again.run().duplicateCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("3단계 dedupe — 기존 장부 fingerprint 충돌 행은 import에 넘기지 않는다")
  void ledgerFingerprintCollision_excluded() {
    EasyBusiness business = pipeline.businesses.get(BIZ);
    EasyTransaction existing = EasyTransaction.builder()
        .business(business).occurredOn(LocalDate.of(2026, 9, 1))
        .direction(EasyTxnDirection.INCOME).amount(1000000L)
        .counterparty("학부형A").description("9월 수강료")
        .fingerprint(EasyFingerprint.of(BIZ, LocalDate.of(2026, 9, 1), "INCOME", 1000000L,
            "학부형A", "9월 수강료", "계좌이체"))
        .build();
    pipeline.txnRepository.save(existing);

    // 외부ID가 달라 EXT 키는 신규지만 fingerprint는 기존 장부와 같다.
    String sameButNewId = "2026-09-01,수입,1000000,학부형A,9월 수강료,계좌이체,EXT-999";
    var response = service.ingestCsv(BIZ, "default", "later.csv", csv(sameButNewId, ROW_B));

    assertThat(response.run().acceptedCount()).isEqualTo(1);
    assertThat(response.run().duplicateCount()).isEqualTo(1);
    assertThat(raws.values()).anySatisfy(raw -> {
      assertThat(raw.getDedupeKey()).endsWith("ext-999"); // EXT 키는 normalize(소문자)로 저장
      assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.LEDGER_DUPLICATE_EXCLUDED);
    });
    assertThat(pipeline.txnCount()).isEqualTo(1); // 직접 INSERT 없음 — 기존 장부 건수 그대로
  }

  @Test
  @DisplayName("정규화 실패 행은 raw ERROR로 보존, 나머지는 인계 (run PARTIAL)")
  void invalidRow_keptAsErrorRaw() {
    String broken = "2026-13-99,수입,100000,학부형C,날짜오류,계좌이체,EXT-777";
    var response = service.ingestCsv(BIZ, "default", "broken.csv", csv(broken, ROW_A));

    assertThat(response.run().status()).isEqualTo("PARTIAL");
    assertThat(response.run().errorCount()).isEqualTo(1);
    assertThat(response.run().acceptedCount()).isEqualTo(1);
    assertThat(response.rowErrors()).hasSize(1);
    assertThat(raws.values()).anySatisfy(raw -> {
      assertThat(raw.getStatus()).isEqualTo(EasyCollectionRawStatus.ERROR);
      assertThat(raw.getNormalizeError()).isNotBlank();
      assertThat(raw.getRawPayload()).contains("EXT-777");
    });
  }

  @Test
  @DisplayName("XLSX는 명시적 거부 — run FAILED, import 없음")
  void xlsxRejected_failsRun() {
    var response = service.ingestCsv(BIZ, "default", "data.xlsx",
        "whatever".getBytes(StandardCharsets.UTF_8));

    assertThat(response.run().status()).isEqualTo("FAILED");
    assertThat(response.run().importBatchId()).isNull();
    assertThat(pipeline.batches).isEmpty();
  }

  @Test
  @DisplayName("run 상태 확인 API — 최근 run 내림차순 + 연결 재사용")
  void listRuns_andConnectionReuse() {
    service.ingestCsv(BIZ, "default", "v1.csv", csv(ROW_A));
    service.ingestCsv(BIZ, "default", "v2.csv", csv(ROW_B));

    var listed = service.listRuns(BIZ);
    assertThat(listed).hasSize(2);
    assertThat(listed.get(0).runId()).isGreaterThan(listed.get(1).runId());
    assertThat(listed.get(0).sourceFilename()).isEqualTo("v2.csv");
    assertThat(connections).hasSize(1); // 동일 business+label은 연결 재사용
    assertThat(connections.values().iterator().next().getLastSuccessAt()).isNotNull();
  }

  @Test
  void givenFinancialArchive_whenHandoffRetried_thenSameRunAndGenericConfirmIsDenied() {
    // given
    var rows = new EasyCsvFileAdapter().parse(csv(ROW_A, ROW_B), "sample.csv");
    String archive = java.util.UUID.randomUUID().toString();
    String scope = "FIN_0123456789abcdef01234567";
    // when
    var first = service.ingestFinancialDraft(BIZ, archive, scope, rows);
    var retry = service.ingestFinancialDraft(BIZ, archive, scope, rows);
    // then
    assertThat(first.run().acceptedCount()).isEqualTo(2);
    assertThat(retry.run().runId()).isEqualTo(first.run().runId());
    assertThat(runs).hasSize(1);
    assertThat(pipeline.txnCount()).isZero();
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        pipeline.importService.confirm(first.run().importBatchId(), null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(pipeline.txnCount()).isZero();
  }

  @Test
  void givenIdenticalNoIdTransactionsInDifferentAccounts_whenCollected_thenDoNotCollapseRawRows() {
    var rows = new EasyCsvFileAdapter().parse(csv("2026-10-01,지출,12000,SAMPLE,SAMPLE,계좌이체,"), "sample.csv");
    service.ingestFinancialDraft(BIZ, java.util.UUID.randomUUID().toString(), "FIN_0123456789abcdef01234567", rows);
    service.ingestFinancialDraft(BIZ, java.util.UUID.randomUUID().toString(), "FIN_abcdef0123456789abcdef01", rows);
    assertThat(raws).hasSize(2);
    assertThat(pipeline.rows).hasSize(2);
    assertThat(pipeline.txnCount()).isZero();
  }

  @Test
  void givenLegacyCsvNamedLikeArchive_whenFinancialHandoffRuns_thenNeverAdoptUnrelatedRun() {
    String archive = java.util.UUID.randomUUID().toString();
    var legacy = service.ingestCsv(BIZ, "default", "financial-" + archive, csv(ROW_A));
    var rows = new EasyCsvFileAdapter().parse(csv(ROW_B), "sample.csv");
    var financial = service.ingestFinancialDraft(BIZ, archive, "FIN_0123456789abcdef01234567", rows);
    assertThat(financial.run().runId()).isNotEqualTo(legacy.run().runId());
    assertThat(financial.run().adapterCode()).isEqualTo("FINANCIAL_FILE");
    assertThat(financial.run().acceptedCount()).isEqualTo(1);
    assertThat(pipeline.txnCount()).isZero();
  }
}
