package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt 시각 편집기 저장 API.
 *
 * <p>전용 테이블(academy_visual_editor_draft/published)을 사용한다.
 * 기존 P0/P1 builderDraft와 완전히 분리되며, P1 스냅샷과 연결하지 않는다.
 *
 * <p>동시 수정 제어: revision 낙관적 잠금.
 * revision 불일치 시 HTTP 409를 반환한다.
 *
 * <p>보안: Jsoup 허용 목록 기반 HTML 정제.
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private final VisualEditorService visualEditorService;

  /**
   * 허용 목록 기반 HTML Safelist.
   */
  private static Safelist editorSafelist() {
    return Safelist.relaxed()
        .removeTags("script", "iframe", "object", "embed", "form", "svg", "math",
            "input", "button", "select", "textarea", "link", "meta", "base")
        .removeAttributes(":all", "onabort", "onblur", "onchange", "onclick",
            "ondblclick", "onerror", "onfocus", "onkeydown", "onkeypress",
            "onkeyup", "onload", "onmousedown", "onmousemove", "onmouseout",
            "onmouseover", "onmouseup", "onreset", "onresize", "onscroll",
            "onselect", "onsubmit", "onunload", "onanimationstart",
            "onanimationend", "ontransitionend", "onpointerdown",
            "onpointerup", "ontouchstart", "ontouchend")
        .addAttributes(":all", "style", "class", "id", "data-preset", "data-gjs-type")
        .addProtocols("a", "href", "http", "https", "mailto", "tel")
        .addProtocols("img", "src", "http", "https", "data")
        .removeProtocols("img", "src", "javascript", "vbscript");
  }

  /**
   * 초안을 저장한다.
   *
   * <p>요청 본문:
   * <ul>
   *   <li>html: 편집기 HTML</li>
   *   <li>css: 편집기 CSS</li>
   *   <li>projectData: GrapesJS 프로젝트 JSON</li>
   *   <li>revision: 읽은 시점의 revision (신규는 null 또는 0)</li>
   * </ul>
   *
   * <p>동시 수정 충돌 시 HTTP 409를 반환한다.
   */
  @PutMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    String rawHtml = (String) request.get("html");
    String rawCss = (String) request.get("css");
    String projectData = (String) request.getOrDefault("projectData", "{}");
    Long revision = toLong(request.get("revision"));

    // 전체 요청 크기 제한
    int totalSize = len(rawHtml) + len(rawCss) + len(projectData);
    if (totalSize > VisualEditorService.MAX_REQUEST_SIZE) {
      throw new IllegalArgumentException(
          "요청 크기가 제한(33MB)을 초과했습니다.");
    }

    String html = sanitizeHtml(rawHtml);
    String css = sanitizeCss(rawCss);
    validateProjectData(projectData);

    VisualEditorDraft draft = visualEditorService.saveDraft(
        principal, html, css, projectData, revision);

    Map<String, Object> response = new HashMap<>();
    response.put("status", "DRAFT_SAVED");
    response.put("revision", draft.getRevision());
    response.put("updatedAt", draft.getUpdatedAt().toString());
    response.put("message", "초안이 저장되었습니다.");
    return ResponseEntity.ok(response);
  }

  /**
   * 초안을 조회한다. 본인 학원의 초안만 반환한다.
   */
  @GetMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> getDraft(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    Optional<VisualEditorDraft> draft = visualEditorService.getDraft(principal);

    Map<String, Object> response = new HashMap<>();
    if (draft.isPresent()) {
      VisualEditorDraft d = draft.get();
      response.put("html", d.getHtml());
      response.put("css", d.getCss());
      response.put("projectData", d.getProjectData());
      response.put("revision", d.getRevision());
      response.put("updatedAt", d.getUpdatedAt().toString());
    } else {
      response.put("html", "");
      response.put("css", "");
      response.put("projectData", "{}");
      response.put("revision", 0L);
      response.put("updatedAt", "");
    }
    return ResponseEntity.ok(response);
  }

  /**
   * 게시본을 조회한다. 본인 학원의 게시본만 반환한다.
   */
  @GetMapping("/visual-editor/published")
  public ResponseEntity<Map<String, Object>> getPublished(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    Optional<VisualEditorPublished> published =
        visualEditorService.getPublished(principal);

    Map<String, Object> response = new HashMap<>();
    if (published.isPresent()) {
      VisualEditorPublished p = published.get();
      response.put("html", p.getHtml());
      response.put("css", p.getCss());
      response.put("publishedAt", p.getPublishedAt().toString());
    } else {
      response.put("html", "");
      response.put("css", "");
      response.put("publishedAt", "");
    }
    return ResponseEntity.ok(response);
  }

  /**
   * 게시 기능은 비활성화 상태로 유지한다.
   */
  @PutMapping("/visual-editor/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", "NOT_IMPLEMENTED");
    response.put("message", "게시 기능은 준비 중입니다.");
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
  }

  /**
   * 동시 수정 충돌 시 HTTP 409를 반환한다.
   */
  @ExceptionHandler(VisualEditorService.DraftConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(
      VisualEditorService.DraftConflictException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 409);
    response.put("message", e.getMessage());
    response.put("error", "Conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  /**
   * 검증 오류 시 HTTP 400을 반환한다.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      IllegalArgumentException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 400);
    response.put("message", e.getMessage());
    response.put("error", "Bad Request");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  private String sanitizeHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return Jsoup.clean(html, editorSafelist());
  }

  private String sanitizeCss(String css) {
    if (css == null || css.isBlank()) {
      return "";
    }
    String cleaned = css.replaceAll("(?i)@import[^;]+;", "");
    cleaned = cleaned.replaceAll("(?i)expression\\s*\\(", "(");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?javascript:", "url(blocked:");
    return cleaned;
  }

  private void validateProjectData(String projectData) {
    if (projectData == null || projectData.isBlank()) {
      return;
    }
    String lower = projectData.toLowerCase();
    if (lower.contains("<script") || lower.contains("</script")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 스크립트가 포함되어 있습니다.");
    }
    if (lower.contains("<iframe")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 iframe이 포함되어 있습니다.");
    }
    if (lower.contains("javascript:")
        || lower.contains("&#106;")
        || lower.contains("&#x6a;")
        || lower.contains("\\u006a")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 URL이 포함되어 있습니다.");
    }
    if (lower.matches(".*\"on\\w+\"\\s*:.*")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 이벤트 핸들러가 포함되어 있습니다.");
    }
  }

  private Long toLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private int len(String s) {
    return s == null ? 0 : s.length();
  }
}
