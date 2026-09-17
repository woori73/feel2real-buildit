package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyExecutiveSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyExecutiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy 경영 대시보드 — 올해 1/1~오늘 vs 작년 동기.
 * {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/easy")
@RequiredArgsConstructor
public class EasyInsightController {

  private final EasyExecutiveService executiveService;

  @GetMapping("/executive-summary")
  public ResponseEntity<EasyExecutiveSummaryResponse> executiveSummary(
      @RequestParam(value = "businessId", required = false) Long businessId) {
    return ResponseEntity.ok(executiveService.summary(businessId));
  }
}
