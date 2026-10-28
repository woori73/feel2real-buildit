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
    service = new ExamIntakeService(repo, files, tx, "");
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
    var reviewed = new ExamIntakeService(repo, files, tx, "TEST-REVIEW-ONLY");
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
    var svc = new ExamIntakeService(broken, files, tx, "");
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
    String action = repo.actions(r.id()).getFirst().id();
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
    var svc = new ExamIntakeService(proxied, files, tx, "");
    assertThat(svc.create(owner, input("proxy-regression-key")).state()).isEqualTo(State.RECEIVED);
  }

  @Test
  void givenApprovedChange_whenAdminApplies_thenDetailsChangeButContractDoesNot() {
    var r = create();
    r = service.requestAction(owner, r.id(), r.version(), "UPDATE_DETAILS", "시험 범위 변경");
    String action = repo.actions(r.id()).getFirst().id();
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
    var svc = new ExamIntakeService(repo, clean, tx, "");
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
}
