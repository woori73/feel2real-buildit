package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsBuilderSettingsDtos.LmsSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsBuilderSettingsDtos.LmsSettingsView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service.LmsBuilderSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Builder "LMS 설정" 패널. {@code /api/tutor/**}라 SecurityConfig의 hasRole("TUTOR")를 그대로 탄다. */
@RestController
@RequiredArgsConstructor
public class LmsBuilderSettingsController {

  private final LmsBuilderSettingsService service;

  @GetMapping("/api/tutor/academy/lms-settings")
  public ResponseEntity<LmsSettingsView> get(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(service.get(principal));
  }

  @PutMapping("/api/tutor/academy/lms-settings")
  public ResponseEntity<LmsSettingsView> update(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody LmsSettingsUpdateRequest request) {
    return ResponseEntity.ok(service.update(principal, request));
  }
}
