package f2r.tutor_solution.private_tutor_solution_back.domain.source.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamConfirmRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamConfirmResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamReviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.PendingReviewSourceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.service.ExamReviewService;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.service.SourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

  private final ExamReviewService examReviewService;
  private final SourceRepository sourceRepository;
  private final SourceService sourceService;
  private final AuthenticatedUserResolver authResolver;

  /** MVP §5.0 4단계 — 학생 대시보드 "검증 대기" 카드용 목록. */
  @GetMapping("/pending-review")
  public ResponseEntity<List<PendingReviewSourceResponse>> listPendingReview(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
    Long userId = authResolver.resolveUserId(principal, targetUserId);
    return ResponseEntity.ok(examReviewService.listPendingReview(userId));
  }

  /** MVP §5.0 5단계 — 검증 페이지가 그릴 데이터(메타 + 채점 결과) */
  @GetMapping("/{sourceId}/exam-review")
  public ResponseEntity<ExamReviewResponse> getReview(@PathVariable Long sourceId) {
    // sourceId 기반 — ownership 검증은 ExamReviewService에서 별도로 (TODO: 향후 보강)
    return ResponseEntity.ok(examReviewService.review(sourceId));
  }

  /** MVP §5.0 6단계 — 학생 [전체 확정]. questions/answers/scores/exams INSERT + source COMPLETED 전이. */
  @PostMapping("/{sourceId}/exam-confirm")
  public ResponseEntity<ExamConfirmResponse> confirm(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long sourceId,
      @RequestParam(value = "targetUserId", required = false) Long targetUserId,
      @RequestBody ExamConfirmRequest request) {
    Long userId = authResolver.resolveUserId(principal, targetUserId);
    return ResponseEntity.ok(examReviewService.confirm(sourceId, userId, request));
  }

  /**
   * 자료 영구 삭제. 본인 또는 ADMIN(임퍼소네이트)만 허용.
   * cascade: conversation_sources, student_answers, student_scores, storage 파일 모두 정리.
   * 자세한 동작은 {@link SourceService#delete(Long)} 참고.
   */
  @DeleteMapping("/{sourceId}")
  public ResponseEntity<Void> deleteSource(@PathVariable Long sourceId) {
    sourceService.delete(sourceId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Phase 10 — 라이트박스 미리보기용 이미지 서빙.
   * IMAGE면 {@code page} 파라미터를 무시하고 단일 JPEG. PDF면 page_NN.jpg 한 페이지.
   *
   * <p>본인이 업로드한 source만 열람 가능 (ADMIN은 임퍼소네이트로 모두 허용).
   */
  @GetMapping("/{sourceId}/file")
  public ResponseEntity<byte[]> getFile(
      @PathVariable Long sourceId,
      @RequestParam(value = "page", defaultValue = "1") int page) {
    Source src = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("source를 찾을 수 없습니다: " + sourceId));
    authResolver.verifyOwnsSource(src);  // 본인 자료 아니면 403
    try {
      byte[] bytes = sourceService.readPageBytes(src, page);
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
          .contentType(MediaType.parseMediaType(servedContentType(src)))
          .body(bytes);
    } catch (IOException e) {
      log.warn("파일 읽기 실패: sourceId={}, page={}, reason={}", sourceId, page, e.getMessage());
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * 학생이 자기 자료를 페이지 단위 JPEG로 다운로드.
   *
   * <p>{@link #getFile} 와 동일한 bytes를 내려주지만 {@code Content-Disposition: attachment}
   * 헤더로 브라우저가 항상 다운로드하게 강제한다. 캐시는 비활성화 (다운로드 캐싱은 무의미).
   *
   * <p>원본은 보관되지 않으므로 PDF였더라도 페이지별 JPEG 1장만 받을 수 있다 (MVP §5.0).
   */
  @GetMapping("/{sourceId}/download")
  public ResponseEntity<byte[]> downloadFile(
      @PathVariable Long sourceId,
      @RequestParam(value = "page", defaultValue = "1") int page) {
    Source src = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("source를 찾을 수 없습니다: " + sourceId));
    authResolver.verifyOwnsSource(src);
    try {
      byte[] bytes = sourceService.readPageBytes(src, page);
      String filename = buildDownloadFilename(
          src.getOriginalFileName(), src.getFileType(), page, storedExtension(src));
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
          .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionAttachment(filename))
          .contentType(MediaType.parseMediaType(servedContentType(src)))
          .body(bytes);
    } catch (IOException e) {
      log.warn("파일 다운로드 실패: sourceId={}, page={}, reason={}", sourceId, page, e.getMessage());
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * 서빙할 Content-Type. PDF는 페이지가 항상 JPEG({@code page_NN.jpg})이고, IMAGE는 저장 키의
   * 확장자를 따른다 — 애니메이션 GIF는 {@code image/gif}로 내려가야 움직인다.
   */
  /** 저장된 실제 포맷의 확장자 (점 없음). */
  static String storedExtension(Source src) {
    return ImageSanitizer.GIF_CONTENT_TYPE.equals(servedContentType(src))
        ? ImageSanitizer.GIF_EXTENSION
        : ImageSanitizer.JPEG_EXTENSION;
  }

  static String servedContentType(Source src) {
    if ("PDF".equalsIgnoreCase(src.getFileType())) {
      return ImageSanitizer.JPEG_CONTENT_TYPE;
    }
    return ImageSanitizer.contentTypeForStorageKey(src.getStorageKey());
  }

  /**
   * 원본 파일명에서 확장자를 떼고 <b>실제 저장 포맷</b>의 확장자를 붙인다. PDF 페이지는
   * {@code _p{N}} suffix를 붙인다.
   *
   * <p>저장 확장자를 그대로 쓰는 이유: 애니메이션 GIF를 {@code .jpg}로 내려주면 OS가 JPEG로 열려다
   * 실패한다. 예전에는 모든 저장물이 JPEG라 .jpg 고정이 맞았지만 더는 아니다.
   */
  static String buildDownloadFilename(String originalName, String fileType, int page, String extension) {
    String base = (originalName == null || originalName.isBlank()) ? "download" : originalName;
    int dot = base.lastIndexOf('.');
    if (dot > 0) base = base.substring(0, dot);
    boolean isPdf = "PDF".equalsIgnoreCase(fileType);
    return isPdf ? base + "_p" + page + ".jpg" : base + "." + extension;
  }

  /**
   * 한글 등 비-ASCII 파일명을 안전하게 내려주는 Content-Disposition 값.
   * RFC 5987 — {@code filename}(ASCII 폴백) + {@code filename*}(UTF-8) 둘 다 제공.
   */
  static String contentDispositionAttachment(String filename) {
    String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
    return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
  }
}
