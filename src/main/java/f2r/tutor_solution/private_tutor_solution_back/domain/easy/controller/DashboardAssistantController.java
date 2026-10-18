package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.DashboardAssistantService;
import java.time.YearMonth;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BuildIt 대시보드 AI 비서(읽기 전용, Muse Spark 1.3 Contributor). ADMIN·TUTOR, 사업체 접근 권한은 서비스가 검증한다. */
@RestController
@RequestMapping("/api/dashboard/assistant")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
public class DashboardAssistantController {

  public record AskRequest(Long businessId, String month, String message) {}

  private final DashboardAssistantService service;

  @PostMapping
  public ResponseEntity<Map<String, Object>> ask(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AskRequest request) {
    YearMonth month = request.month() == null || request.month().isBlank() ? null : YearMonth.parse(request.month());
    var answer = service.ask(principal, request.businessId(), month, request.message());
    if (!answer.ok()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", answer.text()));
    }
    return ResponseEntity.ok(Map.of("answer", answer.text(), "model", answer.model()));
  }
}
