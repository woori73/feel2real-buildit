package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
 * <p>보안 정제는 {@link VisualEditorSanitizer}가 담당한다:
 * HTML 허용 목록, CSS 바이트 경계, projectData의 tagName·type 허용 목록과
 * components/content 문자열 HTML 정제.
 *
 * <p>revision 규칙:
 * <ul>
 *   <li>초안 없음 + revision null/0 → 신규 생성</li>
 *   <li>초안 없음 + 그 외 revision → 409 (클라이언트 상태 불일치)</li>
 *   <li>초안 있음 + revision null → 400</li>
 *   <li>초안 있음 + revision 값 → 현재 revision과 일치해야 하며,
 *       불일치 시 409</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tutor/academy/page-settings")
@RequiredArgsConstructor
public class AcademyEditorApiController {

  private final VisualEditorService visualEditorService;

  @PutMapping("/visual-editor/draft")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    // 잘못된 요청 타입은 400으로 처리한다 (ClassCastException → 500 방지)
    String rawHtml = asString(request, "html");
    String rawCss = asString(request, "css");
    String projectData = asString(request, "projectData");
    if (projectData == null) {
      projectData = "{}";
    }
    Long revision = asRevision(request.get("revision"));

    // revision 규칙: 최초 저장 시 null/0 구분
    Optional<VisualEditorDraft> existing = visualEditorService.getDraft(principal);
    if (existing.isEmpty()) {
      if (revision != null && revision != 0L) {
        throw new VisualEditorService.DraftConflictException(
            "초안이 존재하지 않습니다. 새로고침 후 다시 시도하세요.");
      }
    } else if (revision == null) {
      return badRequest("revision이 필요합니다. 초안을 새로고침 후 다시 시도하세요.");
    }

    int totalBytes = utf8Len(rawHtml) + utf8Len(rawCss) + utf8Len(projectData);
    if (totalBytes > VisualEditorService.MAX_REQUEST_SIZE) {
      return badRequest("요청 크기가 제한(33MB)을 초과했습니다.");
    }

    String html = VisualEditorSanitizer.sanitizeHtml(rawHtml);
    String css = VisualEditorSanitizer.sanitizeCss(rawCss);
    projectData = VisualEditorSanitizer.sanitizeProjectData(projectData);

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

  /**
   * 요청 값을 문자열로 읽는다. 문자열이 아닌 타입이면 400을 위한 예외를 던진다.
   */
  private String asString(Map<String, Object> request, String key) {
    Object value = request.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s;
    }
    throw new IllegalArgumentException(
        "'" + key + "' 형식이 올바르지 않습니다. 문자열이어야 합니다.");
  }

  /**
   * revision을 엄격하게 파싱한다. 숫자·숫자 문자열만 허용하며,
   * 그 외 타입·형식은 400을 위한 예외를 던진다.
   */
  private Long asRevision(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    if (value instanceof String s) {
      try {
        return Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("revision 형식이 올바르지 않습니다.");
      }
    }
    throw new IllegalArgumentException("revision 형식이 올바르지 않습니다.");
  }

  private int utf8Len(String s) {
    return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
  }
}
