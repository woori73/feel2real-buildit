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
 * <p>설계: 10초 폴링. 실제 CLI 명령 실행은 P1의 명령 실행 서비스가 담당하고,
 * Worker는 작업 생명주기(상태 전이·작업 디렉토리 격리)만 관리한다.
 *
 * <p>작업 디렉토리는 {@code {storageRoot}/jobs/{jobId}/}로 격리되며,
 * 다른 사용자의 작업에 접근할 수 없다.
 *
 * <p>수정 이력 (2026-10-14):
 * <ul>
 *   <li>self-invocation으로 @Transactional이 무시되던 버그 수정 — self 프록시 주입</li>
 *   <li>claim 실패 시 FAILED로 표시하여 무한 재시도 방지</li>
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
      return;
    }
    List<PublishingJob> queued = jobRepository.findQueued(Status.QUEUED);
    if (queued.isEmpty()) {
      return;
    }
    // 한 번에 1건씩 처리 (동시 실행 방지)
    PublishingJob job = queued.get(0);
    try {
      // self 프록시를 통해 호출하여 @Transactional 적용
      self.claimAndProvision(job.getId());
    } catch (Exception e) {
      log.error("Publishing 작업 시작 실패: jobId={}", job.getId(), e);
      // 무한 재시도 방지: 실패한 작업은 FAILED로 표시
      try {
        self.markJobFailed(job.getId(), "작업 시작 실패: " + e.getMessage());
      } catch (Exception ex) {
        log.error("FAILED 표시 실패: jobId={}", job.getId(), ex);
      }
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

  /** 실패한 작업을 FAILED로 표시 (별도 트랜잭션). */
  @Transactional
  public void markJobFailed(Long jobId, String error) {
    jobRepository.findById(jobId).ifPresent(job -> {
      if (job.getStatus() == Status.QUEUED || job.getStatus() == Status.RUNNING) {
        job.markFailed(error);
        log.info("Publishing 작업 FAILED 처리: jobId={}", jobId);
      }
    });
  }
}
