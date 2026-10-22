package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real MySQL + real access checks + HTTP endpoints. No mocked authority or import service. The
 * handoff .txt was absent from all three remote branches when this regression was written.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EasyCollectionConfirmBypassMySqlTest {
  @Autowired EasyCollectionIngestService ingest;
  @Autowired EasyCollectionApprovalService approvals;
  @Autowired EasyImportService imports;
  @Autowired EasyTutorImportService tutorImports;
  @Autowired EasyBusinessRepository businesses;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider jwt;
  private EasyBusiness own, other;
  private final JwtTokenProvider.AuthenticatedPrincipal admin =
      new JwtTokenProvider.AuthenticatedPrincipal(5L, "admin", "ADMIN");

  @BeforeEach
  void givenIsolatedBusinesses() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    own =
        businesses.save(
            EasyBusiness.builder().code("BYP_A_" + tag).name("Bypass A").active(true).build());
    other =
        businesses.save(
            EasyBusiness.builder().code("BYP_B_" + tag).name("Bypass B").active(true).build());
  }

  private EasyCollectionIngestResponse collected() {
    return ingest.ingestCsv(
        own.getBusinessId(),
        "bypass-test",
        "source.csv",
        ("거래일,구분,금액,거래처,적요,결제수단,승인번호\n"
                + "2026-10-01,지출,12000,문구점,합성자료,카드,"
                + UUID.randomUUID()
                + "\n")
            .getBytes(StandardCharsets.UTF_8));
  }

  private long ledger() {
    return db.queryForObject(
        "SELECT COUNT(*) FROM easy_transactions WHERE business_id=?",
        Long.class,
        own.getBusinessId());
  }

  private EasyCollectionApproval request(EasyCollectionIngestResponse c) {
    return approvals.requestApproval(own.getBusinessId(), c.run().runId(), "admin", admin);
  }

  @Test
  void
      givenCollectedBatchWithoutApproval_whenGenericConfirmAndAccountChange_thenDeniedAndUnchanged()
          throws Exception {
    var c = collected();
    Long batch = c.run().importBatchId();
    var before = imports.getBatch(batch);
    assertThatThrownBy(() -> imports.confirm(batch, null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                tutorImports.selectAccount(
                    admin, own.getBusinessId(), batch, before.rows().getFirst().rowId(), 1L))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    mvc.perform(
            post("/api/admin/easy/imports/" + batch + "/confirm")
                .cookie(
                    new jakarta.servlet.http.Cookie(
                        "ACCESS_TOKEN", jwt.issue(5L, "admin", "ADMIN"))))
        .andExpect(status().isForbidden());
    assertThat(imports.getBatch(batch)).isEqualTo(before);
    assertThat(ledger()).isZero();
  }

  @Test
  void
      givenPendingRejectedAndApproved_whenGenericConfirm_thenAlwaysDeniedButDedicatedApprovalCommitsOnce() {
    var c = collected();
    var a = request(c);
    Long batch = c.run().importBatchId();
    assertThatThrownBy(
            () -> approvals.commitApproved(own.getBusinessId(), a.getApprovalId(), admin))
        .isInstanceOf(IllegalStateException.class);
    approvals.approve(own.getBusinessId(), a.getApprovalId(), "admin", "reviewed", admin);
    assertThatThrownBy(() -> imports.confirm(batch, null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(ledger()).isZero();
    assertThat(
            approvals.commitApproved(own.getBusinessId(), a.getApprovalId(), admin).isCommitted())
        .isTrue();
    approvals.retryCommit(own.getBusinessId(), a.getApprovalId(), admin);
    assertThat(ledger()).isEqualTo(1);
    assertThat(imports.getBatch(batch).confirmedRows()).isEqualTo(1);
    var rejected = request(collected());
    approvals.reject(own.getBusinessId(), rejected.getApprovalId(), "admin", "reject", admin);
    assertThatThrownBy(
            () -> approvals.commitApproved(own.getBusinessId(), rejected.getApprovalId(), admin))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ledger()).isEqualTo(1);
  }

  @Test
  void givenForeignBusinessOrForgedRunBatch_whenDedicatedApproval_thenNoLedgerWrite() {
    var c = collected();
    var a = request(c);
    assertThatThrownBy(
            () ->
                approvals.approveAndCommit(
                    other.getBusinessId(), a.getApprovalId(), "admin", "wrong", admin))
        .isInstanceOf(SecurityException.class);
    var outsider = new JwtTokenProvider.AuthenticatedPrincipal(999999L, "outsider", "TUTOR");
    assertThatThrownBy(
            () ->
                approvals.approveAndCommit(
                    own.getBusinessId(), a.getApprovalId(), "outsider", "wrong", outsider))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    // Corrupt the server-side batch link deliberately: even approved run cannot commit a foreign
    // batch.
    var foreign =
        imports.stage(
            new EasyImportBatchRequest(
                other.getBusinessId(),
                "normal.csv",
                "CSV",
                List.of(
                    new EasyImportBatchRequest.EasyImportRowInput(
                        "2026-10-01",
                        "EXPENSE",
                        12L,
                        "test",
                        "test",
                        "카드",
                        null,
                        null,
                        null,
                        "{}"))));
    db.update(
        "UPDATE easy_collection_runs SET import_batch_id=? WHERE run_id=?",
        foreign.batchId(),
        c.run().runId());
    var failed =
        approvals.approveAndCommit(
            own.getBusinessId(), a.getApprovalId(), "admin", "reviewed", admin);
    assertThat(failed.getStatus()).isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED);
    assertThat(ledger()).isZero();
    assertThat(imports.getBatch(foreign.batchId()).confirmedRows()).isZero();
  }

  @Test
  void givenReservedSourceWithoutRunOrTamperedSource_whenConfirm_thenFailClosed() {
    var orphan =
        imports.stage(
            new EasyImportBatchRequest(
                own.getBusinessId(),
                "orphan",
                "FINANCIAL_FILE",
                List.of(
                    new EasyImportBatchRequest.EasyImportRowInput(
                        "2026-10-01",
                        "EXPENSE",
                        12L,
                        "test",
                        "test",
                        "카드",
                        null,
                        null,
                        null,
                        "{}"))));
    assertThatThrownBy(() -> imports.confirm(orphan.batchId(), null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    var c = collected();
    var a = request(c);
    db.update(
        "UPDATE easy_import_batches SET source_kind='CSV' WHERE batch_id=?",
        c.run().importBatchId());
    assertThatThrownBy(() -> imports.confirm(c.run().importBatchId(), null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(
            approvals
                .approveAndCommit(
                    own.getBusinessId(), a.getApprovalId(), "admin", "reviewed", admin)
                .getStatus())
        .isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED);
    assertThat(ledger()).isZero();
  }

  @Test
  void givenOrdinaryCsv_whenGenericConfirm_thenExistingLedgerRegistrationStillWorks() {
    var batch =
        imports.stage(
            new EasyImportBatchRequest(
                own.getBusinessId(),
                "normal.csv",
                "CSV",
                List.of(
                    new EasyImportBatchRequest.EasyImportRowInput(
                        "2026-10-01",
                        "EXPENSE",
                        12000L,
                        "test",
                        "ordinary",
                        "카드",
                        null,
                        null,
                        null,
                        "{}"))));
    assertThat(imports.confirm(batch.batchId(), null).confirmedRows()).isEqualTo(1);
    assertThat(ledger()).isEqualTo(1);
  }
}
