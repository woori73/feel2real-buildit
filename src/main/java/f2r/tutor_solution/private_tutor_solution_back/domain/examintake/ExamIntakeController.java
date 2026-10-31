package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

@RestController
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeController {
  public record AmendInput(long version, String actionId, Map<String, String> details) {}

  @PostMapping("/api/admin/exam-intake/requests/{id}/details")
  Request amend(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody AmendInput body,
      HttpServletRequest request) {
    mutation(request);
    return service.amend(p, id, body.version(), body.actionId(), body.details());
  }

  private final ExamIntakeService service;

  public ExamIntakeController(ExamIntakeService service) {
    this.service = service;
  }

  public record ActionInput(long version, String kind, String body) {}

  public record PaymentInput(long version, String payer) {}

  public record StateInput(long version, State state) {}

  public record DeliveryInput(
      long version, int ordinal, String note, boolean accessibleAndReviewed) {}

  public record ReviewInput(long version, String state, Integer amount, String note) {}

  public record VersionInput(long version) {}

  public record SubmissionKey(String idempotencyKey) {}

  @PostMapping("/api/exam-intake/submissions/resolve")
  Map<String, Object> resolve(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody SubmissionKey body,
      HttpServletRequest request) {
    mutation(request);
    return service
        .resolveSubmission(p, body.idempotencyKey())
        .map(r -> Map.<String, Object>of("found", true, "request", r))
        .orElseGet(() -> Map.of("found", false));
  }

  @GetMapping("/api/exam-intake/requests/{id}/notification")
  Map<String, Object> notification(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id) {
    return service.notificationStatus(p, id);
  }

  @PostMapping("/api/admin/exam-intake/events/{id}/retry")
  Map<String, Boolean> retry(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      HttpServletRequest request) {
    mutation(request);
    service.retryNotification(p, id);
    return Map.of("queued", true);
  }

  @PostMapping("/api/admin/exam-intake/events/{id}/received")
  Map<String, Boolean> received(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Map<String, Boolean> body,
      HttpServletRequest request) {
    mutation(request);
    if (!Boolean.TRUE.equals(body.get("confirmedInMailbox")))
      throw error(400, "실제 수신함에서 해당 이벤트 ID의 메일을 확인해 주세요.");
    service.confirmNotificationReceipt(p, id);
    return Map.of("received", true);
  }

  @ModelAttribute
  void noCache(HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
  }

  static void mutation(HttpServletRequest request) {
    if (!"1".equals(request.getHeader("X-F2R-Exam"))
        || !WebUtils.isSameOrigin(new ServletServerHttpRequest(request))
        || (request.getHeader("Sec-Fetch-Site") != null
            && !"same-origin".equals(request.getHeader("Sec-Fetch-Site"))))
      throw error(403, "같은 사이트에서 다시 시도해 주세요.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> rejected(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", Objects.toString(e.getReason(), "요청을 처리할 수 없습니다.")));
  }

  @GetMapping("/api/exam-intake/config")
  Map<String, Object> config() {
    var o = HomeExamOffer.CURRENT;
    return Map.of(
        "enabled",
        true,
        "offer",
        o,
        "summary",
        o.getAssistantSummary(),
        "consentVersion",
        CONSENT,
        "privacyVersion",
        PRIVACY,
        "termsVersion",
        TERMS,
        "refundPolicy",
        "4회·20,000원 상품은 정상 전달된 회차당5,000원을 기준으로 잔여 이용료를 검토합니다. 법정 청약철회·사업자 귀책·서비스 하자는 별도 검토하며, 예정액은"
            + " 최종 법정 환불액이 아닙니다. 자동 공제·송금하지 않습니다.",
        "uploadPolicy",
        "PDF/JPEG/PNG, 파일당 10MiB·신청당 20개. 제출 파일은 관리자만 열람합니다. 검사 대기 파일은 열람할 수 없습니다.");
  }

  @GetMapping("/api/exam-intake/requests")
  List<Request> mine(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(p, page, false);
  }

  @GetMapping("/api/admin/exam-intake/requests")
  List<Request> all(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(p, page, true);
  }

  @PostMapping("/api/exam-intake/requests")
  Request create(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody Create body,
      HttpServletRequest request) {
    mutation(request);
    return service.create(p, body);
  }

  @GetMapping("/api/exam-intake/requests/{id}")
  Map<String, Object> detail(
      @AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable String id) {
    return service.detail(p, id);
  }

  @PostMapping("/api/exam-intake/requests/{id}/actions")
  Request action(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody ActionInput body,
      HttpServletRequest request) {
    mutation(request);
    return service.requestAction(p, id, body.version(), body.kind(), body.body());
  }

  @PostMapping("/api/exam-intake/requests/{id}/payment-confirmation")
  Request paid(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody PaymentInput body,
      HttpServletRequest request) {
    mutation(request);
    return service.paymentRequested(p, id, body.version(), body.payer());
  }

  @PostMapping("/api/exam-intake/requests/{id}/documents")
  Map<String, String> upload(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "false") boolean materialConsent,
      HttpServletRequest request)
      throws IOException {
    mutation(request);
    if (!materialConsent) throw error(400, "선택 자료 제출 동의를 확인해 주세요.");
    service.upload(p, id, file);
    return Map.of("message", "자료를 접수했습니다. 검사 상태를 확인해 주세요.");
  }

  @PostMapping("/api/admin/exam-intake/requests/{id}/documents/{document}/rescan")
  Map<String, String> rescan(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @PathVariable String document,
      HttpServletRequest request)
      throws IOException {
    mutation(request);
    return Map.of("scanState", service.rescan(p, id, document));
  }

  @GetMapping("/api/admin/exam-intake/requests/{id}/documents/{document}")
  ResponseEntity<FileSystemResource> download(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @PathVariable String document) {
    var d = service.download(p, id, document);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(d.originalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(MediaType.parseMediaType(d.contentType()))
        .body(new FileSystemResource(service.files.path(d.storageName())));
  }

  @PostMapping("/api/admin/exam-intake/requests/{id}/state")
  Request state(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody StateInput body,
      HttpServletRequest request) {
    mutation(request);
    return service.adminState(p, id, body.version(), body.state());
  }

  @PostMapping("/api/admin/exam-intake/requests/{id}/deliveries")
  Request delivered(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody DeliveryInput body,
      HttpServletRequest request) {
    mutation(request);
    if (!body.accessibleAndReviewed())
      throw error(400, "1차 수정·검수 후 문제지와 해설지의 전달 및 접근 가능 여부를 확인해 주세요.");
    return service.delivered(p, id, body.version(), body.ordinal(), body.note());
  }

  @PostMapping("/api/admin/exam-intake/requests/{id}/actions/{action}")
  Request reviewed(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @PathVariable String action,
      @RequestBody ReviewInput body,
      HttpServletRequest request) {
    mutation(request);
    return service.resolveAction(
        p, id, body.version(), action, body.state(), body.amount(), body.note());
  }

  @GetMapping("/api/exam-intake/bank")
  Map<String, Object> bank(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service
        .publicBank(p)
        .map(b -> Map.<String, Object>of("available", true, "bank", b))
        .orElseGet(
            () -> Map.of("available", false, "message", "승인된 계좌가 아직 없습니다. 입금하지 말고 안내를 기다려 주세요."));
  }

  @GetMapping("/api/admin/exam-intake/events")
  List<Map<String, Object>> events(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    admin(p);
    return service
        .repo
        .jdbc()
        .queryForList(
            "SELECT id,request_id,event_type,state,attempts,created_at,acknowledged_at,last_error"
                + " FROM exam_intake_event ORDER BY created_at DESC LIMIT 100");
  }

  @PostMapping("/api/admin/exam-intake/events/{id}/ack")
  Map<String, Boolean> ack(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      HttpServletRequest request) {
    mutation(request);
    admin(p);
    int changed =
        service
            .repo
            .jdbc()
            .update(
                "UPDATE exam_intake_event SET acknowledged_at=? WHERE id=? AND acknowledged_at IS"
                    + " NULL",
                java.sql.Timestamp.from(java.time.Instant.now()),
                id);
    return Map.of("acknowledged", changed == 1);
  }
}
