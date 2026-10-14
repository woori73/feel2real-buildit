package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingArtifactRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * PDF/EPUB 출력 서비스 (P2).
 *
 * <p>출력 전 preflight 게이트를 통과해야 한다. X-4는 CMYK 인쇄용 기본값이며,
 * A-2b는 RGB 아카이빙 전용으로 문서화한다 (실측).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

  private final PublishingJobService jobService;
  private final PublishingEditService editService;
  private final DesignCraftCliExecutor cliExecutor;
  private final PublishingGateService gateService;
  private final PublishingArtifactRepository artifactRepository;
  private final ObjectMapper objectMapper;

  /** 지원하는 출력 표준. */
  public enum PdfStandard {
    X4("x4"), A2B("a2b"), NONE("none");

    private final String code;

    PdfStandard(String code) {
      this.code = code;
    }

    public static PdfStandard fromCode(String code) {
      for (PdfStandard s : values()) {
        if (s.code.equalsIgnoreCase(code)) {
          return s;
        }
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "지원하지 않는 표준입니다: " + code + " (x4|a2b|none)");
    }
  }

  /**
   * PDF를 출력하고 산출물로 등록한다.
   *
   * <p>게이트: preflight 통과 → export → X-4 게이트 → artifact 등록.
   * 게이트 실패 시 export를 수행하지 않는다.
   */
  @Transactional
  public PublishingArtifact exportPdf(AuthenticatedPrincipal principal, Long jobId,
      String standardCode, String pages) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireExportable(job);

    PdfStandard standard = PdfStandard.fromCode(standardCode);

    // 1. preflight 게이트
    PreflightResponse preflight = editService.runPreflight(principal, jobId);
    PublishingGateService.GateResult preflightGate =
        gateService.checkPreflight(preflight, 150);
    if (!preflightGate.passed()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
          "검수 게이트 실패: " + String.join("; ", preflightGate.failures()));
    }

    // 2. export 실행
    Path workDir = Path.of(job.getWorkDir());
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path outPdf = workDir.resolve("export-" + standard.code + "-" + timestamp + ".pdf");

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("path", outPdf.toAbsolutePath().toString());
    params.put("standard", standard.code);
    params.put("marks", Map.of("crop", true, "bleed", true, "pageInfo", true));
    params.put("tagged", true);
    if (pages != null && !pages.isBlank()) {
      params.put("pages", pages);
    }
    cliExecutor.execute(new PublishingCommand("file.exportPdf", params), workDir);
    log.info("PDF 출력 완료: jobId={}, standard={}, file={}", jobId, standard.code, outPdf);

    // 3. 출력 게이트 (X-4만 엄격 검사)
    if (standard == PdfStandard.X4) {
      PublishingGateService.GateResult pdfGate =
          gateService.checkPdfX4(outPdf, null);
      if (!pdfGate.passed()) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "PDF/X-4 게이트 실패: " + String.join("; ", pdfGate.failures()));
      }
    } else if (standard == PdfStandard.A2B) {
      gateService.checkPdfA2b(outPdf); // 경고 로그만
    }

    // 4. 산출물 등록
    try {
      long size = Files.size(outPdf);
      PublishingArtifact artifact = new PublishingArtifact(
          jobId, "pdf", standard.code, outPdf.toString(), size, null);
      PublishingArtifact saved = artifactRepository.save(artifact);
      // PDF 출력 성공 시 작업을 DONE으로 전환
      job.markDone();
      log.info("Publishing 작업 완료: jobId={}", jobId);
      return saved;
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "산출물 등록 실패: " + e.getMessage(), e);
    }
  }

  /**
   * EPUB을 출력하고 산출물로 등록한다.
   */
  @Transactional
  public PublishingArtifact exportEpub(AuthenticatedPrincipal principal, Long jobId,
      String title, String author) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireExportable(job);

    Path workDir = Path.of(job.getWorkDir());
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path outEpub = workDir.resolve("export-epub-" + timestamp + ".epub");

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("path", outEpub.toAbsolutePath().toString());
    params.put("title", title != null ? title : job.getTitle());
    params.put("language", "ko");
    if (author != null && !author.isBlank()) {
      params.put("author", author);
    }
    cliExecutor.execute(new PublishingCommand("file.exportEpub", params), workDir);
    log.info("EPUB 출력 완료: jobId={}, file={}", jobId, outEpub);

    try {
      long size = Files.size(outEpub);
      PublishingArtifact artifact = new PublishingArtifact(
          jobId, "epub", null, outEpub.toString(), size, null);
      return artifactRepository.save(artifact);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "산출물 등록 실패: " + e.getMessage(), e);
    }
  }

  private void requireExportable(PublishingJob job) {
    if (job.getStatus() != Status.RUNNING && job.getStatus() != Status.DONE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "작업이 준비되지 않았습니다: " + job.getStatus());
    }
    if (job.getWorkDir() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "작업 디렉토리가 준비되지 않았습니다.");
    }
  }
}
