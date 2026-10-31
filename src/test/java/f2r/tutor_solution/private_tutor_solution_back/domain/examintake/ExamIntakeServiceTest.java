package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class ExamIntakeServiceTest {
  @TempDir Path directory;
  ExamIntakeRepository repo;
  ExamPrivateFiles files;
  ExamIntakeService service;
  TransactionTemplate tx;
  final AuthenticatedPrincipal owner =
      new AuthenticatedPrincipal(1L, "local-owner", "STUDENT", null);
  final AuthenticatedPrincipal other = new AuthenticatedPrincipal(2L, "other", "STUDENT", null);
  final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(3L, "local-admin", "ADMIN", null);

  @BeforeEach
  void setup() throws Exception {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:"
                + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa",
            "");
    new ResourceDatabasePopulator(new FileSystemResource("docs/home-exam/proposed-schema.sql"))
        .execute(ds);
    repo = new ExamIntakeRepository(new JdbcTemplate(ds));
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    files = new ExamPrivateFiles(directory.toString(), "");
    service =
        new ExamIntakeService(
            repo,
            files,
            tx,
            "",
            new ExamBankService(repo.jdbc(), tx),
            true,
            new ExamGuardianConsentService(repo.jdbc(), tx));
  }

  Create input(String key) {
    return new Create(
        Kind.APPLICATION,
        new LinkedHashMap<>(
            Map.of(
                "name",
                "합성 신청자",
                "school",
                "테스트중",
                "grade",
                "중3",
                "contactMethod",
                "EMAIL",
                "contact",
                "synthetic@example.invalid",
                "examDate",
                "2030-12-01",
                "scope",
                "합성 시험 범위",
                "pastPapers",
                "YES",
                "questionTypes",
                "독해",
                "difficulty",
                "중간")) {
          {
            put("format", "A4");
            put("ageBand", "OVER14");
            put("privacyVersion", PRIVACY);
            put("termsVersion", TERMS);
            put("termsAccepted", "true");
            put("optionalConsent", "true");
          }
        },
        true,
        CONSENT,
        key);
  }

  Request create() {
    return service.create(owner, input(UUID.randomUUID().toString()));
  }

  @Test
  void simplifiedApplicationAcceptsOnlyVisibleRequiredFields() {
    var original = input(UUID.randomUUID().toString());
    var details = new LinkedHashMap<>(original.details());
    for (var key : List.of("examDate", "scope", "pastPapers", "questionTypes", "difficulty", "ageBand"))
      details.remove(key);
    var result = service.create(owner, new Create(Kind.APPLICATION, details, true, CONSENT,
        UUID.randomUUID().toString()));
    assertThat(result.details().get("school")).isEqualTo("테스트중");
    assertThat(result.details().get("examDate")).isEmpty();
  }

  @Test
  void acceptsTwentyDocumentsAndRejectsTwentyFirst() throws Exception {
    var request = create();
    for (int i = 0; i < 20; i++) {
      var pixels = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
      pixels.setRGB(0, 0, i + 1);
      var bytes = new ByteArrayOutputStream();
      javax.imageio.ImageIO.write(pixels, "png", bytes);
      service.upload(owner, request.id(), new MockMultipartFile("file", "synthetic-" + i + ".png",
          "image/png", bytes.toByteArray()));
    }
    assertThat(repo.documents(request.id())).hasSize(20);
    status(400, () -> service.upload(owner, request.id(), image()));
    assertThat(repo.documents(request.id())).hasSize(20);
  }

  @Test
  void privateStorageRejectsPublicDirectoryAndSymlinkBeforeCreatingFiles() throws Exception {
    for (var path :
        List.of(
            directory.resolve("static"),
            directory.resolve("static/sub"),
            directory.resolve("agent-media"))) {
      assertThatThrownBy(() -> new ExamPrivateFiles(path.toString(), ""))
          .isInstanceOf(IllegalStateException.class);
      assertThat(Files.exists(path)).isFalse();
    }
    var link = directory.resolve("link");
    Files.createSymbolicLink(link, directory);
    assertThatThrownBy(() -> new ExamPrivateFiles(link.resolve("private").toString(), ""))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Files.exists(directory.resolve("private"))).isFalse();
  }

  void approvedBank() {
    var b =
        service.saveBank(
            admin, new Bank("TEST BANK", "NOT-A-REAL-ACCOUNT", "LOCAL FIXTURE", false, 0));
    service.approveBank(admin, b.version());
  }

  Request production() {
    var r = create();
    approvedBank();
    r = service.paymentRequested(owner, r.id(), r.version(), "합성 입금자");
    r = service.adminState(admin, r.id(), r.version(), State.CONFIRMED);
    return service.adminState(admin, r.id(), r.version(), State.PRODUCTION);
  }

  static void status(int code, org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
    assertThatThrownBy(work)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(code));
  }

  MockMultipartFile image() throws Exception {
    var out = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        out);
    return new MockMultipartFile("file", "synthetic.png", "image/png", out.toByteArray());
  }

  @Test
  void givenLostResponse_whenResolvingKey_thenOnlyOwnerFindsSingleStoredRequest() {
    var in = input("lost-response-12345678");
    assertThat(service.resolveSubmission(owner, in.idempotencyKey())).isEmpty();
    var r = service.create(owner, in);
    assertThat(service.resolveSubmission(owner, in.idempotencyKey()).orElseThrow().id())
        .isEqualTo(r.id());
    assertThat(service.resolveSubmission(other, in.idempotencyKey())).isEmpty();
    assertThat(service.resolveSubmission(admin, in.idempotencyKey())).isEmpty();
    status(401, () -> service.resolveSubmission(null, in.idempotencyKey()));
    assertThat(service.create(owner, in).id()).isEqualTo(r.id());
    assertThat(
            repo.jdbc().queryForObject("SELECT COUNT(*) FROM exam_intake_request", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void givenNotificationFailure_whenRetryingAndConfirming_thenReceiptAndDeliveryRemainDistinct() {
    var r = create();
    var event = repo.jdbc().queryForObject("SELECT id FROM exam_intake_event", String.class);
    assertThat(service.notificationStatus(owner, r.id()))
        .containsEntry("state", "PENDING")
        .containsEntry("received", false);
    status(404, () -> service.notificationStatus(other, r.id()));
    status(403, () -> service.retryNotification(owner, event));
    status(409, () -> service.retryNotification(admin, event));
    status(409, () -> service.confirmNotificationReceipt(admin, event));
    repo.jdbc().update("UPDATE exam_intake_event SET state='FAILED',attempts=8");
    service.retryNotification(admin, event);
    assertThat(repo.jdbc().queryForMap("SELECT state,attempts FROM exam_intake_event"))
        .containsEntry("state", "PENDING")
        .containsEntry("attempts", 0);
    repo.jdbc().update("UPDATE exam_intake_event SET state='SENT'");
    assertThat(service.notificationStatus(owner, r.id())).containsEntry("received", false);
    status(403, () -> service.confirmNotificationReceipt(owner, event));
    service.confirmNotificationReceipt(admin, event);
    service.confirmNotificationReceipt(admin, event);
    assertThat(service.notificationStatus(owner, r.id())).containsEntry("received", true);
    assertThat(repo.actions(r.id()).stream().filter(a -> a.kind().equals("NOTIFICATION_RECEIVED")))
        .hasSize(1);
    assertThat(repo.get(r.id(), false).state()).isEqualTo(State.RECEIVED);
  }

  @Test
  void givenPublishedPolicy_whenCreating_thenOldDraftConsentIsRejectedWithoutChangingHistory() {
    var in = input("published-policy-123456");
    var old = new LinkedHashMap<>(in.details());
    old.put("privacyVersion", "exam-privacy-20261029-v1");
    status(
        400,
        () ->
            service.create(owner, new Create(in.kind(), old, true, CONSENT, in.idempotencyKey())));
    assertThat(PRIVACY).isEqualTo("exam-privacy-20261030-v1");
    assertThat(TERMS).isEqualTo("exam-terms-20261030-v1");
  }

  @Test
  void givenThirtyDayUnpaidRequest_whenAdminReviewsRetention_thenCloseAndEraseOnlyLearningData() {
    var r = create();
    var retention = new ExamPrivacyRetentionService(repo, files, tx, PRIVACY);
    status(409, () -> retention.erase(admin, r.id(), r.version(), true, true));
    repo.jdbc()
        .update(
            "UPDATE exam_intake_request SET updated_at=? WHERE id=?",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(31L * 86400)),
            r.id());
    assertThat(retention.plan(admin)).anyMatch(row -> row.get("id").equals(r.id()));
    status(400, () -> retention.erase(admin, r.id(), r.version(), true, false));
    retention.erase(admin, r.id(), r.version(), true, true);
    var erased = repo.get(r.id(), false);
    assertThat(erased.state()).isEqualTo(State.CANCELLED);
    assertThat(erased.details().get("school")).isEmpty();
    assertThat(erased.offer()).isEqualTo(r.offer());
    assertThat(retention.plan(admin)).isEmpty();
  }

  @Test
  void givenCurrentOffer_whenCreatedAndRetried_thenOneSnapshotAndEvent() {
    var in = input("same-key-123456789");
    var r = service.create(owner, in);
    var again = service.create(owner, in);
    assertThat(again.id()).isEqualTo(r.id());
    assertThat(r.offer())
        .containsEntry("durationDays", 14)
        .containsEntry("totalDeliveries", 4)
        .containsEntry("totalQuestions", 160)
        .containsEntry("priceWon", 20000);
    assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM exam_intake_event", Integer.class))
        .isEqualTo(1);
    var changed = new LinkedHashMap<>(in.details());
    changed.put("school", "다른 학교");
    status(
        409,
        () ->
            service.create(
                owner, new Create(in.kind(), changed, true, CONSENT, in.idempotencyKey())));
  }

  @Test
  void givenHistoricalSnapshot_whenReadingThenTermsAreNotRecomputed() {
    var r = create();
    repo.jdbc()
        .update(
            "UPDATE exam_intake_request SET offer_snapshot=? WHERE id=?",
            repo.json(
                Map.of(
                    "durationDays",
                    21,
                    "totalDeliveries",
                    6,
                    "questionsPerDelivery",
                    40,
                    "totalQuestions",
                    240,
                    "priceWon",
                    20000)),
            r.id());
    assertThat(((Request) service.detail(owner, r.id()).get("request")).offer())
        .containsEntry("durationDays", 21)
        .containsEntry("totalQuestions", 240);
    assertThat(create().offer()).containsEntry("durationDays", 14);
  }

  @Test
  void givenMissingConsentOrFields_whenSubmitting_thenReject() {
    var in = input("validation-key-123");
    status(
        400,
        () ->
            service.create(
                owner, new Create(in.kind(), in.details(), false, CONSENT, in.idempotencyKey())));
    status(
        400,
        () ->
            service.create(
                owner, new Create(in.kind(), Map.of(), true, CONSENT, in.idempotencyKey())));
    status(401, () -> service.create(null, in));
  }

  @Test
  void givenAnotherOwner_whenReadingOrRequesting_thenNotFound() {
    var r = create();
    status(404, () -> service.detail(other, r.id()));
    status(404, () -> service.requestAction(other, r.id(), 0, "REVISION", "wrong owner"));
    status(403, () -> service.list(owner, 0, true));
    assertThat(service.list(other, 0, false)).isEmpty();
  }

  @Test
  void givenUnapprovedBank_whenPaymentRequested_thenNoConfirmation() {
    var r = create();
    status(409, () -> service.paymentRequested(owner, r.id(), 0, "합성 입금자"));
    service.saveBank(admin, new Bank("TEST BANK", "NOT-A-REAL-ACCOUNT", "LOCAL FIXTURE", true, 0));
    assertThat(service.publicBank(owner)).isEmpty();
    status(409, () -> service.paymentRequested(owner, r.id(), 0, "합성 입금자"));
  }

  @Test
  void givenPaymentButton_whenClicked_thenOnlyAdminCanConfirm() {
    var r = create();
    approvedBank();
    r = service.paymentRequested(owner, r.id(), 0, "합성 입금자");
    assertThat(r.state()).isEqualTo(State.CONFIRMATION_REQUESTED);
    String id = r.id();
    long version = r.version();
    status(403, () -> service.adminState(owner, id, version, State.CONFIRMED));
    r = service.adminState(admin, id, version, State.CONFIRMED);
    assertThat(r.state()).isEqualTo(State.CONFIRMED);
    status(409, () -> service.adminState(admin, id, version, State.CONFIRMED));
  }

  @Test
  void givenFourDeliveries_whenRecorded_thenCompleteOnlyAfterAll() {
    var r = production();
    String id = r.id();
    long old = r.version();
    status(409, () -> service.delivered(admin, id, old, 2, "건너뛰기"));
    for (int i = 1; i <= 4; i++) r = service.delivered(admin, id, r.version(), i, "합성 전달 기록");
    assertThat(r.state()).isEqualTo(State.DELIVERED);
    long v = r.version();
    status(409, () -> service.delivered(admin, id, v, 5, "초과"));
    assertThat(service.adminState(admin, id, v, State.COMPLETE).state()).isEqualTo(State.COMPLETE);
  }

  @Test
  void givenProduction_whenEditingOrDeleting_thenOnlyRevisionCancellationRequests() {
    var r = production();
    status(
        409, () -> service.requestAction(owner, r.id(), r.version(), "UPDATE_DETAILS", "시험 범위 변경"));
    var revised = service.requestAction(owner, r.id(), r.version(), "REVISION", "문제 수정 요청");
    assertThat(revised.state()).isEqualTo(State.PRODUCTION);
    service.requestAction(owner, r.id(), revised.version(), "CANCEL", "취소 검토 요청");
    assertThat(repo.get(r.id(), false).state()).isEqualTo(State.PRODUCTION);
  }

  @Test
  void givenRefundProposal_whenUnreviewed_thenNoAutomaticAmountOrApproval() {
    var r = create();
    r = service.requestAction(owner, r.id(), 0, "REFUND", "7일 내 취소 제안");
    var action = repo.actions(r.id()).getFirst();
    assertThat(action.amountWon()).isNull();
    r =
        service.resolveAction(
            admin, r.id(), r.version(), action.id(), "REVIEWING", null, "제공 상태 검토");
    String id = r.id();
    long v = r.version();
    status(409, () -> service.resolveAction(admin, id, v, action.id(), "APPROVED", 15000, "임의 승인"));
    assertThat(repo.actions(id).getFirst().amountWon()).isNull();
  }

  @Test
  void givenReviewPolicy_whenRefundRecorded_thenManualStagesAndNoTransfer() {
    var r = create();
    r = service.requestAction(owner, r.id(), 0, "REFUND", "합성 환불 요청");
    String action = repo.actions(r.id()).getFirst().id();
    var reviewed =
        new ExamIntakeService(
            repo,
            files,
            tx,
            "TEST-REVIEW-ONLY",
            new ExamBankService(repo.jdbc(), tx),
            true,
            new ExamGuardianConsentService(repo.jdbc(), tx));
    r = reviewed.resolveAction(admin, r.id(), r.version(), action, "REVIEWING", null, "테스트 검토");
    r =
        reviewed.resolveAction(
            admin, r.id(), r.version(), action, "APPROVED", 12000, "자동 계산 아닌 테스트 금액");
    r =
        reviewed.resolveAction(
            admin, r.id(), r.version(), action, "REMITTED", null, "테스트 완료 기록 — 송금 없음");
    assertThat(r.state()).isEqualTo(State.CANCELLED);
    assertThat(repo.actions(r.id()).getFirst().amountWon()).isEqualTo(12000);
  }

  @Test
  void givenScannerUnavailable_whenFileUploaded_thenQuarantineAndDedupeAndNoOwnerDownload()
      throws Exception {
    var r = create();
    service.upload(owner, r.id(), image());
    service.upload(owner, r.id(), image());
    var docs = repo.documents(r.id());
    assertThat(docs).hasSize(1);
    assertThat(docs.getFirst().scanState()).isEqualTo("QUARANTINED");
    status(403, () -> service.download(owner, r.id(), docs.getFirst().id()));
    status(409, () -> service.download(admin, r.id(), docs.getFirst().id()));
    try (var paths = Files.list(directory)) {
      assertThat(paths.toList()).hasSize(1);
    }
    status(404, () -> service.upload(other, r.id(), image()));
  }

  @Test
  void givenDisguisedOrOversizedFile_whenUploaded_thenRejectedAndTemporaryFilesCleaned()
      throws Exception {
    var r = create();
    status(
        400,
        () ->
            service.upload(
                owner,
                r.id(),
                new MockMultipartFile(
                    "file", "bad.pdf", "application/pdf", "<script>alert(1)</script>".getBytes())));
    status(
        400,
        () ->
            service.upload(
                owner,
                r.id(),
                new MockMultipartFile("file", "bad.html", "text/html", "text".getBytes())));
    status(
        400,
        () ->
            service.upload(
                owner,
                r.id(),
                new MockMultipartFile(
                    "file",
                    "big.png",
                    "image/png",
                    new byte[(int) ExamPrivateFiles.MAX_BYTES + 1])));
    try (var paths = Files.list(directory)) {
      assertThat(paths.toList()).isEmpty();
    }
  }

  @Test
  void givenActivePdf_whenUploaded_thenRejectedWithoutSaving() throws Exception {
    var r = create();
    byte[] bytes;
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
        var out = new ByteArrayOutputStream()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      pdf.getDocumentCatalog()
          .setOpenAction(
              new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript(
                  "app.alert('synthetic')"));
      pdf.save(out);
      bytes = out.toByteArray();
    }
    status(
        400,
        () ->
            service.upload(
                owner,
                r.id(),
                new MockMultipartFile("file", "active.pdf", "application/pdf", bytes)));
    assertThat(repo.documents(r.id())).isEmpty();
  }

  @Test
  void givenDbFailureAfterFileWrite_whenUpload_thenRollbackAndCleanup() throws Exception {
    var r = create();
    var broken = spy(repo);
    doThrow(new IllegalStateException("synthetic event write failure"))
        .when(broken)
        .event(any(), eq("document.uploaded"), anyString());
    var svc =
        new ExamIntakeService(
            broken,
            files,
            tx,
            "",
            new ExamBankService(broken.jdbc(), tx),
            true,
            new ExamGuardianConsentService(broken.jdbc(), tx));
    assertThatThrownBy(() -> svc.upload(owner, r.id(), image()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(repo.documents(r.id())).isEmpty();
    try (var paths = Files.list(directory)) {
      assertThat(paths.toList()).isEmpty();
    }
  }

  @Test
  void givenDeletionRequest_whenResolved_thenHideAndPreserveAuditAndFile() throws Exception {
    var r = create();
    service.upload(owner, r.id(), image());
    r = repo.get(r.id(), false);
    String doc = repo.documents(r.id()).getFirst().id();
    r = service.requestAction(owner, r.id(), r.version(), "DELETE_DOCUMENT", doc);
    String action =
        repo.actions(r.id()).stream()
            .filter(a -> a.kind().equals("DELETE_DOCUMENT"))
            .findFirst()
            .orElseThrow()
            .id();
    for (String state : List.of("REVIEWING", "APPROVED", "RESOLVED"))
      r = service.resolveAction(admin, r.id(), r.version(), action, state, null, "합성 삭제 요청 처리");
    assertThat(repo.documents(r.id()).getFirst().hidden()).isTrue();
    try (var paths = Files.list(directory)) {
      assertThat(paths.toList()).hasSize(1);
    }
  }

  @Test
  void givenCrossOriginOrMissingHeader_whenMutation_thenForbidden() {
    var request = new MockHttpServletRequest();
    request.setServerName("localhost");
    request.setServerPort(8080);
    status(403, () -> ExamIntakeController.mutation(request));
    request.addHeader("X-F2R-Exam", "1");
    request.addHeader("Origin", "https://attacker.invalid");
    status(403, () -> ExamIntakeController.mutation(request));
    request.removeHeader("Origin");
    request.addHeader("Origin", "http://localhost:8080");
    assertThatCode(() -> ExamIntakeController.mutation(request)).doesNotThrowAnyException();
  }

  @Test
  void givenSmtpFailure_whenNotifying_thenReceiptSurvivesAndRetryHasNoPersonalData() {
    var r = create();
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    var sender = mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(sender);
    when(provider.getObject()).thenReturn(sender);
    doThrow(new org.springframework.mail.MailSendException("synthetic"))
        .when(sender)
        .send(any(org.springframework.mail.SimpleMailMessage.class));
    new ExamIntakeNotifications(repo, tx, provider, true, "synthetic@example.invalid").tick();
    var event = repo.jdbc().queryForMap("SELECT * FROM exam_intake_event");
    assertThat(event.get("state")).isEqualTo("PENDING");
    assertThat(event.get("attempts")).isEqualTo(1);
    assertThat(repo.get(r.id(), false).id()).isEqualTo(r.id());
    var capture =
        org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
    verify(sender).send(capture.capture());
    assertThat(capture.getValue().getText())
        .contains(r.id())
        .doesNotContain("합성 신청자", "테스트중", "synthetic@example.invalid", "시험 범위");
  }

  @Test
  void givenDefaultAvailability_whenInspected_thenFeatureIsDisabled() {
    assertThat(new ExamIntakeAvailabilityAdvice(false).enabled()).isFalse();
  }

  @Test
  void givenSpringRepositoryProxy_whenCreating_thenAccessGoesThroughTargetMethods() {
    var factory = new org.springframework.aop.framework.ProxyFactory(repo);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        (org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
    var proxied = (ExamIntakeRepository) factory.getProxy();
    var svc =
        new ExamIntakeService(
            proxied,
            files,
            tx,
            "",
            new ExamBankService(proxied.jdbc(), tx),
            true,
            new ExamGuardianConsentService(proxied.jdbc(), tx));
    assertThat(svc.create(owner, input("proxy-regression-key")).state()).isEqualTo(State.RECEIVED);
  }

  @Test
  void givenApprovedChange_whenAdminApplies_thenDetailsChangeButContractDoesNot() {
    var r = create();
    r = service.requestAction(owner, r.id(), r.version(), "UPDATE_DETAILS", "시험 범위 변경");
    String action =
        repo.actions(r.id()).stream()
            .filter(a -> a.kind().equals("UPDATE_DETAILS"))
            .findFirst()
            .orElseThrow()
            .id();
    for (String state : List.of("REVIEWING", "APPROVED"))
      r = service.resolveAction(admin, r.id(), r.version(), action, state, null, "합성 검토");
    var details = new LinkedHashMap<>(r.details());
    details.put("scope", "수정된 합성 범위");
    var before = r.offer();
    r = service.amend(admin, r.id(), r.version(), action, details);
    assertThat(r.details().get("scope")).isEqualTo("수정된 합성 범위");
    assertThat(r.offer()).isEqualTo(before);
    assertThat(
            repo.actions(r.id()).stream()
                .filter(a -> a.id().equals(action))
                .findFirst()
                .orElseThrow()
                .state())
        .isEqualTo("RESOLVED");
  }

  @Test
  void givenCleanScannerTestDouble_whenRescanned_thenOnlyAdminCanDownload() throws Exception {
    var r = create();
    service.upload(owner, r.id(), image());
    String id = repo.documents(r.id()).getFirst().id();
    var clean =
        new ExamPrivateFiles(directory.toString(), "") {
          @Override
          String scan(Path p) {
            return "CLEAN";
          }
        };
    var svc =
        new ExamIntakeService(
            repo,
            clean,
            tx,
            "",
            new ExamBankService(repo.jdbc(), tx),
            true,
            new ExamGuardianConsentService(repo.jdbc(), tx));
    status(403, () -> svc.rescan(owner, r.id(), id));
    assertThat(svc.rescan(admin, r.id(), id)).isEqualTo("CLEAN");
    assertThat(svc.download(admin, r.id(), id).scanState()).isEqualTo("CLEAN");
    status(403, () -> svc.download(owner, r.id(), id));
  }

  @Test
  void givenOversizedOrUnboundedMultipart_whenFiltered_thenRejectedBeforeBodyParsing()
      throws Exception {
    for (long length : new long[] {-1, 12L * 1024 * 1024}) {
      var req =
          new MockHttpServletRequest("POST", "/api/exam-intake/requests/test/documents") {
            @Override
            public long getContentLengthLong() {
              return length;
            }
          };
      var response = new org.springframework.mock.web.MockHttpServletResponse();
      var chain = mock(jakarta.servlet.FilterChain.class);
      new ExamUploadLimitFilter().doFilter(req, response, chain);
      assertThat(response.getStatus()).isEqualTo(length < 0 ? 411 : 413);
      verifyNoInteractions(chain);
    }
  }

  @Test
  void givenDeliveries_whenEstimatingRefund_thenProposalOnlyAndHistoryUnchanged() {
    var r = production();
    for (int n = 0; n <= 4; n++) {
      assertThat(service.refundEstimate(r))
          .containsEntry("estimatedWon", 20000 - n * 5000)
          .containsEntry("finalLegalAmount", false);
      if (n < 4) r = service.delivered(admin, r.id(), r.version(), n + 1, "합성 검수·접근 확인");
    }
    repo.jdbc()
        .update(
            "UPDATE exam_intake_request SET offer_snapshot=? WHERE id=?",
            "{\"priceWon\":30000,\"totalDeliveries\":6}",
            r.id());
    assertThat(service.refundEstimate(repo.get(r.id(), false))).containsEntry("available", false);
    assertThat(repo.actions(r.id())).noneMatch(a -> a.amountWon() != null);
  }

  @Test
  void givenUnconfirmedDelivery_whenAdminRequests_thenNotCountedForRefund() {
    var r = production();
    var request =
        new MockHttpServletRequest(
            "POST", "/api/admin/exam-intake/requests/" + r.id() + "/deliveries");
    request.addHeader("X-F2R-Exam", "1");
    request.addHeader("Origin", "http://localhost");
    status(
        400,
        () ->
            new ExamIntakeController(service)
                .delivered(
                    admin,
                    r.id(),
                    new ExamIntakeController.DeliveryInput(r.version(), 1, "아직 확인 전", false),
                    request));
    assertThat(service.refundEstimate(r)).containsEntry("delivered", 0);
  }

  @Test
  void givenInquiry_whenViewingRefundGuide_thenNoChargeOrEstimatedRefund() {
    var in = input(UUID.randomUUID().toString());
    var details = new HashMap<>(in.details());
    details.put("inquiryType", "서비스 문의");
    details.put("message", "일정 문의");
    var r =
        service.create(
            owner, new Create(Kind.INQUIRY, details, true, CONSENT, in.idempotencyKey()));
    assertThat(service.refundEstimate(r))
        .containsEntry("available", false)
        .containsEntry("message", "문의 접수에는 이용료를 청구하지 않습니다.");
  }

  ExamGuardianConsentService guardians() {
    repo.jdbc().execute("CREATE TABLE IF NOT EXISTS users (user_id BIGINT PRIMARY KEY)");
    repo.jdbc().update("MERGE INTO users KEY(user_id) VALUES (1),(2),(3)");
    return new ExamGuardianConsentService(repo.jdbc(), tx);
  }

  Map<String, Object> guardian(ExamGuardianConsentService g) {
    return g.submit(
        owner,
        new ExamGuardianConsentService.Submission(
            "합성 보호자", "010-0000-0000", "PARENT", true, true, PRIVACY, TERMS));
  }

  Create child(String grant) {
    var in = input(UUID.randomUUID().toString());
    var d = new LinkedHashMap<>(in.details());
    d.put("ageBand", "UNDER14");
    d.put("guardianConsentId", grant);
    return new Create(in.kind(), d, true, CONSENT, in.idempotencyKey());
  }

  @Test
  void givenIntakeDisabled_whenAdminPreparesBank_thenNoFileStoreOrIntakeRequired() {
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withPropertyValues(
            "app.exam-intake.enabled=false",
            "app.exam-intake.bank-admin-enabled=true",
            "app.exam-intake.bank-public-enabled=true")
        .withBean(JdbcTemplate.class, () -> repo.jdbc())
        .withBean(TransactionTemplate.class, () -> tx)
        .withUserConfiguration(
            ExamBankService.class,
            ExamBankAdminController.class,
            ExamIntakeService.class,
            ExamPrivateFiles.class,
            ExamIntakeRepository.class)
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .doesNotHaveBean(ExamIntakeService.class)
                  .doesNotHaveBean(ExamPrivateFiles.class);
              var c = context.getBean(ExamBankAdminController.class);
              var request = new MockHttpServletRequest();
              request.addHeader("X-F2R-Exam", "1");
              var saved = c.save(admin, new Bank("합성은행", "000-test-only", "합성", true, 0), request);
              assertThat(saved.approved()).isFalse();
              c.approve(admin, new ExamBankAdminController.Approval(saved.version()), request);
              assertThat(c.read(admin).get("customerVisible")).isEqualTo(false);
              status(403, () -> c.read(owner));
              status(401, () -> c.read(null));
            });
  }

  @Test
  void givenUninstalledSchema_whenBankPreparationStarts_thenHealthyAndSanitizedFailure() {
    var jdbc =
        new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""));
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withPropertyValues(
            "app.exam-intake.enabled=false", "app.exam-intake.bank-admin-enabled=true")
        .withBean(JdbcTemplate.class, () -> jdbc)
        .withBean(TransactionTemplate.class, () -> tx)
        .withUserConfiguration(ExamBankService.class, ExamBankAdminController.class)
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              var controller = c.getBean(ExamBankAdminController.class);
              try {
                controller.read(admin);
                fail("missing schema must fail");
              } catch (org.springframework.dao.DataAccessException e) {
                var response = controller.unavailable(e);
                assertThat(response.getStatusCode().value()).isEqualTo(503);
                assertThat(response.getBody().toString()).doesNotContain("SELECT", "jdbc:");
              }
            });
  }

  @Test
  void givenBankPublicDisabled_whenReadingAllCustomerPaths_thenNoAccountLeak() {
    var b = service.saveBank(admin, new Bank("합성은행", "secret-test-account", "합성", false, 0));
    service.approveBank(admin, b.version());
    var r = create();
    service.paymentRequested(owner, r.id(), r.version(), "합성");
    var hidden =
        new ExamIntakeService(
            repo, files, tx, "", new ExamBankService(repo.jdbc(), tx), false, guardians());
    assertThat(hidden.publicBank(owner)).isEmpty();
    assertThat(hidden.detail(owner, r.id()).toString()).doesNotContain("secret-test-account");
    assertThat(hidden.detail(admin, r.id()).toString()).contains("secret-test-account");
    var next = create();
    status(409, () -> hidden.paymentRequested(owner, next.id(), next.version(), "합성"));
  }

  @Test
  void givenMissingSeparateTerms_whenSubmitting_thenRejectedBeforePersistence() {
    for (String key : List.of("privacyVersion", "termsAccepted", "termsVersion")) {
      var in = input(UUID.randomUUID().toString());
      var d = new LinkedHashMap<>(in.details());
      d.remove(key);
      status(
          400,
          () ->
              service.create(owner, new Create(in.kind(), d, true, CONSENT, in.idempotencyKey())));
    }
    assertThat(
            repo.jdbc().queryForObject("SELECT COUNT(*) FROM exam_intake_request", Integer.class))
        .isZero();
  }

  @Test
  void givenChildWithoutVerifiedGuardian_whenSubmitting_thenNoStudentRecord() {
    var g = guardians();
    String id = guardian(g).get("id").toString();
    status(409, () -> service.create(owner, child(id)));
    status(
        403,
        () -> g.verify(owner, id, new ExamGuardianConsentService.Verification(true, true, true)));
    status(
        400,
        () -> g.verify(admin, id, new ExamGuardianConsentService.Verification(true, true, false)));
    assertThat(
            repo.jdbc().queryForObject("SELECT COUNT(*) FROM exam_intake_request", Integer.class))
        .isZero();
    assertThat(g.list(owner, false).toString()).doesNotContain("테스트중", "합성 시험 범위");
  }

  @Test
  void givenTelephoneVerification_whenChildSubmits_thenSingleUseOwnerBoundConsentAndWithdrawal()
      throws Exception {
    var g = guardians();
    String id = guardian(g).get("id").toString();
    var verified =
        g.verify(admin, id, new ExamGuardianConsentService.Verification(true, true, true));
    assertThat(verified.get("verification_result")).isEqualTo("AGREED");
    assertThat(verified.get("reviewed_at")).isNotNull();
    assertThat(verified.get("consent_items").toString()).contains(PRIVACY, TERMS);
    status(409, () -> service.create(other, child(id)));
    var in = child(id);
    var r = service.create(owner, in);
    assertThat(service.create(owner, in).id()).isEqualTo(r.id());
    status(409, () -> service.create(owner, child(id)));
    status(404, () -> g.revoke(other, id));
    g.revoke(owner, id);
    status(409, () -> service.upload(owner, r.id(), image()));
    assertThat(repo.documents(r.id())).isEmpty();
    assertThat(
            service.requestAction(owner, r.id(), r.version(), "PRIVACY_REQUEST", "동의 철회 후 삭제 요청"))
        .isNotNull();
  }

  @Test
  void givenDeclinedExpiredOrOldPolicyConsent_whenSubmitting_thenRejected() {
    var g = guardians();
    for (String result : List.of("DECLINED", "UNVERIFIED")) {
      String id = guardian(g).get("id").toString();
      g.reject(admin, id, result);
      status(409, () -> service.create(owner, child(id)));
    }
    String id = guardian(g).get("id").toString();
    g.verify(admin, id, new ExamGuardianConsentService.Verification(true, true, true));
    repo.jdbc()
        .update(
            "UPDATE exam_intake_guardian_consent SET expires_at=? WHERE id=?",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
            id);
    status(409, () -> service.create(owner, child(id)));
    String old = guardian(g).get("id").toString();
    repo.jdbc()
        .update("UPDATE exam_intake_guardian_consent SET privacy_version='old' WHERE id=?", old);
    status(
        409,
        () -> g.verify(admin, old, new ExamGuardianConsentService.Verification(true, true, true)));
  }

  @Test
  void givenOptionalDataWithoutConsent_whenSubmitting_thenRejectButMinimalInquiryAllowed() {
    var in = input(UUID.randomUUID().toString());
    var d = new LinkedHashMap<>(in.details());
    d.put("requirements", "선택 요구사항");
    d.put("optionalConsent", "false");
    status(
        400,
        () -> service.create(owner, new Create(in.kind(), d, true, CONSENT, in.idempotencyKey())));
    d.put("requirements", "");
    d.put("school", "");
    d.put("grade", "");
    d.put("inquiryType", "일정");
    d.put("message", "합성 문의");
    d.put("termsAccepted", "false");
    assertThat(
            service
                .create(owner, new Create(Kind.INQUIRY, d, true, CONSENT, in.idempotencyKey()))
                .kind())
        .isEqualTo(Kind.INQUIRY);
  }

  @Test
  void givenRetentionReviewMissingOrOpenCase_whenErasing_thenNoDeletion() throws Exception {
    var r = create();
    service.upload(owner, r.id(), image());
    var retention = new ExamPrivacyRetentionService(repo, files, tx, "");
    status(403, () -> retention.plan(owner));
    status(409, () -> retention.erase(admin, r.id(), r.version(), true, true));
    try (var paths = Files.list(directory)) {
      assertThat(paths.count()).isEqualTo(1);
    }
    var enabled = new ExamPrivacyRetentionService(repo, files, tx, PRIVACY);
    status(409, () -> enabled.erase(admin, r.id(), r.version(), true, true));
  }

  @Test
  void givenExpiredClosedRequest_whenErasing_thenFilesRemovedAndContractPreserved()
      throws Exception {
    var b = service.saveBank(admin, new Bank("합성은행", "test-only", "합성", false, 0));
    service.approveBank(admin, b.version());
    var r = create();
    service.upload(owner, r.id(), image());
    r = repo.get(r.id(), false);
    service.paymentRequested(owner, r.id(), r.version(), "합성 입금자");
    r = repo.get(r.id(), false);
    r = service.requestAction(owner, r.id(), r.version(), "REFUND", "보존 대상 환불 사유");
    String refundId =
        repo.actions(r.id()).stream()
            .filter(a -> a.kind().equals("REFUND"))
            .findFirst()
            .orElseThrow()
            .id();
    service.resolveAction(admin, r.id(), r.version(), refundId, "REJECTED", null, "보존 대상 법적 판단 근거");
    repo.jdbc()
        .update(
            "UPDATE exam_intake_request SET state='COMPLETE',updated_at=? WHERE id=?",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(31L * 86400)),
            r.id());
    r = repo.get(r.id(), false);
    var retention = new ExamPrivacyRetentionService(repo, files, tx, PRIVACY);
    assertThat(retention.plan(admin)).hasSize(1);
    retention.erase(admin, r.id(), r.version(), true, true);
    try (var paths = Files.list(directory)) {
      assertThat(paths.count()).isZero();
    }
    assertThat(repo.get(r.id(), false).details().toString())
        .doesNotContain("synthetic@example.invalid", "합성 시험 범위");
    assertThat(repo.get(r.id(), false).offer().get("priceWon")).isEqualTo(20000);
    assertThat(
            repo.actions(r.id()).stream()
                .filter(a -> a.kind().equals("PAYMENT_CONFIRMATION"))
                .findFirst()
                .orElseThrow()
                .body())
        .contains("합성 입금자");
    assertThat(
            repo.actions(r.id()).stream()
                .filter(a -> a.kind().equals("REFUND"))
                .findFirst()
                .orElseThrow()
                .body())
        .contains("보존 대상 환불 사유");
    assertThat(
            repo.actions(r.id()).stream()
                .filter(a -> a.kind().equals("ACTION_REVIEW"))
                .findFirst()
                .orElseThrow()
                .body())
        .contains("보존 대상 법적 판단 근거");
    assertThat(retention.plan(admin)).isEmpty();
  }

  @Test
  void givenFileErasureFailure_whenRetried_thenNoFalseSuccessAndResourcesPreserved()
      throws Exception {
    var initial = create();
    service.upload(owner, initial.id(), image());
    var r = repo.get(initial.id(), false);
    repo.jdbc()
        .update(
            "UPDATE exam_intake_request SET state='CANCELLED',updated_at=? WHERE id=?",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(31L * 86400)),
            r.id());
    var broken = spy(files);
    doThrow(new IOException("synthetic")).when(broken).discard(anyString());
    var retention = new ExamPrivacyRetentionService(repo, broken, tx, PRIVACY);
    status(503, () -> retention.erase(admin, r.id(), r.version(), true, true));
    assertThat(repo.documents(r.id()).getFirst().hidden()).isTrue();
    assertThat(retention.plan(admin)).hasSize(1);
    status(
        409,
        () ->
            service.requestAction(owner, r.id(), r.version(), "PRIVACY_REQUEST", "during cleanup"));
    new ExamPrivacyRetentionService(repo, files, tx, PRIVACY)
        .erase(admin, r.id(), r.version(), true, true);
    assertThat(retention.plan(admin)).isEmpty();
  }

  @Test
  void givenCancelledRequest_whenSubjectRequestsErasure_thenRightsRemainAndNoMinimumRetention() {
    var r = create();
    repo.jdbc().update("UPDATE exam_intake_request SET state='CANCELLED' WHERE id=?", r.id());
    r = service.requestAction(owner, r.id(), r.version(), "PRIVACY_REQUEST", "개인정보 삭제 요청");
    var action =
        repo.actions(r.id()).stream()
            .filter(a -> a.kind().equals("PRIVACY_REQUEST"))
            .findFirst()
            .orElseThrow();
    for (String state : List.of("REVIEWING", "APPROVED", "RESOLVED"))
      r = service.resolveAction(admin, r.id(), r.version(), action.id(), state, null, "합성 요청 검토");
    new ExamPrivacyRetentionService(repo, files, tx, PRIVACY)
        .erase(admin, r.id(), r.version(), true, true);
    assertThat(repo.get(r.id(), false).details().get("contact")).isEmpty();
  }
}
