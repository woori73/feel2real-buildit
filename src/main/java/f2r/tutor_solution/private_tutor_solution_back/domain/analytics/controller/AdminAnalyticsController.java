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
  private final f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AdminAnalyticsBreakdownService breakdownService;
  private final f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AdminAnalyticsEventsService eventsService;

  @GetMapping("/api/admin/analytics/summary")
  public ResponseEntity<AdminAnalyticsResponse> summary() {
    return ResponseEntity.ok(service.summary());
  }

  /** 서비스·페이지 그룹별 UV/PV, 30일 추세(서비스 선택 가능), 실제 수집된 전환 지표. */
  @GetMapping("/api/admin/analytics/breakdown")
  public ResponseEntity<f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse> breakdown(
      @org.springframework.web.bind.annotation.RequestParam(required = false) String service) {
    return ResponseEntity.ok(breakdownService.breakdown(service));
  }

  /** 섹션 노출·클릭·클릭률·서비스 이동·흐름 시작(이벤트 수집, PV와 별개 지표). */
  @GetMapping("/api/admin/analytics/events")
  public ResponseEntity<f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse> events() {
    return ResponseEntity.ok(eventsService.events());
  }
}
