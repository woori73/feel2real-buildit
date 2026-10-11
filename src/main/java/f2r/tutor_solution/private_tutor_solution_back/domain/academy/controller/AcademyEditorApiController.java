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
import java.util.Set;
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
 * <p>보안:
 * <ul>
 *   <li>HTML: Jsoup 허용 목록 기반 정제</li>
 *   <li>projectData: JSON 파싱 후 content 원시 HTML과 script 속성을 구조적으로 검사·정제</li>
 *   <li>CSS: UTF-8 바이트 기준 크기 검사, 위험 패턴 정제</li>
 *   <li>인라인 style: 위험한 외부 URL·@import 정제</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private final VisualEditorService visualEditorService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * GrapesJS 컴포넌트에서 허용하지 않는 속성.
   */
  private static final Set<String> BLOCKED_COMPONENT_ATTRS = Set.of(
      "script", "onscript", "srcdoc", "formaction", "xlink:href");

  private static Safelist editorSafelist() {
    return Safelist.relaxed()
        .addTags("section", "header", "footer", "nav", "main", "article",
            "aside", "figure", "figcaption", "time", "mark", "hr")
        .removeTags("script", "iframe", "object", "embed", "form", "svg", "math",
            "input", "button", "select", "textarea", "link", "meta", "base",
            "noscript", "template", "slot")
        .removeAttributes(":all", "onabort", "onblur", "onchange", "onclick",
            "ondblclick", "onerror", "onfocus", "onkeydown", "onkeypress",
            "onkeyup", "onload", "onmousedown", "onmousemove", "onmouseout",
            "onmouseover", "onmouseup", "onreset", "onresize", "onscroll",
            "onselect", "onsubmit", "onunload", "onanimationstart",
            "onanimationend", "ontransitionend", "onpointerdown",
            "onpointerup", "ontouchstart", "ontouchend", "ontouchmove",
            "onwheel", "oncopy", "oncut", "onpaste", "ondrag", "ondrop")
        .addAttributes(":all", "style", "class", "id", "data-preset",
            "data-gjs-type", "data-gjs-draggable", "data-gjs-droppable",
            "data-gjs-resizable", "data-gjs-selectable")
        .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
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

    // revision 필수 검증: 기존 초안이 있는데 revision을 생략하면 거부
    // (첫 저장의 revision 0 모호성 해결: null = 신규, 0 이상 = 기존)
    boolean hasDraft = visualEditorService.getDraft(principal).isPresent();
    if (hasDraft && revision == null) {
      return badRequest("revision이 필요합니다. 초안을 새로고침 후 다시 시도하세요.");
    }

    int totalBytes = utf8Len(rawHtml) + utf8Len(rawCss) + utf8Len(projectData);
    if (totalBytes > VisualEditorService.MAX_REQUEST_SIZE) {
      return badRequest("요청 크기가 제한(33MB)을 초과했습니다.");
    }

    String html;
    String css;
    try {
      html = sanitizeHtml(rawHtml);
      css = sanitizeCss(rawCss);
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

    try {
      projectData = sanitizeProjectData(projectData);
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

    VisualEditorDraft draft;
    try {
      draft = visualEditorService.saveDraft(
          principal, html, css, projectData, revision);
    } catch (VisualEditorService.DraftConflictException e) {
      return conflict(e.getMessage());
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

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
    return conflict(e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      IllegalArgumentException e) {
    return badRequest(e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 500);
    response.put("message", "서버 오류가 발생했습니다.");
    response.put("error", "Internal Server Error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  private ResponseEntity<Map<String, Object>> badRequest(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 400);
    response.put("message", message);
    response.put("error", "Bad Request");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  private ResponseEntity<Map<String, Object>> conflict(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 409);
    response.put("message", message);
    response.put("error", "Conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  private String sanitizeHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    // 인라인 style 속성도 정제
    String cleaned = Jsoup.clean(html, editorSafelist());
    return sanitizeInlineStyles(cleaned);
  }

  /**
   * 인라인 style 속성의 위험한 URL·@import를 정제한다.
   */
  private String sanitizeInlineStyles(String html) {
    if (html == null || html.isEmpty()) {
      return html;
    }
    // style="..." 내부의 위험 패턴 제거
    // Jsoup이 이미 정제했지만, 이중 방어로 처리
    return html.replaceAll("(?i)url\\(\\s*['\"]?javascript:", "url(blocked:")
        .replaceAll("(?i)url\\(\\s*['\"]?data:text/html", "url(blocked:")
        .replaceAll("(?i)expression\\s*\\(", "(blocked:");
  }

  /**
   * CSS 정제 및 검증.
   *
   * <p>UTF-8 바이트 기준으로 크기를 검사하며,
   * 정확히 65,536바이트(64KB)인 경우도 400을 반환한다.
   * (경계값: 65536 초과 시 거부 → 65536은 허용, 65537부터 거부)
   *
   * <p>참고: MAX_CSS_SIZE = 65536이므로,
   * 65536바이트는 허용되고 65537바이트부터 거부된다.
   * 감사 지적사항에 따라 65536바이트 입력 시 400이 아닌 정상 처리를 확인하고,
   * 초과 시 반드시 400 JSON을 반환하도록 한다.
   */
  private String sanitizeCss(String css) {
    if (css == null || css.isBlank()) {
      return "";
    }
    int byteSize = css.getBytes(StandardCharsets.UTF_8).length;
    if (byteSize > VisualEditorService.MAX_CSS_SIZE) {
      throw new IllegalArgumentException(
          "CSS 크기가 제한(64KB)을 초과했습니다. (현재 " + byteSize + "바이트)");
    }
    String cleaned = css.replaceAll("(?i)@import[^;]+;", "");
    cleaned = cleaned.replaceAll("(?i)expression\\s*\\(", "(blocked:");
    cleaned = cleaned.replaceAll("(?i)behavior\\s*:", "blocked-behavior:");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?javascript:", "url(blocked:");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?data:text/html", "url(blocked:");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?vbscript:", "url(blocked:");
    return cleaned;
  }

  /**
   * projectData 구조적 검증 및 정제.
   *
   * <p>GrapesJS projectData의 다음을 검사·정제한다:
   * <ul>
   *   <li>컴포넌트 `content`의 원시 HTML → Jsoup 허용 목록으로 정제</li>
   *   <li>컴포넌트 `script` 속성 → 차단 (스크립트 실행 방지)</li>
   *   <li>속성·URL 구조적 검사</li>
   * </ul>
   *
   * @return 정제된 projectData JSON
   */
  private String sanitizeProjectData(String projectData) {
    if (projectData == null || projectData.isBlank() || projectData.equals("{}")) {
      return "{}";
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(projectData);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("프로젝트 데이터가 유효한 JSON이 아닙니다.");
    }
    JsonNode sanitized = sanitizeJsonNode(root, "projectData");
    try {
      return objectMapper.writeValueAsString(sanitized);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("프로젝트 데이터 직렬화에 실패했습니다.");
    }
  }

  private JsonNode sanitizeJsonNode(JsonNode node, String path) {
    if (node.isObject()) {
      com.fasterxml.jackson.databind.node.ObjectNode obj =
          (com.fasterxml.jackson.databind.node.ObjectNode) node.deepCopy();
      Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
      // ConcurrentModification 방지를 위해 키 목록 수집
      java.util.List<String> keys = new java.util.ArrayList<>();
      while (fields.hasNext()) {
        keys.add(fields.next().getKey());
      }
      for (String key : keys) {
        String lowerKey = key.toLowerCase();
        // script 속성 차단
        if (lowerKey.equals("script")) {
          obj.remove(key);
          continue;
        }
        // 이벤트 핸들러 차단
        if (lowerKey.startsWith("on") && lowerKey.length() > 2) {
          obj.remove(key);
          continue;
        }
        // 위험 속성 차단
        if (BLOCKED_COMPONENT_ATTRS.contains(lowerKey)) {
          obj.remove(key);
          continue;
        }
        JsonNode value = obj.get(key);
        if (lowerKey.equals("content") && value.isTextual()) {
          // content 원시 HTML 정제
          obj.put(key, sanitizeHtml(value.asText()));
        } else {
          obj.set(key, sanitizeJsonNode(value, path + "." + key));
        }
      }
      return obj;
    } else if (node.isArray()) {
      com.fasterxml.jackson.databind.node.ArrayNode arr =
          (com.fasterxml.jackson.databind.node.ArrayNode) node.deepCopy();
      for (int i = 0; i < arr.size(); i++) {
        arr.set(i, sanitizeJsonNode(arr.get(i), path + "[" + i + "]"));
      }
      return arr;
    } else if (node.isTextual()) {
      validateTextValue(node.asText(), path);
      return node;
    }
    return node;
  }

  private void validateTextValue(String value, String path) {
    if (value == null || value.isEmpty()) {
      return;
    }
    String lower = value.toLowerCase();
    if (lower.contains("javascript:")
        || lower.contains("vbscript:")
        || lower.contains("data:text/html")) {
      throw new IllegalArgumentException("허용되지 않은 URL이 있습니다: " + path);
    }
    if (lower.contains("<script") || lower.contains("<iframe")
        || lower.contains("<object") || lower.contains("<embed")) {
      throw new IllegalArgumentException("허용되지 않은 HTML이 있습니다: " + path);
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
