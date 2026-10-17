package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval.ApprovalStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionApprovalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * EASY P4 — 격리 MySQL에서 CSV 수집 → 중복 제거 → staging → 승인 → 장부 commit 전체 흐름 검증.
 *
 * <p>일부러 {@code @Transactional}을 붙이지 않는다. 서비스가 실제로 커밋하는 경계(승인 상태 저장, 장부 반영,
 * 실패 후 재시도)를 있는 그대로 관찰해야 하기 때문이다. 각 테스트는 고유한 사업체를 새로 만들어 서로 간섭하지 않는다.
 * 실제 금융기관 로그인·Muse Spark 호출은 사용하지 않는다(CSV 파일 입력만).
 */
@SpringBootTest
class EasyCollectionApprovalP4StagingTest {

  private static final String HEADER = "거래일,구분,금액,거래처,적요,결제수단,승인번호";

  @Autowired EasyCollectionIngestService ingestService;
  @Autowired EasyCollectionApprovalService approvalService;
  @Autowired EasyBusinessRepository businessRepository;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean EasyTutorAccessService accessService;
  @MockitoSpyBean EasyImportService importService;
  @MockitoSpyBean EasyCollectionApprovalRepository approvalRepository;

  private EasyBusiness bizA;
  private EasyBusiness bizB;

  @BeforeEach
  void setUp() {
    reset(importService, approvalRepository);
    String tag = UUID.randomUUID().toString().substring(0, 8);
    bizA = businessRepository.save(EasyBusiness.builder().code("P4A_" + tag).name("P4 A " + tag).active(true).build());
    bizB = businessRepository.save(EasyBusiness.builder().code("P4B_" + tag).name("P4 B " + tag).active(true).build());
    // 소유자 A(userId 10)는 사업체 A만, 소유자 B(userId 11)는 사업체 B만 접근할 수 있다.
    when(accessService.requireAllowedBusiness(any(), anyLong())).thenAnswer(inv -> {
      JwtTokenProvider.AuthenticatedPrincipal p = inv.getArgument(0);
      Long businessId = inv.getArgument(1);
      EasyBusiness own = p.userId() == 10L ? bizA : bizB;
      if (!own.getBusinessId().equals(businessId)) {
        throw new AccessDeniedException("허용되지 않은 사업체");
      }
      return own;
    });
  }

  private JwtTokenProvider.AuthenticatedPrincipal ownerA() {
    return new JwtTokenProvider.AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
  }

  private JwtTokenProvider.AuthenticatedPrincipal ownerB() {
    return new JwtTokenProvider.AuthenticatedPrincipal(11L, "owner-b", "TUTOR");
  }

