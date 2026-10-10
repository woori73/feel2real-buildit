package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.CreateProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditProjectResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.ProjectResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.RevertProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.SnapshotResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.SourceUploadResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service.ArtCraftEditorService;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service.ArtCraftEditorService.PdfFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ArtCraft 웹 편집기 AI 제어 REST API.
 *
 * <p>한글 패치된 PdfCraft CLI를 서버에서 실행해 프로젝트 문서를 편집하는 제어 서버다.
 * 모든 엔드포인트는 JWT 인증이 필요하며 ({@code /api/**} catch-all),
 * 학원별 격리·낙관적 잠금·명령 화이트리스트를 적용한다.
 *
 * <p>ADMIN은 대상 학원을 {@code academyId} 쿼리 파라미터(조회계) 또는
 * 요청 본문(생성)으로 명시한다. TUTOR는 본인 소속 학원으로 자동 격리된다.
 */
@RestController
@RequestMapping("/api/artcraft")
@RequiredArgsConstructor
public class ArtCraftEditorController {

  private final ArtCraftEditorService editorService;

  /**
   * 프로젝트 생성 (ADMIN/TUTOR).
   *
   * <p>TUTOR는 본인 학원에, ADMIN은 본문의 {@code academyId} 학원에 생성한다.
   */
  @PostMapping("/projects")
  public ResponseEntity<ProjectResponse> createProject(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody CreateProjectRequest request) {
    return ResponseEntity.ok(editorService.createProject(principal, request));
  }

  /** 프로젝트 조회 (버전 포함). */
  @GetMapping("/projects/{id}")
  public ResponseEntity<ProjectResponse> getProject(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId) {
    return ResponseEntity.ok(editorService.getProject(principal, projectId, academyId));
  }

  /**
   * AI 편집 명령 실행 (ADMIN/TUTOR, 낙관적 잠금).
   *
   * <p>{@code expectedVersion}이 현재 버전과 다르면 409 + {@code currentVersion}을
   * 반환하고 편집은 수행되지 않는다. 명령은 화이트리스트 검증을 통과해야 하며
   * 순서대로 {@code pdfcraft-cli run --script} 로 실행된다.
   */
  @PostMapping("/projects/{id}/edit")
  public ResponseEntity<EditProjectResponse> editProject(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId,
      @RequestBody EditProjectRequest request) {
    return ResponseEntity.ok(
        editorService.editProject(principal, projectId, academyId, request));
  }

  /**
   * 편집 소스 업로드 (ADMIN/TUTOR, multipart).
   *
   * <p>HTML 업로드 시 원본은 {@code source.html}로 보존하고 headless Chromium
   * {@code --print-to-pdf}로 {@code source.pdf}를 생성한다 (Chromium이 없으면 503).
   * PDF 업로드 시 그대로 {@code source.pdf}로 저장한다.
   * 기존 소스가 있으면 덮어쓴다. {@code edit}은 {@code source.pdf}가 있을 때만
   * 허용된다.
   *
   * <p>인증은 {@code /api/**} catch-all을 그대로 따른다 (별도 SecurityConfig
   * 규칙 불필요).
   */
  @PostMapping(value = "/projects/{id}/source",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SourceUploadResponse> uploadSource(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId,
      @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(
        editorService.uploadSource(principal, projectId, academyId, file));
  }

  /** 버전(스냅샷) 목록 조회. */
  @GetMapping("/projects/{id}/snapshots")
  public ResponseEntity<List<SnapshotResponse>> listSnapshots(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId) {
    return ResponseEntity.ok(editorService.listSnapshots(principal, projectId, academyId));
  }

  /**
   * 특정 버전으로 되돌리기 (ADMIN/TUTOR).
   *
   * <p>되돌리기 전 현재 상태가 스냅샷으로 보존되므로 되돌리기를 되돌릴 수 있다.
   * 결과는 새 버전({@code current+1})이 된다.
   */
  @PostMapping("/projects/{id}/revert")
  public ResponseEntity<ProjectResponse> revertProject(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId,
      @RequestBody RevertProjectRequest request) {
    return ResponseEntity.ok(
        editorService.revertProject(principal, projectId, academyId, request));
  }

  /**
   * 프로젝트 변경 알림 SSE 스트림 (타임아웃 30분).
   *
   * <p>생성·편집·되돌리기 시 {@code project} 이름의 이벤트가 전달된다.
   * 브라우저는 이 알림을 받아 "서버에서 변경됨" UI를 표시한다.
   */
  @GetMapping(value = "/projects/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribeEvents(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId) {
    return editorService.subscribe(principal, projectId, academyId);
  }

  /** 프로젝트의 최신 PDF 다운로드. */
  @GetMapping("/projects/{id}/pdf")
  public ResponseEntity<byte[]> downloadPdf(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable("id") String projectId,
      @RequestParam(required = false) Long academyId) {
    PdfFile pdf = editorService.downloadPdf(principal, projectId, academyId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + pdf.fileName() + "\"")
        .body(pdf.bytes());
  }
}

