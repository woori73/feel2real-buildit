package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.ExecuteCommandsRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingEditService;
import java.util.List;
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
 * DesignCraft 출판 편집 API.
 *
 * <p>Qoder API_INTEGRATION.md 계약을 따른다.
 */
@RestController
@RequestMapping("/api/publishing/jobs/{jobId}")
@RequiredArgsConstructor
public class PublishingEditController {

  private final PublishingEditService editService;

  /**
   * 편집 명령 실행 (적용 전 스냅샷 자동 생성).
   *
   * <p>갭-G2: label은 스냅샷 설명용. 갭-G6: 빈 commands는 스냅샷만 생성.
   * 갭-G4: touchedPages 반환 (프론트 캐시 무효화용).
   */
  @PostMapping("/commands")
  public ResponseEntity<Map<String, Object>> executeCommands(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @RequestBody ExecuteCommandsRequest request) {
    Long snapshotId = editService.executeCommands(
        principal, jobId, request.commands(), request.label());
    // 변경된 페이지는 전체로 간주 (정밀 추적은 P2)
    return ResponseEntity.ok(Map.of(
        "jobId", jobId,
        "applied", true,
        "snapshotId", snapshotId,
        "touchedPages", List.of()));
  }

  /** 페이지 PNG 미리보기 (캐시 브레이커: ETag). */
  @GetMapping("/pages/{page}/png")
  public ResponseEntity<byte[]> renderPage(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @PathVariable int page,
      @RequestParam(defaultValue = "2.0") double scale) {
    byte[] png = editService.renderPagePng(principal, jobId, page, scale);
    String etag = "\"pg" + page + "-s" + scale + "-" + png.length + "\"";
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("ETag", etag)
        .header("Cache-Control", "private, max-age=60")
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
        .header("Cache-Control", "private, max-age=300")
        .body(png);
  }

  /** preflight 검수 실행 (Qoder §2 스키마). */
  @PostMapping("/preflight")
  public ResponseEntity<PreflightResponse> runPreflight(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(editService.runPreflight(principal, jobId));
  }

  /**
   * Markdown 원고의 제목 스타일을 자동 적용한다 (P4).
   *
   * <p>원고가 Markdown(.md)인 경우 {@code #}/{@code ##}/{@code ###} 제목과
   * {@code >} 인용문을 인식하여 ko-heading1/2/3, ko-quote 스타일을 적용한다.
   * {@code file.place} 이후에 호출해야 하며, 본문(ko-body)과 충돌하지 않는다.
   */
  @PostMapping("/apply-markdown-styles")
  @PreAuthorize("hasAnyRole('TUTOR','ADMIN')")
  public ResponseEntity<ApplyStylesResponse> applyMarkdownStyles(
      @PathVariable Long jobId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    int applied = editService.applyMarkdownStyles(principal, jobId);
    return ResponseEntity.ok(new ApplyStylesResponse(applied));
  }

  public record ApplyStylesResponse(int appliedStyles) {}
}
