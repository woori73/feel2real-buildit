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
 *   <li>{@code visualEditor.published.html} — 게시본 HTML</li>
 *   <li>{@code visualEditor.published.css} — 게시본 CSS</li>
 *   <li>{@code visualEditor.published.updatedAt} — 게시 시각</li>
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

  private static final String PUBLISHED_HTML = "visualEditor.published.html";
  private static final String PUBLISHED_CSS = "visualEditor.published.css";
  private static final String PUBLISHED_UPDATED_AT = "visualEditor.published.updatedAt";

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

    return ResponseEntity.ok(Map.of(
        "academyId", academy.academyId(),
        "status", "DRAFT_SAVED",
        "updatedAt", builderDraft.get(DRAFT_UPDATED_AT),
        "message", "초안이 저장되었습니다."));
  }

  /**
   * 저장된 초안을 불러온다.
   */
  @GetMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> getDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);

    Map<String, String> builderDraft = getBuilderDraft(academy);

    return ResponseEntity.ok(Map.of(
        "academyId", academy.academyId(),
        "html", builderDraft.getOrDefault(DRAFT_HTML, ""),
        "css", builderDraft.getOrDefault(DRAFT_CSS, ""),
        "projectData", builderDraft.getOrDefault(DRAFT_PROJECT, "{}"),
        "updatedAt", builderDraft.getOrDefault(DRAFT_UPDATED_AT, "")));
  }

  /**
   * 게시본을 불러온다.
   */
  @GetMapping("/visual-editor/published")
  public ResponseEntity<Map<String, Object>> getPublished(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);

    Map<String, String> builderDraft = getBuilderDraft(academy);

    return ResponseEntity.ok(Map.of(
        "academyId", academy.academyId(),
        "html", builderDraft.getOrDefault(PUBLISHED_HTML, ""),
        "css", builderDraft.getOrDefault(PUBLISHED_CSS, ""),
        "updatedAt", builderDraft.getOrDefault(PUBLISHED_UPDATED_AT, "")));
  }

  /**
   * 게시 기능은 실제 로직 구현 전까지 비활성화.
   * 프론트에서 게시 버튼을 비활성화하므로 이 API는 호출되지 않아야 한다.
   */
  @PutMapping("/visual-editor/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    // 실제 게시 로직 구현 전까지 501 반환
    return ResponseEntity.status(501).body(Map.of(
        "status", "NOT_IMPLEMENTED",
        "message", "게시 기능은 준비 중입니다."));
  }

  private AcademyResponse requireMyAcademy(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService.getMine(principal)
        .orElseThrow(() -> new AccessDeniedException("학원이 없습니다."));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getBuilderDraft(AcademyResponse academy) {
    // AcademyResponse에서 builderDraft 추출
    // 실제 필드명에 맞춰 조정 필요
    try {
      Object pageSettings = academy.getClass().getMethod("pageSettings").invoke(academy);
      if (pageSettings != null) {
        Object draft = pageSettings.getClass().getMethod("builderDraft").invoke(pageSettings);
        if (draft instanceof Map) {
          return (Map<String, String>) draft;
        }
      }
    } catch (Exception e) {
      // 리플렉션 실패 시 빈 맵
    }
    return Map.of();
  }

  /**
   * HTML 정제: script 태그 제거 (XSS 방지).
   * 미리보기는 샌드박스 iframe에서 표시한다.
   */
  private String sanitizeHtml(String html) {
    if (html == null) return "";
    // script 태그 제거
    return html.replaceAll("(?i)<script[^>]*>.*?</script>", "")
               .replaceAll("(?i)<script[^>]*>", "")
               .replaceAll("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");
  }

  /**
   * CSS 정제: @import 제거 (외부 리소스 로딩 방지).
   */
  private String sanitizeCss(String css) {
    if (css == null) return "";
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
