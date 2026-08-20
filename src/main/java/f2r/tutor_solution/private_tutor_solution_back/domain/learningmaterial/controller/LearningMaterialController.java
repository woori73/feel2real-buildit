package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.dto.LearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.service.LearningMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Phase 2 §5.5 학습자료 부여/풀이 재업로드 API.
 *
 * <p>2개 prefix를 다룬다:
 * <ul>
 *   <li>{@code POST /api/conversations/{id}/learning-materials} — 부여(생성)는 대화 컨텍스트가 있으므로 conversations 하위.</li>
 *   <li>{@code /api/learning-materials/**} — 그 외(목록/다운로드/완료/삭제)는 material id 기반.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class LearningMaterialController {

  private final LearningMaterialService service;
  private final AuthenticatedUserResolver authResolver;

  /**
   * Admin/TUTOR가 학생에게 학습자료를 부여.
   * multipart/form-data: file (PDF/이미지, 선택 — 없으면 title+description만으로 부여), title (필수), description (선택)
   */
  @PostMapping("/api/conversations/{id}/learning-materials")
  public ResponseEntity<LearningMaterialResponse> assign(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable("id") Long conversationId,
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam("title") String title,
      @RequestParam(value = "description", required = false) String description) {
    Long assignerUserId = principal.userId();
    return ResponseEntity.ok(service.assign(conversationId, assignerUserId, file, title, description));
  }

  /**
   * 학생 대시보드 "받은 학습자료" 목록.
   * 학생: 본인 자료. ADMIN/TUTOR: ?targetStudentId=로 임퍼소네이트 (TUTOR는 매핑된 학생만).
   */
  @GetMapping("/api/learning-materials")
  public ResponseEntity<List<LearningMaterialResponse>> listForStudent(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "targetStudentId", required = false) Long targetStudentId) {
    Long studentId = authResolver.resolveStudentId(principal, targetStudentId);
    return ResponseEntity.ok(service.listForStudent(studentId));
  }

  /** 대화별 학습자료 목록 (대화 화면 사이드바 등에서 사용). */
  @GetMapping("/api/conversations/{id}/learning-materials")
  public ResponseEntity<List<LearningMaterialResponse>> listForConversation(
      @PathVariable("id") Long conversationId) {
    return ResponseEntity.ok(service.listForConversation(conversationId));
  }

  /**
   * 문제 파일 다운로드. attachment 강제. PDF/이미지 모두 원본 Content-Type에 맞춰 응답한다.
   * ownership 가드는 service에서 (학생 본인/Admin/매핑 TUTOR 통과).
   */
  @GetMapping("/api/learning-materials/{id}/download")
  public ResponseEntity<byte[]> downloadProblem(@PathVariable("id") Long materialId) {
    LearningMaterialService.ProblemFileBundle bundle = service.getProblemFileBundle(materialId);
    String filename = bundle.originalFileName() != null && !bundle.originalFileName().isBlank()
        ? bundle.originalFileName()
        : "learning-material-" + materialId;
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionAttachment(filename))
        .contentType(MediaType.parseMediaType(bundle.contentType()))
        .body(bundle.bytes());
  }

  /**
   * 문제 파일 inline 미리보기 — 화면에 띄워 보기용(iframe 등).
   * {@link #downloadProblem}과 동일 bytes·ownership 가드(service)지만 {@code Content-Disposition: inline}.
   * Cache-Control은 answer-file 미리보기와 같은 inline 선례에 맞춰 {@code max-age=300}
   * (다운로드 엔드포인트의 {@code no-store}와는 의도적으로 다름).
   */
  @GetMapping("/api/learning-materials/{id}/view")
  public ResponseEntity<byte[]> viewProblem(@PathVariable("id") Long materialId) {
    LearningMaterialService.ProblemFileBundle bundle = service.getProblemFileBundle(materialId);
    String filename = bundle.originalFileName() != null && !bundle.originalFileName().isBlank()
        ? bundle.originalFileName()
        : "learning-material-" + materialId;
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType(bundle.contentType()))
        .body(bundle.bytes());
  }

  /**
   * 학생이 풀이 결과물 업로드 (재업로드 시 기존 결과 대치).
   * multipart/form-data: files[] — 1개 이상 (PDF / JPG / PNG / WebP).
   */
  @PostMapping("/api/learning-materials/{id}/complete")
  public ResponseEntity<LearningMaterialResponse> complete(
      @PathVariable("id") Long materialId,
      @RequestParam(value = "files", required = false) List<MultipartFile> files) {
    return ResponseEntity.ok(service.complete(materialId, files));
  }

  /**
   * 학생/Admin/매핑 TUTOR가 풀이 파일 1건을 미리보기.
   * Content-Type은 원본 확장자로 추론 (PDF → application/pdf, IMAGE → image/...).
   * 브라우저가 inline 표시.
   */
  @GetMapping("/api/learning-materials/{id}/answers/{aid}/file")
  public ResponseEntity<byte[]> getAnswerFile(
      @PathVariable("id") Long materialId,
      @PathVariable("aid") Long answerId) {
    LearningMaterialService.AnswerFileBundle bundle = service.getAnswerFile(materialId, answerId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .contentType(MediaType.parseMediaType(bundle.contentType()))
        .body(bundle.bytes());
  }

  /**
   * 강사/Admin이 학생 풀이에 대한 피드백을 등록한다.
   * multipart/form-data: feedbackText 또는 files[] 중 하나 필수. 첨부 시 PDF/이미지 허용.
   * 가드는 service에서 (ADMIN 또는 담당 TUTOR, 학생 차단).
   */
  @PostMapping("/api/learning-materials/{id}/feedback")
  public ResponseEntity<LearningMaterialResponse> addFeedback(
      @PathVariable("id") Long materialId,
      @RequestParam(value = "feedbackText", required = false) String feedbackText,
      @RequestParam(value = "files", required = false) List<MultipartFile> files) {
    return ResponseEntity.ok(service.addFeedback(materialId, feedbackText, files));
  }

  /**
   * 강사/Admin/학생이 피드백 파일 1건을 미리보기.
   * {@link #getAnswerFile}과 동일 패턴 — Content-Type 추론 + inline 표시.
   */
  @GetMapping("/api/learning-materials/{id}/feedback/{fid}/file")
  public ResponseEntity<byte[]> getFeedbackFile(
      @PathVariable("id") Long materialId,
      @PathVariable("fid") Long feedbackId) {
    LearningMaterialService.AnswerFileBundle bundle = service.getFeedbackFile(materialId, feedbackId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .contentType(MediaType.parseMediaType(bundle.contentType()))
        .body(bundle.bytes());
  }

  /**
   * 강사/Admin이 피드백 1건 삭제. 가드는 service에서 (ADMIN 또는 담당 TUTOR, 학생 차단).
   * 삭제 후 갱신된 material(feedbacks 포함)을 반환.
   */
  @DeleteMapping("/api/learning-materials/{id}/feedback/{fid}")
  public ResponseEntity<LearningMaterialResponse> deleteFeedback(
      @PathVariable("id") Long materialId,
      @PathVariable("fid") Long feedbackId) {
    return ResponseEntity.ok(service.deleteFeedback(materialId, feedbackId));
  }

  /**
   * 강사/Admin이 학습자료 '확인(채점 완료)' 상태를 토글.
   * body: {"reviewed": true|false}. 가드는 service에서 (ADMIN 또는 담당 TUTOR, 학생 차단).
   */
  @PatchMapping("/api/learning-materials/{id}/reviewed")
  public ResponseEntity<LearningMaterialResponse> setReviewed(
      @PathVariable("id") Long materialId,
      @RequestBody ReviewedRequest request) {
    return ResponseEntity.ok(service.setReviewed(materialId, request.reviewed()));
  }

  /** reviewed 토글 요청 body. */
  public record ReviewedRequest(boolean reviewed) {}

  /** 부여자(또는 Admin)가 학습자료 영구 삭제. cascade는 service가 담당. */
  @DeleteMapping("/api/learning-materials/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable("id") Long materialId) {
    service.delete(materialId, principal.userId());
    return ResponseEntity.noContent().build();
  }

  /**
   * 한글 파일명을 안전하게 내려주는 Content-Disposition.
   * RFC 5987 — {@code filename}(ASCII 폴백) + {@code filename*}(UTF-8) 둘 다 제공.
   */
  static String contentDispositionAttachment(String filename) {
    String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
    return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
  }
}
