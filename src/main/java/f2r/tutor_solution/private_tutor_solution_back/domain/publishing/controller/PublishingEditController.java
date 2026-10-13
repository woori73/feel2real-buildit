package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.ExecuteCommandsRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingEditService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DesignCraft 출판 편집 API (P1: 명령 실행·페이지 PNG·preflight).
 *
 * <p>Qoder 명세 4.2·4.4 계약을 따른다.
 */
@RestController
@RequestMapping("/api/publishing/jobs/{jobId}")
@RequiredArgsConstructor
public class PublishingEditController {

  private final PublishingEditService editService;

  /** 편집 명령 실행 (적용 전 스냅샷 자동 생성). */
  @PostMapping("/commands")
  public ResponseEntity<Map<String, Object>> executeCommands(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @RequestBody ExecuteCommandsRequest request) {
    Long snapshotId = editService.executeCommands(principal, jobId, request.commands());
    return ResponseEntity.ok(Map.of(
        "jobId", jobId,
        "applied", true,
        "snapshotId", snapshotId));
  }

  /** 페이지 PNG 미리보기. */
  @GetMapping("/pages/{page}/png")
  public ResponseEntity<byte[]> renderPage(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @PathVariable int page,
      @RequestParam(defaultValue = "2.0") double scale) {
    byte[] png = editService.renderPagePng(principal, jobId, page, scale);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(png);
  }

  /** 페이지 썸네일 (저해상도). */
  @GetMapping("/pages/{page}/thumb")
  public ResponseEntity<byte[]> renderThumb(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @PathVariable int page) {
    byte[] png = editService.renderPagePng(principal, jobId, page, 0.5);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(png);
  }

  /** preflight 검수 실행. */
  @PostMapping("/preflight")
  public ResponseEntity<PreflightResponse> runPreflight(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(editService.runPreflight(principal, jobId));
  }
}
