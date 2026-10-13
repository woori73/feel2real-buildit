package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.VersionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingVersionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DesignCraft 출판 버전(스냅샷) API (P3).
 */
@RestController
@RequestMapping("/api/publishing/jobs/{jobId}/versions")
@RequiredArgsConstructor
public class PublishingVersionController {

  private final PublishingVersionService versionService;

  /** 버전 목록. */
  @GetMapping
  public ResponseEntity<List<VersionResponse>> listVersions(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(versionService.listVersions(principal, jobId));
  }

  /** 특정 버전으로 복원. */
  @PostMapping("/{versionId}/restore")
  public ResponseEntity<Void> restoreVersion(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @PathVariable Long versionId) {
    versionService.restoreVersion(principal, jobId, versionId);
    return ResponseEntity.ok().build();
  }
}
