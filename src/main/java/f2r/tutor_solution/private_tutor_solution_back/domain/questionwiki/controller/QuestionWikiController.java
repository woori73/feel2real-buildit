package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service.QuestionThreadService;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service.QuestionWikiService;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.web.FileDownloadResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 공개 BuildIt Wiki 문항별 Question Wiki + THREAD API.
 *
 * <p>GET(목록/상세/Context/첨부 다운로드)은 SecurityConfig 에서 permitAll(비로그인 read).
 * write(글/답글/수정/삭제/첨부)는 {@code /api/**} catch-all 로 인증. 자료 링크 관리는 {@code /api/admin/**}(ROLE_ADMIN).
 *
 * <p>{@code GET /api/wiki/questions/{id}/context} 는 문항 전체 맥락을 한 번에 돌려준다 — 향후 MCP
 * {@code get_question_wiki_context(questionWikiId)} 가 그대로 감싼다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wiki/questions")
public class QuestionWikiController {

  private static final String MOBILE_PREVIEW_HEAD = """
      <meta name="viewport" content="width=1100, initial-scale=1">
      <style>
        html, body { margin-left: 0 !important; margin-right: 0 !important; }
      </style>
      """;

  private final QuestionWikiService questionWikiService;
  private final QuestionThreadService questionThreadService;

  /* ── 문항 ── */

  @GetMapping
  public ResponseEntity<List<QuestionWikiSummaryResponse>> list(
      @RequestParam(required = false) String subject,
      @RequestParam(required = false) Integer grade,
      @RequestParam(required = false) Integer examYear,
      @RequestParam(required = false) String examLabel) {
    return ResponseEntity.ok(questionWikiService.listNav(subject, grade, examYear, examLabel));
  }

  @GetMapping("/{questionWikiId}")
  public ResponseEntity<QuestionWikiSummaryResponse> summary(@PathVariable Long questionWikiId) {
    return ResponseEntity.ok(questionWikiService.getSummary(questionWikiId));
  }

  @GetMapping("/{questionWikiId}/context")
  public ResponseEntity<QuestionWikiContextResponse> context(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long questionWikiId) {
    return ResponseEntity.ok(questionWikiService.getContext(questionWikiId, principal));
  }

  /* ── THREAD 글 / 답글 ── */

  @PostMapping(value = "/{questionWikiId}/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<QuestionWikiContextResponse> createPost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long questionWikiId,
      @RequestBody QuestionThreadPostRequest request) {
    return ResponseEntity.ok(questionThreadService.createPost(principal, questionWikiId, request));
  }

  @PostMapping(value = "/{questionWikiId}/posts/{parentPostId}/replies",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<QuestionWikiContextResponse> createReply(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long questionWikiId,
      @PathVariable Long parentPostId,
      @RequestBody QuestionThreadPostRequest request) {
    QuestionThreadPostRequest withParent = new QuestionThreadPostRequest(
        request == null ? null : request.category(),
        null,
        request == null ? null : request.title(),
        request == null ? null : request.content(),
        parentPostId,
        null,
        null);
    return ResponseEntity.ok(questionThreadService.createPost(principal, questionWikiId, withParent));
  }

  @PutMapping("/posts/{postId}")
  public ResponseEntity<QuestionWikiContextResponse> updatePost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestBody QuestionThreadPostRequest request) {
    return ResponseEntity.ok(questionThreadService.updatePost(principal, postId, request));
  }

  @DeleteMapping("/posts/{postId}")
  public ResponseEntity<QuestionWikiContextResponse> deletePost(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId) {
    return ResponseEntity.ok(questionThreadService.hidePost(principal, postId));
  }

  /* ── 첨부 ── */

  @PostMapping(value = "/posts/{postId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<QuestionWikiContextResponse> addAttachments(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long postId,
      @RequestParam(value = "files", required = false) List<MultipartFile> files,
      @RequestParam(value = "files[]", required = false) List<MultipartFile> bracketFiles) {
    return ResponseEntity.ok(
        questionThreadService.addAttachments(principal, postId, mergeFiles(files, bracketFiles)));
  }

  @DeleteMapping("/attachments/{attachmentId}")
  public ResponseEntity<Void> deleteAttachment(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long attachmentId) {
    questionThreadService.deleteAttachment(principal, attachmentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/attachments/{attachmentId}/file")
  public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long attachmentId) {
    QuestionThreadService.AttachmentFileBundle bundle =
        questionThreadService.getAttachmentFile(attachmentId);
    return FileDownloadResponses.attachment(bundle.bytes(), bundle.contentType(), bundle.filename());
  }

  /**
   * HTML 학습자료 격리 미리보기(공개). sandboxed iframe 안에서만 열도록 CSP {@code sandbox} +
   * {@code frame-ancestors 'self'} + {@code default-src 'none'}(스크립트 차단, CSS/이미지만 허용).
   * 다운로드는 여전히 {@code viewUrl}(attachment 강제)이 담당한다.
   */
  @GetMapping("/materials/{linkId}/preview")
  public ResponseEntity<byte[]> materialHtmlPreview(
      @PathVariable Long linkId,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    QuestionWikiService.HtmlPreview preview = questionWikiService.getHtmlPreview(linkId);
    byte[] body = isMobileUserAgent(userAgent) ? mobileFitHtml(preview.bytes()) : preview.bytes();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy",
            "sandbox; default-src 'none'; img-src data: https:; media-src data: https:; "
                + "style-src 'unsafe-inline' https:; font-src https: data:; frame-ancestors 'self'")
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
        .body(body);
  }

  private boolean isMobileUserAgent(String userAgent) {
    if (userAgent == null) {
      return false;
    }
    String ua = userAgent.toLowerCase();
    return ua.contains("android") || ua.contains("iphone") || ua.contains("ipad") || ua.contains("mobile");
  }

  private byte[] mobileFitHtml(byte[] original) {
    String html = new String(original, StandardCharsets.UTF_8);
    // The uploaded worksheets often have desktop fixed widths. A wider layout viewport makes
    // mobile Chrome scale the whole worksheet into the iframe instead of clipping both sides.
    html = html.replaceAll("(?is)<meta\\s+[^>]*name\\s*=\\s*['\"]viewport['\"][^>]*>", "");
    int headEnd = html.toLowerCase().indexOf("</head>");
    if (headEnd >= 0) {
      html = html.substring(0, headEnd) + MOBILE_PREVIEW_HEAD + html.substring(headEnd);
    } else {
      html = MOBILE_PREVIEW_HEAD + html;
    }
    return html.getBytes(StandardCharsets.UTF_8);
  }

  private List<MultipartFile> mergeFiles(List<MultipartFile> files, List<MultipartFile> bracketFiles) {
    if (files == null || files.isEmpty()) {
      return bracketFiles;
    }
    if (bracketFiles == null || bracketFiles.isEmpty()) {
      return files;
    }
    List<MultipartFile> merged = new ArrayList<>(files);
    merged.addAll(bracketFiles);
    return merged;
  }
}
