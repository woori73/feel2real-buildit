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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishing Worker: QUEUED 작업 폴링 → 템플릿 프로비저닝 → RUNNING 전환.
 *
 * <p>수정 이력 (2026-10-14):
 * <ul>
 *   <li>self-invocation으로 @Transactional이 무시되던 버그 수정 — self 프록시 주입</li>
 *   <li>claim 실패 시 재시도 횟수 내면 QUEUED 유지, 초과 시 FAILED (무한 반복 방지)</li>
 *   <li>최대 재시도 횟수 제한 및 상세 로깅</li>
 *   <li>수정 (2026-10-14): 첫 실패에 즉시 FAILED 표시하던 버그 수정 — 재시도 로직이 동작하지 않았음</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishingWorker {

  private final PublishingJobRepository jobRepository;
  private final PublishingProperties props;
  private final TemplateService templateService;

  /** self-invocation 우회를 위한 프록시 주입. */
  @Autowired
  @Lazy
  private PublishingWorker self;

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
    PublishingJob job = queued.get(0);
    try {
      self.claimAndProvision(job.getId());
    } catch (Exception e) {
      log.error("Publishing 작업 시작 실패: jobId={}", job.getId(), e);
      try {
        self.markJobFailed(job.getId(), "작업 시작 실패: " + e.getMessage());
      } catch (Exception ex) {
        log.error("FAILED 표시 실패: jobId={}", job.getId(), ex);
      }
    }
  }

  @Transactional
  public void claimAndProvision(Long jobId) {
    PublishingJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null || job.getStatus() != Status.QUEUED) {
      log.debug("작업 {} 스킵 (이미 처리됨 또는 상태 변경)", jobId);
      return;
    }
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
      // 재시도 횟수 내면 QUEUED 유지 (다음 poll에서 재시도), 초과 시 FAILED 확정
      if (job.getAttemptCount() >= props.getMaxAttempts()) {
        job.markFailed("템플릿 프로비저닝 실패 (최대 재시도 초과): " + e.getMessage());
        log.error("템플릿 프로비저닝 실패, FAILED 확정: jobId={}", jobId, e);
      } else {
        job.updateProgress(0, "재시도 대기 중");
        log.warn("템플릿 프로비저닝 실패, 재시도 예정 ({}/{}): jobId={}",
            job.getAttemptCount(), props.getMaxAttempts(), jobId);
      }
    }
  }

  /**
   * 예상치 못한 오류로 claimAndProvision이 예외를 던진 경우 호출된다.
   * 재시도 횟수 내면 QUEUED를 유지해 다음 poll에서 재시도하고,
   * 횟수 초과 시에만 FAILED로 확정한다 (무한 반복 방지).
   */
  @Transactional
  public void markJobFailed(Long jobId, String error) {
    jobRepository.findById(jobId).ifPresent(job -> {
      if (job.getStatus() != Status.QUEUED && job.getStatus() != Status.RUNNING) {
        return;
      }
      job.incrementAttempt();
      if (job.getAttemptCount() >= props.getMaxAttempts()) {
        job.markFailed(error + " (최대 재시도 초과)");
        log.warn("Publishing 작업 FAILED 확정: jobId={}, 시도={}", jobId, job.getAttemptCount());
      } else {
        log.warn("Publishing 작업 실패, 재시도 예정 ({}/{}): jobId={}",
            job.getAttemptCount(), props.getMaxAttempts(), jobId);
      }
    });
  }
}
