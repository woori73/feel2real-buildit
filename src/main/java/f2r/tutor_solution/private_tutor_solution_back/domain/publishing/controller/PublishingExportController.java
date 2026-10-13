package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.ArtifactResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingArtifactRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PdfExportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service.PublishingJobService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * DesignCraft 출판 출력 API (P2: PDF/EPUB 출력·산출물·다운로드).
 *
 * <p>Qoder 명세 4.4 계약을 따른다.
 */
@RestController
@RequestMapping("/api/publishing/jobs/{jobId}")
@RequiredArgsConstructor
public class PublishingExportController {

  private final PdfExportService exportService;
  private final PublishingJobService jobService;
  private final PublishingArtifactRepository artifactRepository;

  /** PDF 출력 (게이트 통과 시에만). */
  @PostMapping("/export")
  public ResponseEntity<ArtifactResponse> exportPdf(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @RequestBody Map<String, String> body) {
    String format = body.getOrDefault("format", "pdf");
    if ("epub".equalsIgnoreCase(format)) {
      PublishingArtifact artifact = exportService.exportEpub(
          principal, jobId, body.get("title"), body.get("author"));
      return ResponseEntity.accepted().body(ArtifactResponse.from(artifact));
    }
    String standard = body.getOrDefault("standard", "x4");
    PublishingArtifact artifact = exportService.exportPdf(
        principal, jobId, standard, body.get("pages"));
    return ResponseEntity.accepted().body(ArtifactResponse.from(artifact));
  }

  /** 산출물 목록. */
  @GetMapping("/artifacts")
  public ResponseEntity<List<ArtifactResponse>> listArtifacts(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId) {
    jobService.requireOwned(principal, jobId); // 소유자 검증
    List<ArtifactResponse> list = artifactRepository.findByJobIdOrderByIdDesc(jobId)
        .stream().map(ArtifactResponse::from).toList();
    return ResponseEntity.ok(list);
  }

  /** 산출물 다운로드. */
  @GetMapping("/artifacts/{artifactId}")
  public ResponseEntity<byte[]> downloadArtifact(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long jobId,
      @PathVariable Long artifactId) {
    jobService.requireOwned(principal, jobId); // 소유자 검증
    PublishingArtifact artifact = artifactRepository.findById(artifactId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "산출물을 찾을 수 없습니다: " + artifactId));
    if (!artifact.getJobId().equals(jobId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "산출물을 찾을 수 없습니다.");
    }
    try {
      byte[] bytes = Files.readAllBytes(Path.of(artifact.getFilePath()));
      MediaType contentType = "pdf".equals(artifact.getFormat())
          ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM;
      return ResponseEntity.ok()
          .contentType(contentType)
          .header(HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"publishing-" + artifactId + "." + artifact.getFormat() + "\"")
          .body(bytes);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "산출물 파일을 읽을 수 없습니다.", e);
    }
  }
}
