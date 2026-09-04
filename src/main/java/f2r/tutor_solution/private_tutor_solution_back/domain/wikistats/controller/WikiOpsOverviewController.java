package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service.WikiOpsOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 홈 "Wiki 운영 현황" 카드. {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class WikiOpsOverviewController {

  private final WikiOpsOverviewService service;

  @GetMapping("/api/admin/wiki/ops-overview")
  public ResponseEntity<WikiOpsOverviewResponse> overview() {
    return ResponseEntity.ok(service.overview());
  }
}
