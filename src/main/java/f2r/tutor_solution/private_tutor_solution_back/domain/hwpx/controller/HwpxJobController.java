package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.CreateHwpxJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.HwpxJobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service.HwpxJobService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * HWPX 편집 작업 REST API.
 *
 * <p>기존 {@code /api/publishing/*}와 독립적이다.
 * 모든 엔드포인트는 JWT 인증 + tutorId 소유자 격리를 적용한다.
 */
@RestController
@RequestMapping("/api/hwpx/jobs")
@RequiredArgsConstructor
public class HwpxJobController {

  private final HwpxJobService jobService;

  /**
   * HWP/HWPX 업로드 + 셀 수정 작업 생성 → 202 QUEUED.
   *
   * <p>multipart: file (hwp|hwpx), title, edits (JSON)
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<HwpxJobResponse> createJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestPart("file") MultipartFile file,
      @RequestParam("title") String title,
      @RequestPart("edits") CreateHwpxJobRequest request) throws IOException {
    String originalName = file.getOriginalFilename();
    String ext = originalName != null && originalName.toLowerCase().endsWith(".hwp")
        ? "hwp" : "hwpx";
    if (!originalName.toLowerCase().endsWith(".hwp")
        && !originalName.toLowerCase().endsWith(".hwpx")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hwp/hwpx 파일만 가능");
    }

    HwpxJobResponse response = jobService.createJob(principal, request, ext);

    // 업로드 파일을 작업 디렉터리에 저장 (Worker가 사용)
    // 실제 경로는 HwpxWorker와 공유하는 규칙을 따른다
    Path workDir = Paths.get("/tmp/hwpx/jobs", String.valueOf(response.getId()));
    Files.createDirectories(workDir);
    Path inputFile = workDir.resolve("input." + ext);
    file.transferTo(inputFile);

    return ResponseEntity.accepted().body(response);
  }

  /** 내 작업 목록. */
  @GetMapping
  public ResponseEntity<List<HwpxJobResponse>> listJobs(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(jobService.listJobs(principal));
  }

  /** 작업 상태 조회 (폴링용). */
  @GetMapping("/{jobId}")
  public ResponseEntity<HwpxJobResponse> getJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(jobService.getJob(principal, jobId));
  }

  /** 결과 HWPX 다운로드. */
  @GetMapping("/{jobId}/download")
  public ResponseEntity<Resource> download(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    HwpxArtifact artifact = jobService.getArtifact(principal, jobId);
    Path path = Paths.get(artifact.getFilePath());
    if (!Files.exists(path)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 없음");
    }
    Resource resource = new FileSystemResource(path);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"result.hwpx\"")
        .contentType(MediaType.parseMediaType(
            "application/vnd.hancom.hwpx"))
        .body(resource);
  }

  /** 작업 취소. */
  @PostMapping("/{jobId}/cancel")
  public ResponseEntity<HwpxJobResponse> cancelJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    return ResponseEntity.ok(jobService.cancelJob(principal, jobId));
  }

  /** 작업 삭제. */
  @DeleteMapping("/{jobId}")
  public ResponseEntity<Void> deleteJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    jobService.deleteJob(principal, jobId);
    return ResponseEntity.noContent().build();
  }
}
