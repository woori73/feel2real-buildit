package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.config.HwpxProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.dto.HwpxCellEdit;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxArtifact;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity.HwpxJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxArtifactRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.repository.HwpxJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.service.HwpxCliExecutor.CliResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * HWPX Worker: QUEUED 작업 폴링 → Python CLI 실행 → DONE 전환.
 *
 * <p>PublishingWorker 패턴을 따른다. 10초 폴링, 한 번에 1건씩 처리.
 * 작업 디렉터리는 {@code {storageRoot}/jobs/{jobId}/}로 격리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HwpxWorker {

  private final HwpxJobRepository jobRepository;
  private final HwpxArtifactRepository artifactRepository;
  private final HwpxProperties props;
  private final HwpxCliExecutor cliExecutor;
  private final ObjectMapper objectMapper;

  /** 10초 간격 폴링. */
  @Scheduled(fixedDelayString = "${hwpx.worker-poll-seconds:10}000")
  public void poll() {
    if (!props.isWorkerEnabled()) {
      return;
    }
    List<HwpxJob> queued = jobRepository.findQueued(Status.QUEUED);
    if (queued.isEmpty()) {
      return;
    }
    HwpxJob job = queued.get(0);
    try {
      process(job.getId());
    } catch (Exception e) {
      log.error("HWPX 작업 처리 실패: jobId={}", job.getId(), e);
    }
  }

  @Transactional
  public void process(Long jobId) {
    HwpxJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != Status.QUEUED) {
      return;
    }
    Path workDir = Path.of(props.getStorageRoot(), "jobs", String.valueOf(jobId));
    try {
      Files.createDirectories(workDir);
      // 입력 파일은 Controller에서 업로드 시 workDir에 배치되고 inputFile에 기록됨
      String inputFile = job.getInputFile();
      if (inputFile == null || inputFile.isBlank()) {
        job.markFailed("입력 파일 경로가 기록되지 않음 (업로드 처리 오류)");
        log.error("HWPX 입력 파일 경로 누락: jobId={}", jobId);
        return;
      }
      Path inputPath = Path.of(inputFile);
      if (!Files.exists(inputPath)) {
        job.markFailed("입력 파일을 찾을 수 없음: " + inputFile);
        log.error("HWPX 입력 파일 없음: jobId={}, path={}", jobId, inputFile);
        return;
      }
      job.markRunning(workDir.toString(), inputFile);
      job.updateProgress(10, "Python CLI 실행 중");

      List<HwpxCellEdit> edits = parseEdits(job.getEditsJson());
      String outputFileName = "output.hwpx";
      CliResult result = cliExecutor.edit(workDir, inputPath.getFileName().toString(),
          edits, outputFileName);

      if (!result.ok()) {
        job.markFailed("HWPX 편집 실패: " + result.error());
        log.error("HWPX 편집 실패: jobId={}, error={}", jobId, result.error());
        return;
      }

      job.updateProgress(90, "검증 중");
      Path outputPath = workDir.resolve(outputFileName);
      CliResult validated = cliExecutor.validate(outputPath);
      if (!validated.ok()) {
        job.markFailed("HWPX 검증 실패: " + validated.error());
        return;
      }

      long fileSize = Files.size(outputPath);
      HwpxArtifact artifact = new HwpxArtifact(jobId, outputPath.toString(), fileSize);
      artifactRepository.save(artifact);

      job.markDone(outputPath.toString());
      log.info("HWPX 작업 완료: jobId={}", jobId);
    } catch (Exception e) {
      job.markFailed("처리 중 오류: " + e.getMessage());
      log.error("HWPX 작업 처리 중 오류: jobId={}", jobId, e);
    }
  }

  private List<HwpxCellEdit> parseEdits(String editsJson) throws Exception {
    Map<String, List<HwpxCellEdit>> doc = objectMapper.readValue(editsJson,
        new TypeReference<Map<String, List<HwpxCellEdit>>>() {});
    return doc.getOrDefault("edits", List.of());
  }
}
