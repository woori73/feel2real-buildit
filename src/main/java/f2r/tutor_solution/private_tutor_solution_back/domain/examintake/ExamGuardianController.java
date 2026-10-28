package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamGuardianController {
  private final ExamGuardianConsentService service;

  public ExamGuardianController(ExamGuardianConsentService service) {
    this.service = service;
  }

  @ModelAttribute
  void headers(HttpServletResponse r) {
    r.setHeader("Cache-Control", "private, no-store");
    r.setHeader("X-Content-Type-Options", "nosniff");
  }

  @GetMapping({"/exam-service/guardian", "/admin/hub/exam-guardians"})
  String page(
      @AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest r, Model model) {
    user(p);
    boolean adminView = r.getRequestURI().startsWith("/admin/");
    if (adminView) admin(p);
    model.addAttribute("adminView", adminView);
    model.addAttribute("privacyVersion", PRIVACY);
    model.addAttribute("termsVersion", TERMS);
    return "public/exam_guardian";
  }

  @GetMapping("/api/exam-intake/guardian-consents")
  @ResponseBody
  List<Map<String, Object>> mine(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.list(p, false);
  }

  @GetMapping("/api/admin/exam-intake/guardian-consents")
  @ResponseBody
  List<Map<String, Object>> all(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.list(p, true);
  }

  @PostMapping("/api/exam-intake/guardian-consents")
  @ResponseBody
  Map<String, Object> submit(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody ExamGuardianConsentService.Submission body,
      HttpServletRequest r) {
    ExamIntakeController.mutation(r);
    return service.submit(p, body);
  }

  @PostMapping("/api/admin/exam-intake/guardian-consents/{id}/verify")
  @ResponseBody
  Map<String, Object> verify(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody ExamGuardianConsentService.Verification body,
      HttpServletRequest r) {
    ExamIntakeController.mutation(r);
    return service.verify(p, id, body);
  }

  public record Rejection(String result) {}

  @PostMapping("/api/admin/exam-intake/guardian-consents/{id}/reject")
  @ResponseBody
  Map<String, Object> reject(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      @RequestBody Rejection body,
      HttpServletRequest r) {
    ExamIntakeController.mutation(r);
    return service.reject(p, id, body.result());
  }

  @PostMapping("/api/exam-intake/guardian-consents/{id}/revoke")
  @ResponseBody
  Map<String, Object> revoke(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      HttpServletRequest r) {
    ExamIntakeController.mutation(r);
    return service.revoke(p, id);
  }

  @ExceptionHandler(ResponseStatusException.class)
  @ResponseBody
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
