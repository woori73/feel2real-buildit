package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt 시각 편집기 저장 API.
 *
 * <p>초안(DRAFT)과 게시본(PUBLISHED)을 분리한다.
 * 명시적 게시 요청 없이는 자동 게시되지 않는다.
 *
 * <p>주의: P1 D3 동시성 문제가 해결되기 전에는
 * AcademyPageVersionService 스냅샷과 실제 통합하지 않는다.
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private final AcademyService academyService;

  /**
   * 편집 중인 초안을 저장한다.
   * 실제 홈페이지에는 반영되지 않는다.
   */
  @PutMapping("/draft")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    AcademyResponse academy = requireMyAcademy(principal);

    String html = (String) request.get("html");
    String css = (String) request.get("css");
    // projectData는 GrapesJS 프로젝트 JSON (향후 버전 관리용)

    // TODO: 초안 저장 로직 구현
    // - academy_page_draft 테이블 또는 기존 builderDraft 필드 활용
    // - D3 해결 후 스냅샷 연동

    return ResponseEntity.ok(Map.of(
        "academyId", academy.academyId(),
        "status", "DRAFT_SAVED",
        "message", "초안이 저장되었습니다."));
  }

  /**
   * 초안을 게시한다. 명시적 사용자 승인 후에만 호출된다.
   * 실제 홈페이지에 반영된다.
   */
  @PutMapping("/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    AcademyResponse academy = requireMyAcademy(principal);

    String html = (String) request.get("html");
    String css = (String) request.get("css");

    // TODO: 게시 로직 구현
    // - 초안을 게시본으로 승격
    // - D3 해결 후 스냅샷 저장

    return ResponseEntity.ok(Map.of(
        "academyId", academy.academyId(),
        "status", "PUBLISHED",
        "message", "홈페이지가 게시되었습니다."));
  }

  /**
   * 저장된 초안을 불러온다.
   */
  @GetMapping("/draft")
  public ResponseEntity<Map<String, Object>> getDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);

    // TODO: 초안 조회 로직 구현

    return ResponseEntity.ok(Map.of(
        "academyId", academy.academyId(),
        "html", "",
        "css", ""));
  }

  private AcademyResponse requireMyAcademy(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.getMine(principal)
        .orElseThrow(() -> new AccessDeniedException("학원이 없습니다."));
  }
}
