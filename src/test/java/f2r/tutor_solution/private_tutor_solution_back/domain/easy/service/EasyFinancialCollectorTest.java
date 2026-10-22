package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller.EasyFinancialCollectorController;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFinancialCollectorDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionApprovalRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.access.AccessDeniedException;

class EasyFinancialCollectorTest {
  private static final String HEADER = "date,direction,amount,counterparty,description,externalId";
  private static final String ROW = "2026-10-01,EXPENSE,12000,SAMPLE_SHOP,SAMPLE_PAPER,sample001";
  private static final byte[] CSV = (HEADER + "\n" + ROW + "\n").getBytes(StandardCharsets.UTF_8);
  private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "sample-admin", "ADMIN", null);
  private final EasyFinancialFileParser parser = new EasyFinancialFileParser(new EasyCsvFileAdapter());
  private final EasyFinancialReportBuilder reports = new EasyFinancialReportBuilder();
  @TempDir Path temp;

  private Scope scope(Dataset dataset) {
    return new Scope(dataset, "sample-account", "2026-10-01", "2026-10-31", 1, 12000L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"csv", "xls", "xlsx", "pdf"})
  void givenSyntheticTemplates_whenParsed_thenEquivalentTransactions(String extension) throws Exception {
    // given: entirely synthetic canonical templates, no real export or account.
    byte[] input = switch (extension) {
      case "csv" -> CSV;
      case "pdf" -> pdf(HEADER.replace(',', '|'), ROW.replace(',', '|'));
      default -> spreadsheet(extension, false);
    };
    // when / then
    var rows = parser.parse(input, extension);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().isValid()).isTrue();
    assertThat(rows.getFirst().amount()).isEqualTo(12000);
    assertThat(reports.build(scope(Dataset.BANK_ACCOUNT), rows).status()).isEqualTo("SUCCESS");
  }

  @Test
  void givenImpossibleDateAndTruncatedCsv_whenParsed_thenNeverSilentlyRepair() {
    var invalidDate = parser.parse(new String(CSV, StandardCharsets.UTF_8)
        .replace("2026-10-01", "2026-02-30").getBytes(StandardCharsets.UTF_8), "csv");
    assertThat(invalidDate.getFirst().error()).isEqualTo("INVALID_DATE");
    assertThatThrownBy(() -> parser.parse((HEADER + "\n\"truncated").getBytes(), "csv"))
        .hasMessage("CSV_UNCLOSED_QUOTE");
    assertThatThrownBy(() -> parser.parse((HEADER + "\n\"x\"bad,EXPENSE,1,x,x,x").getBytes(), "csv"))
        .hasMessage("CSV_INVALID_QUOTE");
    var korean = "거래일,구분,금액,거래처\n2026-10-01,지출,12000,샘플".getBytes(java.nio.charset.Charset.forName("MS949"));
    assertThat(parser.parse(korean, "csv").getFirst().counterparty()).isEqualTo("샘플");
  }

  @Test
  void givenFormulaAndScannedPdf_whenParsed_thenRequireManualAlternative() throws Exception {
    assertThatThrownBy(() -> parser.parse(spreadsheet("xlsx", true), "xlsx"))
        .hasMessage("FORMULA_NOT_ALLOWED");
    assertThatThrownBy(() -> parser.parse(pdf(), "pdf")).hasMessage("PDF_TEXT_TEMPLATE_REQUIRED");
    assertThatThrownBy(() -> parser.parse(new byte[EasyFinancialFileParser.MAX_BYTES + 1], "csv"))
        .hasMessage("FILE_SIZE_INVALID");
  }

  @Test
  void givenMissingControlsAndWrongPeriod_whenReported_thenPartialDoesNotClaimComplete() {
    var rows = parser.parse(CSV, "csv");
    var unverified = reports.build(new Scope(Dataset.BANK_ACCOUNT, "demo", "2026-10-01", "2026-10-31", null, null), rows);
    assertThat(unverified.status()).isEqualTo("PARTIAL");
    assertThat(unverified.issues()).contains("COMPLETENESS_UNVERIFIED");
    var wrong = reports.build(new Scope(Dataset.BANK_ACCOUNT, "demo", "2026-10-02", "2026-10-31", 2, 1L), rows);
    assertThat(wrong.status()).isEqualTo("FAILED");
    assertThat(wrong.failed()).isEqualTo(1);
    assertThat(wrong.issues()).contains("COUNT_MISMATCH", "TOTAL_MISMATCH", "ROW_1:OUTSIDE_REQUESTED_PERIOD");
  }

  @Test
  void givenRecurringApprovalNumbers_whenCompared_thenDayAndAccountRemainDistinct() {
    var rows = parser.parse((HEADER + "\n" + ROW + "\n" + ROW.replace("10-01", "10-02")).getBytes(), "csv");
    assertThat(reports.externalKey(scope(Dataset.BANK_ACCOUNT), rows.get(0)))
        .isNotEqualTo(reports.externalKey(scope(Dataset.BANK_ACCOUNT), rows.get(1)));
    assertThat(reports.scopeKey(scope(Dataset.BANK_ACCOUNT))).isNotEqualTo(reports.scopeKey(
        new Scope(Dataset.BANK_ACCOUNT, "another-account", "2026-10-01", "2026-10-31", 1, 12000L)));
    var conflict = parser.parse((HEADER + "\n" + ROW + "\n" + ROW.replace("12000", "13000")).getBytes(), "csv");
    assertThat(reports.build(scope(Dataset.BANK_ACCOUNT), conflict).issues()).contains("EXTERNAL_ID_CONFLICT");
  }

  @Test
  void givenEncryptedOriginal_whenTamperedOrMovedAcrossBusiness_thenFailClosed() throws Exception {
    var vault = new EasyFinancialVault(temp.resolve("vault").toString(), key());
    var snapshot = snapshot(Dataset.BANK_ACCOUNT);
    vault.save(snapshot);
    vault.audit(1L, snapshot.collectionId(), "UPLOAD_AUTHORIZED", "1", null);
    assertThat(vault.read(1L, snapshot.collectionId()).original()).isEqualTo(CSV);
    Path original = temp.resolve("vault/1-" + snapshot.collectionId() + "-original.enc");
    assertThat(new String(Files.readAllBytes(original), StandardCharsets.ISO_8859_1)).doesNotContain("SAMPLE_SHOP");
    assertThat(Files.getPosixFilePermissions(original)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    Path other = temp.resolve("vault/2-" + snapshot.collectionId() + "-original.enc");
    Files.copy(original, other);
    assertThatThrownBy(() -> vault.read(2L, snapshot.collectionId())).hasMessageContaining("TAMPERED");
    byte[] tampered = Files.readAllBytes(original);
    tampered[tampered.length - 1] ^= 1;
    Files.write(original, tampered);
    assertThatThrownBy(() -> vault.read(1L, snapshot.collectionId())).hasMessageContaining("TAMPERED");
    assertThatThrownBy(() -> vault.save(snapshot)).hasMessage("VAULT_WRITE_FAILED");
    assertThatThrownBy(() -> vault.read(1L, "../../escape")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void givenWeakKeyOrSymlink_whenOpeningVault_thenReject() throws Exception {
    assertThatThrownBy(() -> new EasyFinancialVault(temp.resolve("bad").toString(), ""))
        .hasMessageContaining("256_BITS");
    Files.createDirectory(temp.resolve("real"));
    Files.createSymbolicLink(temp.resolve("linked"), temp.resolve("real"));
    assertThatThrownBy(() -> new EasyFinancialVault(temp.resolve("linked").toString(), key()))
        .hasMessage("VAULT_SYMLINK_REJECTED");
  }

  @Test
  void givenNoBusinessPermission_whenReading_thenNeverReadVault() {
    var access = mock(EasyTutorAccessService.class);
    var vault = mock(EasyFinancialVault.class);
    when(access.requireAllowedBusiness(ADMIN, 2L)).thenThrow(new AccessDeniedException("denied"));
    var service = service(access, vault, mock(EasyCollectionIngestService.class), mock(EasyImportService.class));
    assertThatThrownBy(() -> service.get(2L, UUID.randomUUID().toString(), ADMIN)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(vault);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CARD_PURCHASE", "CARD_SALES", "CARD_SETTLEMENT", "TAX_INVOICE", "CASH_RECEIPT"})
  void givenNonBankDataset_whenStageRequested_thenNeverCreateGenericIncomeOrExpense(String dataset) {
    var vault = mock(EasyFinancialVault.class);
    var snapshot = snapshot(Dataset.valueOf(dataset));
    when(vault.read(1L, snapshot.collectionId())).thenReturn(snapshot);
    var ingest = mock(EasyCollectionIngestService.class);
    var imports = mock(EasyImportService.class);
    var service = service(mock(EasyTutorAccessService.class), vault, ingest, imports);
    assertThatThrownBy(() -> service.stage(1L, snapshot.collectionId(), true, ADMIN))
        .hasMessage("DEDICATED_ACCOUNTING_ROUTE_REQUIRED");
    verifyNoInteractions(ingest, imports);
  }

  @Test
  void givenMissingUploadConsent_whenUploading_thenNoPersistenceOrLedgerCalls() {
    var vault = mock(EasyFinancialVault.class);
    var ingest = mock(EasyCollectionIngestService.class);
    var imports = mock(EasyImportService.class);
    var service = service(mock(EasyTutorAccessService.class), vault, ingest, imports);
    assertThatThrownBy(() -> service.upload(1L, scope(Dataset.BANK_ACCOUNT), "sample.csv", CSV, false, ADMIN))
        .hasMessage("USER_UPLOAD_CONSENT_REQUIRED");
    verifyNoInteractions(vault, ingest, imports);
  }

  @Test
  void givenParserFailure_whenUploading_thenRetainEncryptedOriginalAndFailedReport() {
    var vault = new EasyFinancialVault(temp.resolve("vault").toString(), key());
    var ingest = mock(EasyCollectionIngestService.class);
    when(ingest.listRuns(1L)).thenReturn(List.of());
    var imports = mock(EasyImportService.class);
    var service = service(mock(EasyTutorAccessService.class), vault, ingest, imports);
    var status = service.upload(1L, scope(Dataset.BANK_ACCOUNT), "sample.pdf", CSV, true, ADMIN);
    assertThat(status.report().status()).isEqualTo("FAILED");
    assertThat(status.ledgerSaved()).isZero();
    assertThat(vault.read(1L, status.collectionId()).original()).isEqualTo(CSV);
    verifyNoInteractions(imports);
    assertThatThrownBy(() -> service.stage(1L, status.collectionId(), true, ADMIN)).hasMessage("DRAFT_NOT_STAGEABLE");
  }

  @Test
  void givenBankPreview_whenStaged_thenOnlyExistingIngestBoundaryRuns() {
    var snapshot = snapshot(Dataset.BANK_ACCOUNT);
    var vault = mock(EasyFinancialVault.class);
    when(vault.read(1L, snapshot.collectionId())).thenReturn(snapshot);
    var ingest = mock(EasyCollectionIngestService.class);
    var run = mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse.class);
    when(run.runId()).thenReturn(5L);
    when(ingest.ingestFinancialDraft(eq(1L), eq(snapshot.collectionId()), anyString(), anyList()))
        .thenReturn(new f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse(run, List.of()));
    var imports = mock(EasyImportService.class);
    var service = service(mock(EasyTutorAccessService.class), vault, ingest, imports);
    assertThatThrownBy(() -> service.stage(1L, snapshot.collectionId(), false, ADMIN))
        .hasMessage("USER_DRAFT_REVIEW_REQUIRED");
    service.stage(1L, snapshot.collectionId(), true, ADMIN);
    verify(ingest).ingestFinancialDraft(eq(1L), eq(snapshot.collectionId()), eq(reports.scopeKey(snapshot.scope())),
        argThat(rows -> rows.size() == 1 && rows.getFirst().externalId().length() == 64));
    verifyNoInteractions(imports);
  }

  @Test
  void givenReviewedBatch_whenExplicitlyApproved_thenReuseApprovalServiceAndAuditTrustedActor() {
    var snapshot = snapshot(Dataset.BANK_ACCOUNT);
    var vault = mock(EasyFinancialVault.class);
    when(vault.read(1L, snapshot.collectionId())).thenReturn(snapshot);
    var ingest = mock(EasyCollectionIngestService.class);
    var run = mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse.class);
    when(run.runId()).thenReturn(5L);
    when(run.importBatchId()).thenReturn(9L);
    when(run.sourceFilename()).thenReturn("financial-" + snapshot.collectionId());
    when(ingest.listRuns(1L)).thenReturn(List.of(run));
    var imports = mock(EasyImportService.class);
    var batch = new f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse(
        9L, 1L, "SAMPLE", "SAMPLE", "FINANCIAL_FILE", "STAGED", 1, 0, List.of());
    when(imports.getBatch(9L)).thenReturn(batch);
    var approvalService = mock(EasyCollectionApprovalService.class);
    var repository = mock(EasyCollectionApprovalRepository.class);
    var stored = new java.util.concurrent.atomic.AtomicReference<f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval>();
    when(repository.findByRun_RunId(5L)).thenAnswer(i -> java.util.Optional.ofNullable(stored.get()));
    var approval = mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval.class);
    when(approval.getApprovalId()).thenReturn(7L);
    when(approval.getStatus()).thenReturn(f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval.ApprovalStatus.PENDING);
    when(approvalService.requestApproval(eq(1L), eq(5L), eq("1"), eq(ADMIN))).thenReturn(approval);
    when(approvalService.approveAndCommit(eq(1L), eq(7L), eq("1"), anyString(), eq(ADMIN))).thenAnswer(i -> {
      var committed = mock(f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval.class);
      when(committed.getApprovalId()).thenReturn(7L);
      when(committed.getStatus()).thenReturn(f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionApproval.ApprovalStatus.COMMITTED);
      stored.set(committed);
      return committed;
    });
    var service = new EasyFinancialCollectorService(mock(EasyTutorAccessService.class), parser, reports,
        vault, ingest, imports, approvalService, repository);
    String hash = service.get(1L, snapshot.collectionId(), ADMIN).reviewHash();
    assertThatThrownBy(() -> service.approveAndCommit(1L, snapshot.collectionId(), false, hash, ADMIN))
        .hasMessage("USER_LEDGER_APPROVAL_REQUIRED");
    assertThatThrownBy(() -> service.approveAndCommit(1L, snapshot.collectionId(), true, "old-review", ADMIN))
        .hasMessage("REVIEW_CHANGED_OR_NO_BATCH");
    verifyNoInteractions(approvalService);
    assertThat(service.approveAndCommit(1L, snapshot.collectionId(), true, hash, ADMIN).approvalStatus()).isEqualTo("COMMITTED");
    service.approveAndCommit(1L, snapshot.collectionId(), true, hash, ADMIN);
    verify(approvalService, times(1)).approveAndCommit(eq(1L), eq(7L), eq("1"), contains(hash), eq(ADMIN));
    verify(vault).audit(1L, snapshot.collectionId(), "LEDGER_COMMITTED", "1", 5L);
    verify(imports, never()).confirm(any(), any());
  }

  @Test
  void givenPrdOrDisabledFeature_whenCreatingContext_thenNoCollectorApiOrVault() {
    for (String profile : List.of("prd", "local,prd", "local")) {
      new ApplicationContextRunner().withUserConfiguration(EasyFinancialCollectorController.class,
          EasyFinancialCollectorService.class, EasyFinancialVault.class)
          .withPropertyValues("spring.profiles.active=" + profile,
              "app.easy.financial-collector.enabled=" + !profile.equals("local"))
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EasyFinancialCollectorController.class);
            assertThat(context).doesNotHaveBean(EasyFinancialVault.class);
          });
    }
  }

  private EasyFinancialCollectorService service(EasyTutorAccessService access, EasyFinancialVault vault,
      EasyCollectionIngestService ingest, EasyImportService imports) {
    return new EasyFinancialCollectorService(access, parser, reports, vault, ingest, imports,
        mock(EasyCollectionApprovalService.class), mock(EasyCollectionApprovalRepository.class));
  }

  private Snapshot snapshot(Dataset dataset) {
    var rows = parser.parse(CSV, "csv");
    return new Snapshot(UUID.randomUUID().toString(), 1L, scope(dataset), "csv", "sample-hash",
        "2026-10-22T00:00:00Z", "1", CSV, rows, reports.build(scope(dataset), rows));
  }

  private String key() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private byte[] spreadsheet(String type, boolean formula) throws Exception {
    try (Workbook workbook = type.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("SAMPLE");
      int number = 0;
      for (String line : List.of(HEADER, ROW)) {
        var row = sheet.createRow(number++);
        String[] cells = line.split(",");
        for (int i = 0; i < cells.length; i++) row.createCell(i).setCellValue(cells[i]);
      }
      if (formula) sheet.getRow(1).getCell(2).setCellFormula("1+1");
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private byte[] pdf(String... lines) throws Exception {
    try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var stream = new PDPageContentStream(doc, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        stream.setLeading(14);
        stream.newLineAtOffset(20, 750);
        for (String line : lines) { stream.showText(line); stream.newLine(); }
        stream.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }
}
