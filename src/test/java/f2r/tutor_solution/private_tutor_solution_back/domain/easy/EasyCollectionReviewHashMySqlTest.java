package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Financial positive control and approval-to-commit review hash revalidation, real MySQL. */
@SpringBootTest
class EasyCollectionReviewHashMySqlTest {
  @Autowired EasyCollectionIngestService ingest;
  @Autowired EasyCollectionApprovalService approvals;
  @Autowired EasyCollectionApprovalRepository approvalRows;
  @Autowired EasyImportService imports;
  @Autowired EasyBusinessRepository businesses;
  @Autowired JdbcTemplate db;
  @MockitoBean EasyTutorAccessService access;
  private final AuthenticatedPrincipal owner = new AuthenticatedPrincipal(10L, "owner-a", "TUTOR");
  private EasyBusiness business;

  @BeforeEach
  void givenOwnedBusiness() {
    business =
        businesses.save(
            EasyBusiness.builder()
                .code("HASH_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Synthetic hash test")
                .active(true)
                .build());
    when(access.requireAllowedBusiness(any(), eq(business.getBusinessId()))).thenReturn(business);
  }

  private EasyCollectionApproval staged() {
    var row =
        new CollectionParsedRow(
            1,
            Map.of(),
            "{\"business\":\"PUBLISHING_CONFIRMED\"}",
            LocalDate.of(2026, 10, 1),
            "EXPENSE",
            12345L,
            "synthetic",
            "review hash",
            "TRANSFER",
            null,
            null,
            "NONE",
            null,
            "hash-" + UUID.randomUUID(),
            null);
    var run =
        ingest
            .ingestFinancialDraft(
                business.getBusinessId(),
                UUID.randomUUID().toString(),
                "FIN_" + "a".repeat(24),
                List.of(row))
            .run();
    return approvals.requestApproval(business.getBusinessId(), run.runId(), "owner-a", owner);
  }

  private long ledger() {
    return db.queryForObject(
        "SELECT COUNT(*) FROM easy_transactions WHERE business_id=?",
        Long.class,
        business.getBusinessId());
  }

  private Long batch(EasyCollectionApproval a) {
    return db.queryForObject(
        "SELECT import_batch_id FROM easy_collection_runs WHERE run_id=(SELECT run_id FROM"
            + " easy_collection_approvals WHERE approval_id=?)",
        Long.class,
        a.getApprovalId());
  }

  @Test
  void
      givenFinancialDraft_whenHashMissingOrWrong_thenStayPendingButCorrectHashCommitsOnceToOwnedBusiness() {
    var a = staged();
    Long batch = batch(a);
    for (String note : List.of("reviewed", EasyImportReviewHash.NOTE_PREFIX + "0".repeat(64))) {
      assertThatThrownBy(
              () ->
                  approvals.approveAndCommit(
                      business.getBusinessId(), a.getApprovalId(), "owner-a", note, owner))
          .hasMessageContaining("REVIEW_CHANGED_OR_NO_BATCH");
      assertThat(approvalRows.findById(a.getApprovalId()).orElseThrow().getStatus())
          .isEqualTo(EasyCollectionApproval.ApprovalStatus.PENDING);
      assertThat(ledger()).isZero();
    }
    String note =
        EasyImportReviewHash.NOTE_PREFIX + EasyImportReviewHash.of(imports.getBatch(batch));
    assertThat(
            approvals
                .approveAndCommit(
                    business.getBusinessId(), a.getApprovalId(), "owner-a", note, owner)
                .isCommitted())
        .isTrue();
    approvals.retryCommit(business.getBusinessId(), a.getApprovalId(), owner);
    assertThat(ledger()).isEqualTo(1);
    assertThat(imports.getBatch(batch).confirmedRows()).isEqualTo(1);
  }

  @Test
  void
      givenApprovedFinancialDraft_whenDbContentChangesBeforeCommit_thenHashRecheckedUnderLockAndLedgerUnchanged() {
    var a = staged();
    Long batch = batch(a);
    String note =
        EasyImportReviewHash.NOTE_PREFIX + EasyImportReviewHash.of(imports.getBatch(batch));
    approvals.approve(business.getBusinessId(), a.getApprovalId(), "owner-a", note, owner);
    db.update(
        "UPDATE easy_import_rows SET description='synthetic stale review' WHERE batch_id=?", batch);
    assertThat(
            approvals
                .commitApproved(business.getBusinessId(), a.getApprovalId(), owner)
                .getStatus())
        .isEqualTo(EasyCollectionApproval.ApprovalStatus.COMMIT_FAILED);
    assertThat(ledger()).isZero();
  }
}
