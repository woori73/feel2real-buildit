package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Template;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingJobRepository;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishing Worker: QUEUED 작업 폴링 → 템플릿 프로비저닝 → RUNNING 전환.
 *
 * <p>설계: 10초 폴링. 실제 CLI 명령 실행은 P1의 명령 실행 서비스가 담당하고,
 * Worker는 작업 생명주기(상태 전이·작업 디렉토리 격리)만 관리한다.
 *
 * <p>작업 디렉토리는 {@code {storageRoot}/jobs/{jobId}/}로 격리되며,
 * 다른 사용자의 작업에 접근할 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishingWorker {

  private final PublishingJobRepository jobRepository;
  private final PublishingProperties props;
  private final TemplateService templateService;

  /** 10초 간격 폴링 (fixedDelay, workerEnabled=false면 스킵). */
  @Scheduled(fixedDelayString = "${publishing.worker-poll-seconds:10}000")
  public void poll() {
    if (!props.isWorkerEnabled()) {
      return;
    }
    List<PublishingJob> queued = jobRepository.findQueued(Status.QUEUED);
    if (queued.isEmpty()) {
      return;
    }
    // 한 번에 1건씩 처리 (동시 실행 방지)
    PublishingJob job = queued.get(0);
    try {
      claimAndProvision(job.getId());
    } catch (Exception e) {
      log.error("Publishing 작업 시작 실패: jobId={}", job.getId(), e);
    }
  }

  /**
   * 작업을 RUNNING으로 전환하고 템플릿을 프로비저닝한다.
   *
   * <p>별도 트랜잭션으로 상태를 선점한다 (동시 Worker 대비).
   */
  @Transactional
  public void claimAndProvision(Long jobId) {
    PublishingJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != Status.QUEUED) {
      return; // 이미 다른 Worker가 선점
    }
    Path workDir = Path.of(props.getStorageRoot(), "jobs", String.valueOf(jobId));
    try {
      Template template = Template.fromCode(job.getTemplate());
      Path designcraftFile = templateService.provision(template, workDir);
      job.markRunning(workDir.toString(), designcraftFile.toString());
      job.updateProgress(5, "템플릿 준비 완료");
      log.info("Publishing 작업 시작: jobId={}, template={}", jobId, template.getCode());
    } catch (Exception e) {
      job.markFailed("템플릿 프로비저닝 실패: " + e.getMessage());
      log.error("템플릿 프로비저닝 실패: jobId={}", jobId, e);
    }
  }
}
