package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.config.HwpxProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.CreateHwpxJobRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.HwpxJobResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service.HwpxJobService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 *
 * <p>multipart 요청 형식:
 * <ul>
 *   <li>file: HWP/HWPX 파일 (Content-Type: application/octet-stream)</li>
 *   <li>title: 작업 제목 (text)</li>
 *   <li>edits: 셀 수정 JSON (Content-Type: application/json)
 *       예: {"title":"...","edits":[{"table":0,"row":1,"col":1,"text":"..."}]}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/hwpx/jobs")
@RequiredArgsConstructor
public class HwpxJobController {

  private final HwpxJobService jobService;
  private final HwpxProperties props;

  /**
   * HWP/HWPX 업로드 + 셀 수정 작업 생성 → 202 QUEUED.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> createJob(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestPart("file") MultipartFile file,
      @RequestParam("title") String title,
      @RequestPart("edits") CreateHwpxJobRequest request) throws IOException {
    // Worker가 꺼져 있으면(예: 운영에 Python Worker가 아직 배포되지 않음) 작업을 만들지 않는다.
    // 만들면 아무도 처리하지 않아 QUEUED로 영원히 남고 업로드 파일만 서버에 쌓인다.
    if (!props.isWorkerEnabled()) {
      // 기본 설정에서는 ResponseStatusException의 reason이 응답 본문에 실리지 않으므로 본문을 직접 돌려준다.
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("code", "HWPX_UNAVAILABLE",
              "message", "HWPX 편집 기능은 현재 일시적으로 제공되지 않습니다. 잠시 후 다시 시도해 주세요."));
    }
    String originalName = file.getOriginalFilename();
    if (originalName == null
        || (!originalName.toLowerCase().endsWith(".hwp")
            && !originalName.toLowerCase().endsWith(".hwpx"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hwp/hwpx 파일만 가능");
    }
    String ext = originalName.toLowerCase().endsWith(".hwp") ? "hwp" : "hwpx";

    // 1. 작업 생성 (ID 확보)
    HwpxJobResponse response = jobService.createJob(principal, request, ext);

    // 2. 업로드 파일을 작업 디렉터리에 저장 (HwpxWorker와 동일한 storage-root 사용)
    Path workDir = Path.of(props.getStorageRoot(), "jobs", String.valueOf(response.getId()));
    Files.createDirectories(workDir);
    Path inputFile = workDir.resolve("input." + ext);
    file.transferTo(inputFile);

    // 3. 작업에 입력 파일 경로 기록 (Worker가 읽을 수 있도록)
    jobService.setInputFile(principal, response.getId(), inputFile.toString());

    return ResponseEntity.accepted().body(jobService.getJob(principal, response.getId()));
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
    Path path = Path.of(artifact.getFilePath());
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