  private byte[] csv(String... rows) {
    return (HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private long txns(EasyBusiness b) {
    return jdbc.queryForObject("select count(*) from easy_transactions where business_id = ?",
        Long.class, b.getBusinessId());
  }

  private long approvedRowsStatus(Long batchId, String status) {
    return jdbc.queryForObject(
        "select count(*) from easy_import_rows where batch_id = ? and status = ?", Long.class, batchId, status);
  }

  private EasyCollectionIngestResponse ingest(EasyBusiness b, String file, byte[] content) {
    return ingestService.ingestCsv(b.getBusinessId(), "p4-" + b.getCode(), file, content);
  }

  private EasyCollectionApproval request(EasyBusiness b, JwtTokenProvider.AuthenticatedPrincipal p, Long runId) {
    return approvalService.requestApproval(b.getBusinessId(), runId, p.loginId(), p);
  }

  @Test
  @DisplayName("given V1088·V1089 적용 DB when 스키마 조회 then 테이블·커밋 컬럼·FK가 있고 마이그레이션이 성공 상태다")
  void flywaySchemaIsApplied() {
    List<Map<String, Object>> history = jdbc.queryForList(
        "select version, success from flyway_schema_history where version in ('1088','1089') order by installed_rank");
    assertThat(history).extracting(m -> m.get("version").toString()).containsExactly("1088", "1089");
    assertThat(history).allSatisfy(m -> assertThat(m.get("success")).isIn(true, 1, 1L));

    List<String> columns = jdbc.queryForList(
        "select column_name from information_schema.columns where table_schema = database() "
            + "and table_name = 'easy_collection_approvals'", String.class);
    assertThat(columns).contains("approval_id", "run_id", "business_id", "status", "idempotency_key",
        "committed_at", "commit_error");
    Long fks = jdbc.queryForObject(
        "select count(*) from information_schema.referential_constraints where constraint_schema = database() "
            + "and table_name = 'easy_collection_approvals'", Long.class);
    assertThat(fks).isEqualTo(2L);
  }

  @Test
  @DisplayName("given CSV 3행 when 업로드·승인 요청 then 승인 전 장부는 0건이고 staging에는 행이 있다")
  void ledgerUntouchedBeforeApproval() {
    EasyCollectionIngestResponse res = ingest(bizA, "a.csv", csv(
        "2026-10-01,지출,11000,문구점,펜,카드,A1", "2026-10-02,지출,22000,서점,교재,카드,A2",
        "2026-10-03,수입,300000,학부모,수강료,계좌,A3"));
    Long batchId = res.run().importBatchId();
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());

    assertThat(batchId).isNotNull();
    assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(txns(bizA)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from easy_import_rows where batch_id = ?", Long.class, batchId))
        .isEqualTo(3L);
  }

  @Test
  @DisplayName("given 같은 CSV 재업로드 when 두 번째 수집 then 새 중복 행 없이 duplicateCount로 집계된다")
  void duplicateFileIsDeduplicated() {
    byte[] content = csv("2026-10-01,지출,11000,문구점,펜,카드,D1", "2026-10-02,지출,22000,서점,교재,카드,D2");
    EasyCollectionIngestResponse first = ingest(bizA, "d.csv", content);
    EasyCollectionIngestResponse second = ingest(bizA, "d.csv", content);

    assertThat(first.run().acceptedCount()).isEqualTo(2);
    assertThat(second.run().acceptedCount()).isZero();
    assertThat(second.run().duplicateCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("given 승인 요청 when 승인·반영 then 장부에 정확히 한 번 반영되고 상태가 COMMITTED로 저장된다")
  void approveAndCommitWritesOnceAndPersistsStatus() {
    EasyCollectionIngestResponse res = ingest(bizA, "c.csv", csv(
        "2026-10-01,지출,11000,문구점,펜,카드,C1", "2026-10-02,지출,22000,서점,교재,카드,C2"));
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());

    EasyCollectionApproval result = approvalService.approveAndCommit(
        bizA.getBusinessId(), approval.getApprovalId(), "owner-a", "ok", ownerA());

    assertThat(txns(bizA)).isEqualTo(2L);
    assertThat(result.getStatus()).isEqualTo(ApprovalStatus.COMMITTED);
    // DB에서 다시 읽어 실제로 저장되었는지 확인한다(영속성 컨텍스트에 의존하지 않는다).
    String persisted = jdbc.queryForObject("select status from easy_collection_approvals where approval_id = ?",
        String.class, approval.getApprovalId());
    assertThat(persisted).isEqualTo("COMMITTED");
    assertThat(jdbc.queryForObject("select committed_at is not null from easy_collection_approvals where approval_id = ?",
        Boolean.class, approval.getApprovalId())).isTrue();
  }

  @Test
  @DisplayName("given COMMITTED 건 when retryCommit 재호출 then 장부 건수가 늘지 않는다")
  void retryAfterCommittedDoesNotDuplicate() {
    EasyCollectionIngestResponse res = ingest(bizA, "r.csv", csv("2026-10-01,지출,11000,문구점,펜,카드,R1"));
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());
    approvalService.approveAndCommit(bizA.getBusinessId(), approval.getApprovalId(), "owner-a", "ok", ownerA());

    approvalService.retryCommit(bizA.getBusinessId(), approval.getApprovalId(), ownerA());

    assertThat(txns(bizA)).isEqualTo(1L);
  }

  @Test
  @DisplayName("given commit 실패 주입 when 승인·반영 then COMMIT_FAILED로 남고 장부는 0건, 재시도하면 한 번만 반영된다")
  void failedCommitIsRetryable() {
    EasyCollectionIngestResponse res = ingest(bizA, "f.csv", csv(
        "2026-10-01,지출,11000,문구점,펜,카드,F1", "2026-10-02,지출,22000,서점,교재,카드,F2"));
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());
    doThrow(new IllegalStateException("주입된 일시 오류")).doCallRealMethod()
        .when(importService).confirm(anyLong(), any());

    EasyCollectionApproval failed = approvalService.approveAndCommit(
        bizA.getBusinessId(), approval.getApprovalId(), "owner-a", "ok", ownerA());

    assertThat(failed.getStatus()).isEqualTo(ApprovalStatus.COMMIT_FAILED);
    assertThat(txns(bizA)).isZero();
    assertThat(jdbc.queryForObject("select status from easy_collection_approvals where approval_id = ?",
        String.class, approval.getApprovalId())).isEqualTo("COMMIT_FAILED");

    EasyCollectionApproval retried = approvalService.retryCommit(
        bizA.getBusinessId(), approval.getApprovalId(), ownerA());

    assertThat(retried.getStatus()).isEqualTo(ApprovalStatus.COMMITTED);
    assertThat(txns(bizA)).isEqualTo(2L);
  }

  @Test
  @DisplayName("given commit 성공 직후 실패 주입 when 재시도 then 중복 거래가 생기지 않는다(장부와 상태는 원자적)")
  void failureRightAfterCommitDoesNotDuplicate() {
    EasyCollectionIngestResponse res = ingest(bizA, "s.csv", csv(
        "2026-10-01,지출,11000,문구점,펜,카드,S1", "2026-10-02,지출,22000,서점,교재,카드,S2"));
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());
    // 실제 confirm이 끝난 직후(상태 기록 직전)에 실패를 던진다. 한 트랜잭션이면 장부 INSERT도 함께 롤백되어야 한다.
    AtomicInteger calls = new AtomicInteger();
    doAnswer(inv -> {
      Object result = inv.callRealMethod();
      if (calls.incrementAndGet() == 1) {
        throw new IllegalStateException("주입된 반영 직후 실패");
      }
      return result;
    }).when(importService).confirm(anyLong(), any());

    EasyCollectionApproval afterFailure = approvalService.approveAndCommit(
        bizA.getBusinessId(), approval.getApprovalId(), "owner-a", "ok", ownerA());
    long txnsAfterFailure = txns(bizA);
    EasyCollectionApproval retried = approvalService.retryCommit(
        bizA.getBusinessId(), approval.getApprovalId(), ownerA());

    assertThat(afterFailure.getStatus()).isEqualTo(ApprovalStatus.COMMIT_FAILED);
    assertThat(txnsAfterFailure).as("실패 시 장부 INSERT도 롤백").isZero();
    assertThat(retried.getStatus()).isEqualTo(ApprovalStatus.COMMITTED);
    assertThat(txns(bizA)).as("재시도 후 정확히 한 번만 반영").isEqualTo(2L);
  }

