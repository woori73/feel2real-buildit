package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamPrivacyRetentionController {
  private final ExamPrivacyRetentionService service;

  public ExamPrivacyRetentionController(ExamPrivacyRetentionService service) {
    this.service = service;
  }

  @ModelAttribute
  void noCache(HttpServletResponse r) {
    r.setHeader("Cache-Control", "private, no-store");
  }

  @GetMapping("/api/admin/exam-intake/retention")
  List<Map<String, Object>> plan(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.plan(p);
  }

  public record Erasure(long version, boolean purposeComplete, boolean noLegalHold) {}

  @PostMapping("/api/admin/exam-intake/requests/{id}/erase-learning-data")
  Map<String, Boolean> erase(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Erasure in,
      HttpServletRequest r) {
    ExamIntakeController.mutation(r);
    service.erase(p, id, in.version(), in.purposeComplete(), in.noLegalHold());
    return Map.of("completed", true);
  }

  @PostMapping("/api/admin/exam-intake/retention/unused-guardians")
  Map<String, Integer> guardians(
      @AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest r) {
    ExamIntakeController.mutation(r);
    return Map.of("redacted", service.eraseUnusedGuardians(p));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> rejected(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", Objects.toString(e.getReason(), "요청을 처리할 수 없습니다.")));
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  @ResponseBody
  ResponseEntity<Map<String, String>> unavailable(
      org.springframework.dao.DataAccessException ignored) {
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "저장소를 사용할 수 없습니다. 관리자에게 연결·스키마 상태 확인을 요청해 주세요."));
  }
}
