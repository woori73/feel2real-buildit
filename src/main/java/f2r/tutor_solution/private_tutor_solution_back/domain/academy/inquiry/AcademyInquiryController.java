package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상담신청 API. 공개 접수는 로그인 없이, 조회·상태 변경은 원장(TUTOR)/관리자(ADMIN)만 쓴다.
 * 개인정보 응답은 캐시하지 않고(no-store), 요청 본문은 로그에 남기지 않는다.
 */
@RestController
public class AcademyInquiryController {

  public record SubmitRequest(String name, String phone, String message, Boolean consent, String website) {}

  public record StatusRequest(String status) {}

  private final AcademyInquiryService service;
  private final AcademyInquiryRateLimiter rateLimiter;

  public AcademyInquiryController(AcademyInquiryService service, AcademyInquiryRateLimiter rateLimiter) {
    this.service = service;
    this.rateLimiter = rateLimiter;
  }

  /** 공개 접수. website는 사람에게 보이지 않는 함정 입력칸(봇이 채우면 저장하지 않는다). */
  @PostMapping("/api/academies/{slug}/inquiries")
  public ResponseEntity<Map<String, Object>> submit(
      @PathVariable String slug, @RequestBody SubmitRequest body, HttpServletRequest request) {
    if (body == null) {
      return json(HttpStatus.BAD_REQUEST, Map.of("message", "요청 내용이 없어요."));
    }
    if (!rateLimiter.tryAcquire(rateLimiter.clientIp(request))) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60")
          .cacheControl(CacheControl.noStore())
          .body(Map.of("message", "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."));
    }
    try {
      AcademyInquiryService.SubmitResult r = service.submit(
          slug, body.name(), body.phone(), body.message(), body.consent(), body.website());
      return switch (r) {
        case RECEIVED, SKIPPED_SPAM, SKIPPED_DUPLICATE -> json(HttpStatus.ACCEPTED, Map.of("received", true));
        case ACADEMY_NOT_FOUND -> json(HttpStatus.NOT_FOUND, Map.of("message", "상담을 받을 수 없는 학원이에요."));
        case DAILY_LIMIT -> json(HttpStatus.TOO_MANY_REQUESTS, Map.of("message", "오늘 접수 한도에 도달했어요. 전화나 이메일로 문의해 주세요."));
      };
    } catch (InquiryInput.Invalid e) {
      return json(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  @GetMapping("/api/tutor/academy/inquiries")
  public ResponseEntity<Map<String, Object>> listMine(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    InquiryStatus s = parseStatus(status);
    if (status != null && !status.isBlank() && s == null) {
      return json(HttpStatus.BAD_REQUEST, Map.of("message", "상태는 NEW, IN_PROGRESS, DONE 중 하나예요."));
    }
    return json(HttpStatus.OK, view(service.listMine(principal, s, page, size)));
  }

  @PatchMapping("/api/tutor/academy/inquiries/{inquiryId}/status")
  public ResponseEntity<Map<String, Object>> changeStatus(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long inquiryId, @RequestBody StatusRequest body) {
    InquiryStatus next = body == null ? null : parseStatus(body.status());
    if (next == null) {
      return json(HttpStatus.BAD_REQUEST, Map.of("message", "상태는 NEW, IN_PROGRESS, DONE 중 하나예요."));
    }
    return service.changeStatus(principal, inquiryId, next)
        .map(i -> json(HttpStatus.OK, Map.<String, Object>of("id", i.id(), "status", i.status().name())))
        .orElseGet(() -> json(HttpStatus.NOT_FOUND, Map.of("message", "접수 내역을 찾을 수 없어요.")));
  }

  /** 관리자 조회 전용(상태 변경 없음). */
  @GetMapping("/api/admin/academies/{academyId}/inquiries")
  public ResponseEntity<Map<String, Object>> listForAdmin(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long academyId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return json(HttpStatus.OK, view(service.listForAdmin(principal, academyId, parseStatus(status), page, size)));
  }

  static InquiryStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return InquiryStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Map<String, Object> view(AcademyInquiryService.ListResult r) {
    return Map.of("items", r.items(), "total", r.total(), "page", r.page(), "size", r.size(), "newCount", r.newCount());
  }

  private static ResponseEntity<Map<String, Object>> json(HttpStatus status, Map<String, Object> body) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }
}
