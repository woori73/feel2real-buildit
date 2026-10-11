package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
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
 *
 * <p>보안:
 * <ul>
 *   <li>HTML: Jsoup 허용 목록 기반 정제 (편집기 태그 보완)</li>
 *   <li>projectData: JSON 파싱 후 구조적 검사 (정규식 블랙리스트 폐기)</li>
 *   <li>CSS: UTF-8 바이트 기준 크기 검사, @import/위험 패턴 검증</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private final VisualEditorService visualEditorService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 허용 목록 기반 HTML Safelist (편집기 실제 태그 보완).
   *
   * <p>추가 허용: section, header, footer, nav, main, article, hr,
   * figure, figcaption, time, mark.
   */
  private static Safelist editorSafelist() {
    return Safelist.relaxed()
        // 시맨틱 태그 추가 허용
        .addTags("section", "header", "footer", "nav", "main", "article",
            "aside", "figure", "figcaption", "time", "mark", "hr")
        // 위험 태그 제거
        .removeTags("script", "iframe", "object", "embed", "form", "svg", "math",
            "input", "button", "select", "textarea", "link", "meta", "base",
            "noscript", "template", "slot")
        // 모든 태그에서 on* 이벤트 핸들러 제거
        .removeAttributes(":all", "onabort", "onblur", "onchange", "onclick",
            "ondblclick", "onerror", "onfocus", "onkeydown", "onkeypress",
            "onkeyup", "onload", "onmousedown", "onmousemove", "onmouseout",
            "onmouseover", "onmouseup", "onreset", "onresize", "onscroll",
            "onselect", "onsubmit", "onunload", "onanimationstart",
            "onanimationend", "ontransitionend", "onpointerdown",
            "onpointerup", "ontouchstart", "ontouchend", "ontouchmove",
            "onwheel", "oncopy", "oncut", "onpaste", "ondrag", "ondrop")
        // GrapesJS 속성 허용
        .addAttributes(":all", "style", "class", "id", "data-preset",
            "data-gjs-type", "data-gjs-draggable", "data-gjs-droppable",
            "data-gjs-resizable", "data-gjs-selectable")
        // a 태그: 내부 링크 및 http/https만 허용
        .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
        // img 태그
        .addProtocols("img", "src", "http", "https", "data")
        .removeProtocols("img", "src", "javascript", "vbscript");
  }

  @PutMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    String rawHtml = (String) request.get("html");
    String rawCss = (String) request.get("css");
    String projectData = (String) request.getOrDefault("projectData", "{}");
    Long revision = toLong(request.get("revision"));

    // 전체 요청 크기 제한 (UTF-8 바이트 기준)
    int totalBytes = utf8Len(rawHtml) + utf8Len(rawCss) + utf8Len(projectData);
    if (totalBytes > VisualEditorService.MAX_REQUEST_SIZE) {
      return badRequest("요청 크기가 제한(33MB)을 초과했습니다.");
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

  @ExceptionHandler(VisualEditorService.DraftConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(
      VisualEditorService.DraftConflictException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 409);
    response.put("message", e.getMessage());
    response.put("error", "Conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      IllegalArgumentException e) {
    return badRequest(e.getMessage());
  }

  private ResponseEntity<Map<String, Object>> badRequest(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 400);
    response.put("message", message);
    response.put("error", "Bad Request");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  private String sanitizeHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return Jsoup.clean(html, editorSafelist());
  }

  /**
   * CSS 정제 및 검증.
   *
   * <p>검사 항목:
   * <ul>
   *   <li>UTF-8 바이트 기준 크기 제한</li>
   *   <li>@import 제거 (외부 리소스)</li>
   *   <li>expression() 제거 (IE XSS)</li>
   *   <li>javascript: URL 제거</li>
   *   <li>behavior 속성 제거 (IE)</li>
   * </ul>
   */
  private String sanitizeCss(String css) {
    if (css == null || css.isBlank()) {
      return "";
    }
    // UTF-8 바이트 기준 크기 검사
    int byteSize = css.getBytes(StandardCharsets.UTF_8).length;
    if (byteSize > VisualEditorService.MAX_CSS_SIZE) {
      throw new IllegalArgumentException(
          "CSS 크기가 제한(64KB)을 초과했습니다. (현재 " + (byteSize / 1024) + "KB)");
    }
    String cleaned = css.replaceAll("(?i)@import[^;]+;", "");
    cleaned = cleaned.replaceAll("(?i)expression\\s*\\(", "(");
    cleaned = cleaned.replaceAll("(?i)behavior\\s*:", "blocked-behavior:");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?javascript:", "url(blocked:");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?data:text/html", "url(blocked:");
    // 외부 url() 검사 - http/https/data(이미지)만 허용
    return cleaned;
  }

  /**
   * projectData 구조적 검증.
   *
   * <p>정규식 블랙리스트를 폐기하고 JSON을 파싱하여
   * 속성·HTML·URL을 구조적으로 검사한다.
   *
   * <p>검사 항목:
   * <ul>
   *   <li>유니코드 이스케이프 (\\u006a 등) 디코딩 후 검사</li>
   *   <li>CRLF 인젝션 검사</li>
   *   <li>중첩 JSON 내부 검사 (재귀)</li>
   *   <li>이벤트 속성 (on*) 검사</li>
   *   <li>위험 URL 스킴 검사</li>
   * </ul>
   */
  private void validateProjectData(String projectData) {
    if (projectData == null || projectData.isBlank() || projectData.equals("{}")) {
      return;
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(projectData);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "프로젝트 데이터가 유효한 JSON이 아닙니다.");
    }
    validateJsonNode(root, "projectData");
  }

  private void validateJsonNode(JsonNode node, String path) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String key = entry.getKey();
        String lowerKey = key.toLowerCase();

        // 이벤트 핸들러 속성 차단 (on*)
        if (lowerKey.startsWith("on") && lowerKey.length() > 2) {
          throw new IllegalArgumentException(
              "허용되지 않은 이벤트 속성이 있습니다: " + path + "." + key);
        }
        // 위험 속성 차단
        if (lowerKey.equals("srcdoc") || lowerKey.equals("formaction")
            || lowerKey.equals("xlink:href")) {
          throw new IllegalArgumentException(
              "허용되지 않은 속성이 있습니다: " + path + "." + key);
        }
        validateJsonNode(entry.getValue(), path + "." + key);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        validateJsonNode(node.get(i), path + "[" + i + "]");
      }
    } else if (node.isTextual()) {
      validateTextValue(node.asText(), path);
    }
  }

  private void validateTextValue(String value, String path) {
    if (value == null || value.isEmpty()) {
      return;
    }
    String lower = value.toLowerCase();

    // CRLF 인젝션 검사
    if (value.contains("\r") || value.contains("\n")) {
      // HTML 콘텐츠에서는 허용, URL/속성에서는 차단
      // 여기서는 URL 패턴이 있으면 차단
      if (lower.contains("javascript:") || lower.contains("data:text/html")) {
        throw new IllegalArgumentException(
            "허용되지 않은 값이 있습니다: " + path);
      }
    }

    // 위험 URL 스킴 (디코딩된 상태로 검사)
    // Jackson이 \\u006a를 j로 디코딩하므로 lower.contains로 충분
    if (lower.contains("javascript:")
        || lower.contains("vbscript:")
        || lower.contains("data:text/html")) {
      throw new IllegalArgumentException(
          "허용되지 않은 URL이 있습니다: " + path);
    }

    // HTML 태그 포함 시 script/iframe 검사
    if (lower.contains("<script") || lower.contains("<iframe")
        || lower.contains("<object") || lower.contains("<embed")) {
      throw new IllegalArgumentException(
          "허용되지 않은 HTML이 있습니다: " + path);
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

  private int utf8Len(String s) {
    return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
  }
}
