package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 "통합 Analytics" 화면. {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class AdminAnalyticsController {

  private final AdminAnalyticsService service;

  @GetMapping("/api/admin/analytics/summary")
  public ResponseEntity<AdminAnalyticsResponse> summary() {
    return ResponseEntity.ok(service.summary());
  }
}
