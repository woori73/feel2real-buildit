package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@ConditionalOnProperty(name = "app.exam-intake.bank-admin-enabled", havingValue = "true")
public class ExamBankAdminController {
  private final ExamBankService banks;
  private final boolean customerEnabled;
  private final boolean publicEnabled;

  public ExamBankAdminController(
      ExamBankService banks,
      @Value("${app.exam-intake.enabled:false}") boolean customerEnabled,
      @Value("${app.exam-intake.bank-public-enabled:false}") boolean publicEnabled) {
    this.banks = banks;
    this.customerEnabled = customerEnabled;
    this.publicEnabled = publicEnabled;
  }

  @ModelAttribute
  void noCache(HttpServletResponse response) {
    response.setHeader("Cache-Control", "private, no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
  }

  @GetMapping("/admin/hub/exam-bank")
  String page(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    admin(p);
    return "admin/exam_bank";
  }

  @GetMapping("/api/admin/exam-intake/bank")
  @ResponseBody
  Map<String, Object> read(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return banks
        .adminBank(p)
        .map(
            b ->
                Map.<String, Object>of(
                    "bank", b, "customerVisible", customerEnabled && publicEnabled && b.approved()))
        .orElseGet(Map::of);
  }

  @PutMapping("/api/admin/exam-intake/bank")
  @ResponseBody
  Bank save(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody Bank bank,
      HttpServletRequest request) {
    ExamIntakeController.mutation(request);
    return banks.saveBank(p, bank);
  }

  public record Approval(long version) {}

  @PostMapping("/api/admin/exam-intake/bank/approve")
  @ResponseBody
  Bank approve(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestBody Approval body,
      HttpServletRequest request) {
    ExamIntakeController.mutation(request);
    return banks.approveBank(p, body.version());
  }

  @ExceptionHandler(DataAccessException.class)
  @ResponseBody
  ResponseEntity<Map<String, String>> unavailable(DataAccessException ignored) {
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "계좌 저장소를 사용할 수 없습니다. 승인된 스키마와 연결 상태를 확인해 주세요."));
  }

  @ExceptionHandler(ResponseStatusException.class)
  @ResponseBody
  ResponseEntity<Map<String, String>> rejected(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", e.getReason() == null ? "요청을 처리할 수 없습니다." : e.getReason()));
  }
}
