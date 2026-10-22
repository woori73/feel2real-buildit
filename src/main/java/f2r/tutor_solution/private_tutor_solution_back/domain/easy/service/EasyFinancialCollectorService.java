package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFinancialCollectorDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local & !prd")
@ConditionalOnProperty(name = "app.easy.financial-collector.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EasyFinancialCollectorService {
  private final EasyTutorAccessService access;
  private final EasyFinancialFileParser parser;
  private final EasyFinancialReportBuilder reports;
  private final EasyFinancialVault vault;
  private final EasyCollectionIngestService ingest;
  private final EasyImportService imports;
  private final EasyCollectionApprovalService approvalService;
  private final EasyCollectionApprovalRepository approvals;

  public Status upload(Long businessId, Scope scope, String filename, byte[] bytes,
      boolean userApprovedUpload, AuthenticatedPrincipal principal) {
    access.requireAllowedBusiness(principal, businessId);
    reports.validateScope(scope);
    if (!userApprovedUpload) throw new IllegalArgumentException("USER_UPLOAD_CONSENT_REQUIRED");
    if (bytes.length == 0 || bytes.length > EasyFinancialFileParser.MAX_BYTES) {
      throw new IllegalArgumentException("FILE_SIZE_INVALID");
    }
    String extension = filename == null || !filename.contains(".") ? ""
        : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (!List.of("csv", "xls", "xlsx", "pdf").contains(extension)) {
      throw new IllegalArgumentException("UNSUPPORTED_FORMAT");
    }
    List<CollectionParsedRow> rows;
    Report report;
    try {
      rows = parser.parse(bytes, extension);
      report = reports.build(scope, rows);
    } catch (RuntimeException e) {
      rows = List.of();
      // Parser details can contain workbook content; do not expose arbitrary exception messages.
      report = new Report("FAILED", 0, 0, 0, 0, 0, 0,
          List.of("PARSER_REJECTED", "USE_VERIFIED_TEMPLATE_OR_MANUAL_REVIEW"), List.of());
    }
    Snapshot snapshot = new Snapshot(UUID.randomUUID().toString(), businessId, scope, extension,
        sha256(bytes), Instant.now().toString(), actor(principal), bytes, rows, report);
    vault.save(snapshot);
    vault.audit(businessId, snapshot.collectionId(), "UPLOAD_AUTHORIZED", actor(principal), null);
    vault.audit(businessId, snapshot.collectionId(), "PREVIEW_" + report.status(), actor(principal), null);
    return status(snapshot);
  }

  public Status get(Long businessId, String id, AuthenticatedPrincipal principal) {
    access.requireAllowedBusiness(principal, businessId);
    return status(vault.read(businessId, id));
  }

  /** Serialized only for this single-node local prototype. No ledger write occurs here. */
  public synchronized Status stage(Long businessId, String id, boolean reviewed,
      AuthenticatedPrincipal principal) {
    access.requireAllowedBusiness(principal, businessId);
    Snapshot snapshot = vault.read(businessId, id);
    if (!reviewed) throw new IllegalArgumentException("USER_DRAFT_REVIEW_REQUIRED");
    if (snapshot.scope().dataset() != Dataset.BANK_ACCOUNT) {
      throw new IllegalArgumentException("DEDICATED_ACCOUNTING_ROUTE_REQUIRED");
    }
    if (snapshot.report().valid() == 0 || snapshot.report().issues().contains("EXTERNAL_ID_CONFLICT")) {
      throw new IllegalArgumentException("DRAFT_NOT_STAGEABLE");
    }
    vault.audit(businessId, id, "STAGE_AUTHORIZED", actor(principal), null);
    try {
      List<CollectionParsedRow> rows = snapshot.rows().stream().map(row -> {
        boolean inPeriod = row.occurredOn() != null
            && !row.occurredOn().isBefore(java.time.LocalDate.parse(snapshot.scope().from()))
            && !row.occurredOn().isAfter(java.time.LocalDate.parse(snapshot.scope().to()));
        return new CollectionParsedRow(row.rowNo(), row.originalRow(), row.rawJson(), row.occurredOn(),
            row.direction(), row.amount(), row.counterparty(), row.description(), row.payMethod(),
            row.supplyAmount(), row.vatAmount(), row.evidenceType(), row.externalAccount(),
            !row.isValid() || !inPeriod ? "INVALID_" + id + "_" + row.rowNo()
                : row.externalId() == null ? null : reports.externalKey(snapshot.scope(), row),
            !row.isValid() ? row.error() : inPeriod ? null : "OUTSIDE_REQUESTED_PERIOD");
      }).toList();
      var result = ingest.ingestFinancialDraft(businessId, id, reports.scopeKey(snapshot.scope()), rows);
      vault.audit(businessId, id, "STAGE_COMPLETED", actor(principal), result.run().runId());
      return status(snapshot);
    } catch (RuntimeException e) {
      vault.audit(businessId, id, "STAGE_FAILED_OR_RECONCILE_REQUIRED", actor(principal), null);
      throw e;
    }
  }

  /** Human command only; Task A's autonomous tool list must not include this endpoint. */
  public synchronized Status approveAndCommit(Long businessId, String id, boolean userApproved,
      String reviewHash, AuthenticatedPrincipal principal) {
    access.requireAllowedBusiness(principal, businessId);
    Snapshot snapshot = vault.read(businessId, id);
    if (!userApproved) throw new IllegalArgumentException("USER_LEDGER_APPROVAL_REQUIRED");
    if (snapshot.scope().dataset() != Dataset.BANK_ACCOUNT) {
      throw new IllegalArgumentException("DEDICATED_ACCOUNTING_ROUTE_REQUIRED");
    }
    Status current = status(snapshot);
    if ("COMMITTED".equals(current.approvalStatus())) return current;
    if (current.batch() == null || current.reviewHash() == null || !current.reviewHash().equals(reviewHash)) {
      throw new IllegalArgumentException("REVIEW_CHANGED_OR_NO_BATCH");
    }
    vault.audit(businessId, id, "LEDGER_COMMIT_AUTHORIZED", actor(principal), current.run().runId());
    try {
      var approval = approvalService.requestApproval(businessId, current.run().runId(), actor(principal), principal);
      EasyCollectionApproval result;
      if (approval.getStatus() == EasyCollectionApproval.ApprovalStatus.PENDING) {
        result = approvalService.approveAndCommit(businessId, approval.getApprovalId(), actor(principal),
            "financial-collector reviewed batch " + reviewHash, principal);
      } else {
        result = approvalService.commitApproved(businessId, approval.getApprovalId(), principal);
      }
      vault.audit(businessId, id, "LEDGER_" + result.getStatus().name(), actor(principal), current.run().runId());
      return status(snapshot);
    } catch (RuntimeException e) {
      vault.audit(businessId, id, "LEDGER_FAILED_OR_RECONCILE_REQUIRED", actor(principal), current.run().runId());
      throw e;
    }
  }

  private Status status(Snapshot snapshot) {
    EasyCollectionRunResponse run = ingest.listRuns(snapshot.businessId()).stream()
        .filter(candidate -> "FINANCIAL_FILE".equals(candidate.adapterCode())
            && ("financial-" + snapshot.collectionId()).equals(candidate.sourceFilename()))
        .findFirst().orElse(null);
    EasyImportBatchResponse batch = run == null || run.importBatchId() == null
        ? null : imports.getBatch(run.importBatchId());
    if (batch != null && !snapshot.businessId().equals(batch.businessId())) {
      throw new SecurityException("BATCH_BUSINESS_MISMATCH");
    }
    long saved = batch == null ? 0 : batch.confirmedRows();
    var approval = run == null ? null : approvals.findByRun_RunId(run.runId()).orElse(null);
    String reviewHash = null;
    if (batch != null) {
      try {
        reviewHash = sha256(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
            .writeValueAsBytes(batch));
      } catch (java.io.IOException e) { throw new IllegalStateException("BATCH_REVIEW_HASH_FAILED"); }
    }
    String next = approval != null && approval.isCommitted() ? "LEDGER_COMMITTED_CHECK_REMAINING_REVIEW"
        : snapshot.report().valid() == 0 ? "MANUAL_REVIEW_OR_REUPLOAD"
        : run != null && run.importBatchId() == null ? "NO_NEW_DRAFTS_CHECK_DUPLICATES_AND_ERRORS"
        : run != null ? "REQUEST_EXISTING_COLLECTION_APPROVAL_AFTER_BATCH_REVIEW"
        : snapshot.scope().dataset() == Dataset.BANK_ACCOUNT ? "REVIEW_THEN_STAGE"
        : snapshot.scope().dataset().postingRoute;
    return new Status(snapshot.collectionId(), snapshot.businessId(), snapshot.scope().dataset().institution,
        snapshot.scope().dataset().name(), snapshot.scope().dataset().postingRoute, snapshot.sha256(),
        snapshot.uploadedAt(), snapshot.report(), run, batch, saved,
        approval == null ? null : approval.getApprovalId(),
        approval == null ? null : approval.getStatus().name(), reviewHash, next);
  }

  private String actor(AuthenticatedPrincipal principal) {
    return String.valueOf(principal.userId());
  }

  private String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
