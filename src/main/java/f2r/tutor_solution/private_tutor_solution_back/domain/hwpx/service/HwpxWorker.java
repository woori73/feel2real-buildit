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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

  /**
   * self-invocation 우회를 위한 프록시 주입.
   * {@code process()}가 같은 객체의 {@code @Transactional} 메서드를 직접 호출하면 프록시를 거치지 않아
   * 트랜잭션이 적용되지 않고 {@code job} 상태 변경이 flush되지 않는다(작업이 QUEUED에 머무름).
   */
  @Autowired
  @Lazy
  private HwpxWorker self;

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

  /**
   * 작업 1건 처리. 트랜잭션을 걸지 않고 단계별로 {@code self}의 짧은 트랜잭션을 호출한다:
   * 선점(QUEUED→RUNNING 커밋) → Python CLI 실행(DB 트랜잭션·커넥션 점유 없음) → 완료(DONE) 또는 실패(FAILED).
   */
  public void process(Long jobId) {
    Claimed claimed = self.claim(jobId);
    if (claimed == null) {
      return;
    }
    Path workDir = Path.of(claimed.workDir());
    try {
      Path inputPath = Path.of(claimed.inputFile());
      List<HwpxCellEdit> edits = parseEdits(claimed.editsJson());
      String outputFileName = "output.hwpx";
      CliResult result = cliExecutor.edit(workDir, inputPath.getFileName().toString(),
          edits, outputFileName);

      if (!result.ok()) {
        self.fail(jobId, "HWPX 편집 실패: " + result.error());
        log.error("HWPX 편집 실패: jobId={}, error={}", jobId, result.error());
        return;
      }

      self.progress(jobId, 90, "검증 중");
      Path outputPath = workDir.resolve(outputFileName);
      CliResult validated = cliExecutor.validate(outputPath);
      if (!validated.ok()) {
        self.fail(jobId, "HWPX 검증 실패: " + validated.error());
        return;
      }

      self.complete(jobId, outputPath.toString(), Files.size(outputPath));
      log.info("HWPX 작업 완료: jobId={}", jobId);
    } catch (Exception e) {
      self.fail(jobId, "처리 중 오류: " + e.getMessage());
      log.error("HWPX 작업 처리 중 오류: jobId={}", jobId, e);
    }
  }

  /** 선점 결과. 입력 파일이 없으면 FAILED로 기록하고 null을 돌려준다. */
  public record Claimed(String workDir, String inputFile, String editsJson) {}

  @Transactional
  public Claimed claim(Long jobId) {
    HwpxJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != Status.QUEUED) {
      return null;
    }
    Path workDir = Path.of(props.getStorageRoot(), "jobs", String.valueOf(jobId));
    try {
      Files.createDirectories(workDir);
    } catch (Exception e) {
      job.markFailed("처리 중 오류: " + e.getMessage());
      log.error("HWPX 작업 디렉터리 생성 실패: jobId={}", jobId, e);
      return null;
    }
    // 입력 파일은 Controller에서 업로드 시 workDir에 배치되고 inputFile에 기록됨
    String inputFile = job.getInputFile();
    if (inputFile == null || inputFile.isBlank()) {
      job.markFailed("입력 파일 경로가 기록되지 않음 (업로드 처리 오류)");
      log.error("HWPX 입력 파일 경로 누락: jobId={}", jobId);
      return null;
    }
    if (!Files.exists(Path.of(inputFile))) {
      job.markFailed("입력 파일을 찾을 수 없음: " + inputFile);
      log.error("HWPX 입력 파일 없음: jobId={}, path={}", jobId, inputFile);
      return null;
    }
    job.markRunning(workDir.toString(), inputFile);
    job.updateProgress(10, "Python CLI 실행 중");
    return new Claimed(workDir.toString(), inputFile, job.getEditsJson());
  }

  @Transactional
  public void progress(Long jobId, int progress, String step) {
    jobRepository.findById(jobId)
        .filter(j -> j.getStatus() == Status.RUNNING)
        .ifPresent(j -> j.updateProgress(progress, step));
  }

  @Transactional
  public void complete(Long jobId, String outputFile, long fileSize) {
    HwpxJob job = jobRepository.findById(jobId).orElse(null);
    // 실행 중 사용자가 취소했다면 결과를 반영하지 않는다.
    if (job == null || job.getStatus() != Status.RUNNING) {
      return;
    }
    artifactRepository.save(new HwpxArtifact(jobId, outputFile, fileSize));
    job.markDone(outputFile);
  }

  @Transactional
  public void fail(Long jobId, String error) {
    HwpxJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != Status.RUNNING) {
      return;
    }
    job.markFailed(error);
  }

  private List<HwpxCellEdit> parseEdits(String editsJson) throws Exception {
    Map<String, List<HwpxCellEdit>> doc = objectMapper.readValue(editsJson,
        new TypeReference<Map<String, List<HwpxCellEdit>>>() {});
    return doc.getOrDefault("edits", List.of());
  }
}
