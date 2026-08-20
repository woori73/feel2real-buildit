package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAccessStatisticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics.AcademyAccessStatisticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AcademyAccessStatisticsController {
  private final AcademyAccessStatisticsQueryService queryService;

  @GetMapping("/api/tutor/academy/access-statistics")
  public ResponseEntity<AcademyAccessStatisticsResponse> getMine(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(queryService.getMine(principal, days));
  }
}
