package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.time.Instant;
import java.util.HashMap;
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
 * <p>저장 방식: 기존 P0의 {@code builderDraft} (Map&lt;String, String&gt;)를 재사용한다.
 * 신규 DB 테이블을 생성하지 않는다.
 *
 * <p>키 규칙:
 * <ul>
 *   <li>{@code visualEditor.draft.html} — 초안 HTML</li>
 *   <li>{@code visualEditor.draft.css} — 초안 CSS</li>
 *   <li>{@code visualEditor.draft.projectData} — GrapesJS 프로젝트 JSON</li>
 *   <li>{@code visualEditor.draft.updatedAt} — 초안 저장 시각 (ISO-8601)</li>
 * </ul>
 *
 * <p>주의: P1 D3 동시성 문제가 해결되기 전에는
 * AcademyPageVersionService 스냅샷과 실제 통합하지 않는다.
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private static final String DRAFT_HTML = "visualEditor.draft.html";
  private static final String DRAFT_CSS = "visualEditor.draft.css";
  private static final String DRAFT_PROJECT = "visualEditor.draft.projectData";
  private static final String DRAFT_UPDATED_AT = "visualEditor.draft.updatedAt";

  private final AcademyService academyService;

  /**
   * 편집 중인 초안을 저장한다.
   * 실제 홈페이지에는 반영되지 않는다.
   *
   * <p>저장 실패 시 성공 메시지를 표시하지 않는다 (프론트에서 처리).
   */
  @PutMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    AcademyResponse academy = requireMyAcademy(principal);

    String html = sanitizeHtml((String) request.get("html"));
    String css = sanitizeCss((String) request.get("css"));
    String projectData = (String) request.getOrDefault("projectData", "{}");

    // URL 검증
    validateUrls(html);

    Map<String, String> builderDraft = new HashMap<>();
    builderDraft.put(DRAFT_HTML, html);
    builderDraft.put(DRAFT_CSS, css);
    builderDraft.put(DRAFT_PROJECT, projectData);
    builderDraft.put(DRAFT_UPDATED_AT, Instant.now().toString());

    AcademyPageSettingsUpdateRequest updateRequest =
        new AcademyPageSettingsUpdateRequest(
            null, null, null, null, null, null, null, builderDraft);

    academyService.updatePageSettings(principal, updateRequest);

    Map<String, Object> response = new HashMap<>();
    response.put("academyId", academy.academyId());
    response.put("status", "DRAFT_SAVED");
    response.put("updatedAt", builderDraft.get(DRAFT_UPDATED_AT));
    response.put("message", "초안이 저장되었습니다.");
    return ResponseEntity.ok(response);
  }

  /**
   * 저장된 초안을 불러온다.
   *
   * <p>참고: builderDraft 조회는 AcademyService를 통해 수행한다.
   * 현재는 빈 초안을 반환하며, 실제 조회 로직은 AcademyService 확장 후 연동한다.
   */
  @GetMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> getDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);

    // TODO: AcademyService에 builderDraft 조회 메서드 추가 후 연동
    // 현재는 빈 초안 반환 (프론트에서 신규 작성으로 처리)
    Map<String, Object> response = new HashMap<>();
    response.put("academyId", academy.academyId());
    response.put("html", "");
    response.put("css", "");
    response.put("projectData", "{}");
    response.put("updatedAt", "");
    return ResponseEntity.ok(response);
  }

  /**
   * 게시 기능은 실제 로직 구현 전까지 비활성화.
   */
  @PutMapping("/visual-editor/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    // 실제 게시 로직 구현 전까지 501 반환
    Map<String, Object> response = new HashMap<>();
    response.put("status", "NOT_IMPLEMENTED");
    response.put("message", "게시 기능은 준비 중입니다.");
    return ResponseEntity.status(501).body(response);
  }

  private AcademyResponse requireMyAcademy(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.getMine(principal)
        .orElseThrow(() -> new AccessDeniedException("학원이 없습니다."));
  }

  /**
   * HTML 정제: script 태그 제거 (XSS 방지).
   */
  private String sanitizeHtml(String html) {
    if (html == null) {
      return "";
    }
    // script 태그 제거
    return html.replaceAll("(?i)<script[^>]*>.*?</script>", "")
        .replaceAll("(?i)<script[^>]*>", "")
        .replaceAll("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");
  }

  /**
   * CSS 정제: @import 제거 (외부 리소스 로딩 방지).
   */
  private String sanitizeCss(String css) {
    if (css == null) {
      return "";
    }
    return css.replaceAll("(?i)@import[^;]+;", "");
  }

  /**
   * URL 검증: javascript: 스킴 차단.
   */
  private void validateUrls(String html) {
    if (html != null && html.toLowerCase().contains("javascript:")) {
      throw new IllegalArgumentException("허용되지 않은 URL 스킴이 포함되어 있습니다.");
    }
  }
}
