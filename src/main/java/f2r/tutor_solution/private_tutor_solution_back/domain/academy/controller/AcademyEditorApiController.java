package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
 * <p>보안: Jsoup 허용 목록 기반 HTML 정제를 사용한다.
 * 정규식 기반 정제는 우회 가능하므로 사용하지 않는다.
 *
 * <p>차단 대상:
 * <ul>
 *   <li>script, iframe, object, embed, form 태그</li>
 *   <li>svg 태그 (스크립트 포함 가능)</li>
 *   <li>on* 이벤트 핸들러 속성</li>
 *   <li>javascript:, data: (이미지 제외), vbscript: URL</li>
 *   <li>인코딩된 위험 URL (&#106;avascript: 등)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private static final String DRAFT_HTML = "visualEditor.draft.html";
  private static final String DRAFT_CSS = "visualEditor.draft.css";
  private static final String DRAFT_PROJECT = "visualEditor.draft.projectData";
  private static final String DRAFT_UPDATED_AT = "visualEditor.draft.updatedAt";

  // 크기 제한
  private static final int MAX_HTML_SIZE = 16 * 1024 * 1024;  // 16MB
  private static final int MAX_CSS_SIZE = 64 * 1024;           // 64KB
  private static final int MAX_PROJECT_SIZE = 16 * 1024 * 1024; // 16MB

  private final AcademyService academyService;

  /**
   * 허용 목록 기반 HTML Safelist.
   *
   * <p>허용 태그: 기본 텍스트·레이아웃 태그만 허용.
   * 차단: script, iframe, object, embed, form, svg, style(인라인은 허용).
   */
  private static Safelist editorSafelist() {
    return Safelist.relaxed()
        // 위험 태그 제거
        .removeTags("script", "iframe", "object", "embed", "form", "svg", "math",
            "input", "button", "select", "textarea", "link", "meta", "base")
        // 모든 태그에서 on* 이벤트 핸들러 제거
        .removeAttributes(":all", "onabort", "onblur", "onchange", "onclick",
            "ondblclick", "onerror", "onfocus", "onkeydown", "onkeypress",
            "onkeyup", "onload", "onmousedown", "onmousemove", "onmouseout",
            "onmouseover", "onmouseup", "onreset", "onresize", "onscroll",
            "onselect", "onsubmit", "onunload", "onanimationstart",
            "onanimationend", "ontransitionend", "onpointerdown",
            "onpointerup", "ontouchstart", "ontouchend")
        // style 속성은 허용 (GrapesJS 인라인 스타일)
        .addAttributes(":all", "style", "class", "id", "data-preset", "data-gjs-type")
        // a 태그: http/https만 허용
        .addProtocols("a", "href", "http", "https", "mailto", "tel")
        // img 태그: http/https/data(이미지) 허용
        .addProtocols("img", "src", "http", "https", "data")
        .removeProtocols("img", "src", "javascript", "vbscript");
  }

  /**
   * 편집 중인 초안을 저장한다.
   */
  @PutMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    AcademyResponse academy = requireMyAcademy(principal);

    String rawHtml = (String) request.get("html");
    String rawCss = (String) request.get("css");
    String projectData = (String) request.getOrDefault("projectData", "{}");

    // 크기 제한 검증
    validateSize(rawHtml, MAX_HTML_SIZE, "HTML");
    validateSize(rawCss, MAX_CSS_SIZE, "CSS");
    validateSize(projectData, MAX_PROJECT_SIZE, "프로젝트 데이터");

    // 허용 목록 기반 HTML 정제
    String html = sanitizeHtml(rawHtml);
    // CSS 정제
    String css = sanitizeCss(rawCss);
    // projectData 내부 검증
    validateProjectData(projectData);

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
   */
  @GetMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> getDraft(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = requireMyAcademy(principal);

    // TODO: AcademyService에 builderDraft 조회 메서드 추가 후 연동
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
   * Jsoup 허용 목록 기반 HTML 정제.
   *
   * <p>정규식과 달리 파서가 HTML을 해석하므로
   * 인코딩 우회(&#106;avascript: 등)를 차단한다.
   */
  private String sanitizeHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return Jsoup.clean(html, editorSafelist());
  }

  /**
   * CSS 정제: @import 및 expression() 제거.
   */
  private String sanitizeCss(String css) {
    if (css == null || css.isBlank()) {
      return "";
    }
    // @import 제거 (외부 리소스)
    String cleaned = css.replaceAll("(?i)@import[^;]+;", "");
    // expression() 제거 (IE XSS)
    cleaned = cleaned.replaceAll("(?i)expression\\s*\\(", "(");
    // javascript: URL 제거
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?javascript:", "url(blocked:");
    return cleaned;
  }

  /**
   * projectData 내부 검증.
   *
   * <p>GrapesJS projectData는 JSON이며 내부에 HTML·속성·URL을 포함할 수 있다.
   * 위험 패턴이 있으면 거부한다.
   */
  private void validateProjectData(String projectData) {
    if (projectData == null || projectData.isBlank()) {
      return;
    }
    String lower = projectData.toLowerCase();

    // script 태그 포함 여부
    if (lower.contains("<script") || lower.contains("</script")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 스크립트가 포함되어 있습니다.");
    }
    // iframe 포함 여부
    if (lower.contains("<iframe")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 iframe이 포함되어 있습니다.");
    }
    // javascript: URL (인코딩 변형 포함)
    // Jsoup이 디코딩하므로 원본에서도 검사
    if (lower.contains("javascript:")
        || lower.contains("&#106;")  // j의 HTML 엔티티
        || lower.contains("&#x6a;")  // j의 16진 엔티티
        || lower.contains("\\u006a")) {  // j의 유니코드 이스케이프
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 URL이 포함되어 있습니다.");
    }
    // on* 이벤트 핸들러 (JSON 내 속성)
    if (lower.matches(".*\"on\\w+\"\\s*:.*")) {
      throw new IllegalArgumentException(
          "프로젝트 데이터에 허용되지 않은 이벤트 핸들러가 포함되어 있습니다.");
    }
  }

  private void validateSize(String data, int maxSize, String name) {
    if (data != null && data.length() > maxSize) {
      throw new IllegalArgumentException(
          name + " 크기가 제한(" + (maxSize / 1024) + "KB)을 초과했습니다.");
    }
  }
}
