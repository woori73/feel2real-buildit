package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.ReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/newstart/updates")
public class NewStartOfficialUpdateAdminController {

  private final NewStartOfficialUpdateService service;

  @GetMapping
  public ResponseEntity<List<NewStartOfficialUpdateResponse>> list(
      @RequestParam(required = false) SourceType sourceType,
      @RequestParam(required = false) ReviewStatus status) {
    return ResponseEntity.ok(service.adminUpdates(sourceType, status));
  }

  @PostMapping
  public ResponseEntity<NewStartOfficialUpdateResponse> create(
      @Valid @RequestBody NewStartOfficialUpdateRequest request) {
    return ResponseEntity.ok(service.createReview(request));
  }

  @PostMapping("/{id}/publish")
  public ResponseEntity<NewStartOfficialUpdateResponse> publish(
      @PathVariable Long id,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(service.publish(id, principal.loginId()));
  }

  @PostMapping("/{id}/reject")
  public ResponseEntity<NewStartOfficialUpdateResponse> reject(
      @PathVariable Long id,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(service.reject(id, principal.loginId()));
  }

  @PostMapping("/{id}/archive")
  public ResponseEntity<NewStartOfficialUpdateResponse> archive(
      @PathVariable Long id,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(service.archive(id, principal.loginId()));
  }

  @PostMapping("/{id}/review")
  public ResponseEntity<NewStartOfficialUpdateResponse> review(
      @PathVariable Long id,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(service.returnToReview(id, principal.loginId()));
  }

  @PostMapping("/{id}/featured")
  public ResponseEntity<NewStartOfficialUpdateResponse> featured(
      @PathVariable Long id,
      @RequestParam boolean enabled) {
    return ResponseEntity.ok(service.setFeatured(id, enabled));
  }
}
