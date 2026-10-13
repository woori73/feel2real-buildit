package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorPublished;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
@Slf4j
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
    // 선택 필드: AI 제안을 적용한 저장이면 제안 ID(없으면 기존 동작 그대로)
    Long appliedProposalId = asProposalId(request.get("appliedProposalId"));

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
    VisualEditorProposalPort.ProposalApplyResult applied = null;
    try {
      if (appliedProposalId == null) {
        // 기존 경로 — appliedProposalId가 없으면 이전과 완전히 같다.
        draft = visualEditorService.saveDraft(
            principal, html, css, projectData, revision);
      } else {
        // 저장 + Proposal APPLIED를 한 트랜잭션으로. 실패하면 초안도 저장되지 않는다.
        VisualEditorService.SaveOutcome outcome = visualEditorService.saveDraftWithProposal(
            principal, html, css, projectData, revision, appliedProposalId);
        draft = outcome.draft();
        applied = outcome.appliedProposal();
      }
    } catch (VisualEditorService.DraftConflictException e) {
      return conflict(e.getMessage(), "REVISION_CONFLICT");
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("status", "DRAFT_SAVED");
    response.put("revision", draft.getRevision());
    response.put("updatedAt", draft.getUpdatedAt().toString());
    response.put("message", "초안이 저장되었습니다.");
    if (applied != null) {
      Map<String, Object> proposal = new HashMap<>();
      proposal.put("proposalId", applied.proposalId());
      proposal.put("status", applied.status());
      proposal.put("appliedRevision", applied.appliedRevision());
      response.put("appliedProposal", proposal);
    }
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

  @PostMapping("/visual-editor/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    Long revision = asRevision(request.get("revision"));
    if (revision == null) {
      return badRequest("revision이 필요합니다. 초안을 새로고침 후 다시 시도하세요.");
    }
    VisualEditorService.PublishOutcome outcome;
    try {
      // draft → published 복사와 renderMode 변경을 하나의 트랜잭션으로 처리
      outcome = visualEditorService.publish(principal, revision);
    } catch (VisualEditorService.DraftConflictException e) {
      return conflict(e.getMessage());
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("status", "PUBLISHED");
    response.put("renderMode", outcome.renderMode());
    response.put("publicPath", outcome.publicPath());
    response.put("publicReachable", outcome.publicReachable());
    // 응답 문구는 실제 방문자 노출 상태와 일치해야 한다.
    response.put("message", outcome.publicReachable()
        ? "게시되었습니다. 공개 홈페이지에 편집기 디자인이 반영됩니다."
        : "게시본은 저장되었지만 학원이 비공개(미발행) 상태라 방문자에게는 아직 보이지 않습니다. "
            + "학원을 공개로 전환하면 반영됩니다.");
    return ResponseEntity.ok(response);
  }

  /**
   * 게시를 취소한다. renderMode를 template(P0)으로 되돌린다.
   * published 행과 P0 데이터는 유지된다.
   */
  @PostMapping("/visual-editor/unpublish")
  public ResponseEntity<Map<String, Object>> unpublish(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    VisualEditorService.PublishOutcome outcome;
    try {
      outcome = visualEditorService.unpublish(principal);
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("status", "UNPUBLISHED");
    response.put("renderMode", outcome.renderMode());
    response.put("publicPath", outcome.publicPath());
    response.put("publicReachable", outcome.publicReachable());
    response.put("message", "게시 취소되었습니다. 기존 P0 홈페이지로 되돌아갑니다.");
    return ResponseEntity.ok(response);
  }

  /**
   * 편집기 화면 상태(게시 여부·미게시 변경·공개 경로·방문자 노출 여부)를 반환한다.
   * 게시 버튼과 상태 표시가 서버가 아는 사실만 보여주도록 하는 용도다.
   */
  @GetMapping("/visual-editor/status")
  public ResponseEntity<Map<String, Object>> getStatus(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    VisualEditorService.EditorStatus st = visualEditorService.getEditorStatus(principal);
    Map<String, Object> response = new HashMap<>();
    response.put("draftExists", st.draftExists());
    response.put("draftRevision", st.draftRevision());
    response.put("publishedAt", st.publishedAt());
    response.put("renderMode", st.renderMode());
    response.put("hasUnpublishedChanges", st.hasUnpublishedChanges());
    response.put("publicPath", st.publicPath());
    response.put("publicReachable", st.publicReachable());
    response.put("academyName", st.academyName());
    return ResponseEntity.ok(response);
  }

  /**
   * P0 빌더 DOM을 초안으로 가져온다.
   *
   * <p>브라우저에서 P0 미리보기 DOM을 직렬화해 전송한다.
   * 기존 초안이 있으면 무단 덮어쓰기를 방지하기 위해 409를 반환한다.
   */
  @PostMapping("/visual-editor/import")
  public ResponseEntity<Map<String, Object>> importFromP0(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
      JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    String rawHtml = asString(request, "html");
    String rawCss = asString(request, "css");

    int totalBytes = utf8Len(rawHtml) + utf8Len(rawCss);
    if (totalBytes > VisualEditorService.MAX_REQUEST_SIZE) {
      return badRequest("요청 크기가 제한(33MB)을 초과했습니다.");
    }

    VisualEditorDraft draft;
    try {
      draft = visualEditorService.importFromP0(principal, rawHtml, rawCss);
    } catch (VisualEditorService.DraftConflictException e) {
      return conflict(e.getMessage());
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("status", "IMPORTED");
    response.put("revision", draft.getRevision());
    response.put("message", "P0 홈페이지를 초안으로 가져왔습니다.");
    return ResponseEntity.ok(response);
  }

  /** 제안 적용 실패(404/409/422/400). 저장 트랜잭션은 이미 롤백되었다. */
  @ExceptionHandler(VisualEditorProposalPort.ProposalException.class)
  public ResponseEntity<Map<String, Object>> handleProposal(
      VisualEditorProposalPort.ProposalException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", e.getHttpStatus());
    response.put("message", e.getMessage());
    response.put("error", HttpStatus.valueOf(e.getHttpStatus()).getReasonPhrase());
    response.put("code", e.getCode());
    if (!e.getDetails().isEmpty()) {
      response.put("details", e.getDetails());
    }
    return ResponseEntity.status(e.getHttpStatus()).body(response);
  }

  @ExceptionHandler(VisualEditorService.DraftConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(
      VisualEditorService.DraftConflictException e) {
    return conflict(e.getMessage(), "REVISION_CONFLICT");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(
      IllegalArgumentException e) {
    return badRequest(e.getMessage());
  }

  /** 소유 학원이 없거나 권한이 없는 호출은 500이 아니라 403이다. */
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(
      org.springframework.security.access.AccessDeniedException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 403);
    response.put("message", e.getMessage() == null ? "접근 권한이 없습니다." : e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  /** 서비스가 던진 의도된 HTTP 상태(404 등)를 그대로 전달한다. */
  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(
      org.springframework.web.server.ResponseStatusException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", e.getStatusCode().value());
    response.put("message", e.getReason() == null ? "요청을 처리할 수 없습니다." : e.getReason());
    return ResponseEntity.status(e.getStatusCode()).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
    log.error("시각 편집기 API 처리 중 예상치 못한 오류", e);
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

  /** 클라이언트가 충돌 종류를 구분할 수 있도록 code를 함께 내려준다. */
  private ResponseEntity<Map<String, Object>> conflict(String message, String code) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", 409);
    response.put("message", message);
    response.put("error", "Conflict");
    response.put("code", code);
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

  /**
   * appliedProposalId를 엄격하게 파싱한다. 양의 정수(숫자 또는 숫자 문자열)만 허용하고,
   * 그 외 타입·형식은 400을 위한 예외를 던진다. null이면 "제안 없이 저장"이다.
   */
  private Long asProposalId(Object value) {
    if (value == null) {
      return null;
    }
    long id;
    if (value instanceof Integer || value instanceof Long) {
      id = ((Number) value).longValue();
    } else if (value instanceof String s) {
      try {
        id = Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("appliedProposalId 형식이 올바르지 않습니다.");
      }
    } else {
      throw new IllegalArgumentException("appliedProposalId 형식이 올바르지 않습니다.");
    }
    if (id <= 0) {
      throw new IllegalArgumentException("appliedProposalId 형식이 올바르지 않습니다.");
    }
    return id;
  }

  private int utf8Len(String s) {
    return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
  }
}