  @Test
  @DisplayName("given 같은 승인 건 when 두 스레드가 동시에 승인·반영 then 장부는 한 번만 반영된다")
  void concurrentApprovalCommitsOnce() throws Exception {
    EasyCollectionIngestResponse res = ingest(bizA, "k.csv", csv(
        "2026-10-01,지출,11000,문구점,펜,카드,K1", "2026-10-02,지출,22000,서점,교재,카드,K2",
        "2026-10-03,수입,300000,학부모,수강료,계좌,K3"));
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      String who = "owner-a-" + i;
      tasks.add(() -> {
        start.await();
        try {
          return approvalService.approveAndCommit(bizA.getBusinessId(), approval.getApprovalId(), who, "ok",
              ownerA()).getStatus().name();
        } catch (RuntimeException e) {
          return "EXC:" + e.getClass().getSimpleName();
        }
      });
    }
    List<Future<String>> futures = new ArrayList<>();
    for (Callable<String> t : tasks) {
      futures.add(pool.submit(t));
    }
    start.countDown();
    List<String> outcomes = new ArrayList<>();
    for (Future<String> f : futures) {
      outcomes.add(f.get());
    }
    pool.shutdown();

    assertThat(outcomes).as("한 요청만 승인·반영, 다른 요청은 '이미 처리됨' 거부").containsExactlyInAnyOrder(
        "COMMITTED", "EXC:IllegalStateException");
    assertThat(txns(bizA)).as("outcomes=%s", outcomes).isEqualTo(3L);
    assertThat(jdbc.queryForObject("select status from easy_collection_approvals where approval_id = ?",
        String.class, approval.getApprovalId())).isEqualTo("COMMITTED");
  }

  @Test
  @DisplayName("given 소유자 B when 사업체 A의 승인 건을 요청·승인·반영 then 거부되고 A의 장부는 그대로다")
  void tenantIsolation() {
    EasyCollectionIngestResponse res = ingest(bizA, "t.csv", csv("2026-10-01,지출,11000,문구점,펜,카드,T1"));
    EasyCollectionApproval approval = request(bizA, ownerA(), res.run().runId());

    assertThatThrownBy(() -> request(bizA, ownerB(), res.run().runId())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> approvalService.approveAndCommit(
        bizA.getBusinessId(), approval.getApprovalId(), "owner-b", "x", ownerB()))
        .isInstanceOf(AccessDeniedException.class);
    // 사업체 B 경로로 A의 승인 ID를 넣어도(경로 위조) 사업체 불일치로 막힌다.
    assertThatThrownBy(() -> approvalService.approveAndCommit(
        bizB.getBusinessId(), approval.getApprovalId(), "owner-b", "x", ownerB()))
        .isInstanceOf(SecurityException.class);

    assertThat(txns(bizA)).isZero();
    assertThat(txns(bizB)).isZero();
    assertThat(Optional.ofNullable(jdbc.queryForObject(
        "select status from easy_collection_approvals where approval_id = ?", String.class,
        approval.getApprovalId()))).contains("PENDING");
  }
}
