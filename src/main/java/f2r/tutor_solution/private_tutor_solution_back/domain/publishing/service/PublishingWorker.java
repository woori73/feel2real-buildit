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
 *
 * <p>실패 작업은 maxAttempts까지 재시도 후 FAILED로 확정한다 (무한 반복 방지).
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
      log.debug("Publishing Worker 비활성화됨 (worker-enabled=false)");
      return;
    }
    List<PublishingJob> queued = jobRepository.findQueued(Status.QUEUED);
    if (queued.isEmpty()) {
      log.trace("처리할 QUEUED 작업 없음");
      return;
    }
    log.info("QUEUED 작업 {}건 발견, 첫 번째 처리 시작", queued.size());
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
   * 최대 재시도 횟수를 초과한 작업은 FAILED로 확정한다.
   */
  @Transactional
  public void claimAndProvision(Long jobId) {
    PublishingJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != Status.QUEUED) {
      log.debug("작업 {} 스킵 (이미 처리됨 또는 상태 변경)", jobId);
      return; // 이미 다른 Worker가 선점
    }
    // 재시도 횟수 확인 (무한 반복 방지)
    if (job.getAttemptCount() >= props.getMaxAttempts()) {
      job.markFailed("최대 재시도 횟수(" + props.getMaxAttempts() + ") 초과");
      log.warn("작업 {} 최대 재시도 초과로 FAILED 확정", jobId);
      return;
    }
    job.incrementAttempt();
    log.info("작업 {} 처리 시작 (시도 {}/{})", jobId, job.getAttemptCount(), props.getMaxAttempts());
    
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
