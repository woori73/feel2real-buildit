package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Template;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingJobRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PublishingWorker 재시도 로직 테스트.
 *
 * <p>버그 이력: claimAndProvision 실패 시 즉시 FAILED로 표시되어
 * maxAttempts 재시도 로직이 동작하지 않았음 (2026-10-14 수정).
 */
@ExtendWith(MockitoExtension.class)
class PublishingWorkerRetryTest {

  @Mock
  private PublishingJobRepository jobRepository;

  @Mock
  private PublishingProperties props;

  @Mock
  private TemplateService templateService;

  private PublishingWorker worker;

  @BeforeEach
  void setUp() {
    worker = new PublishingWorker(jobRepository, props, templateService);
    when(props.getMaxAttempts()).thenReturn(3);
    when(props.getStorageRoot()).thenReturn("/tmp/publishing-test");
  }

  private PublishingJob newJob() {
    return new PublishingJob(1L, "테스트", Template.KO_TEXTBOOK_A4);
  }

  @Test
  @DisplayName("프로비저닝 성공 시 RUNNING 전환")
  void successToRunning() throws Exception {
    PublishingJob job = newJob();
    when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
    when(templateService.provision(any(), any())).thenReturn(Path.of("/tmp/x/test.designcraft"));

    // id 설정을 위해 리플렉션 사용 (엔티티 id는 JPA가 설정)
    setId(job, 1L);
    when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

    worker.claimAndProvision(1L);

    assertEquals(Status.RUNNING, job.getStatus());
    assertEquals(1, job.getAttemptCount());
  }

  @Test
  @DisplayName("첫 실패 시 QUEUED 유지 (재시도 가능)")
  void firstFailureStaysQueued() {
    PublishingJob job = newJob();
    setId(job, 1L);
    when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
    when(templateService.provision(any(), any()))
        .thenThrow(new RuntimeException("템플릿 없음"));

    worker.claimAndProvision(1L);

    // 버그 수정 전: FAILED, 수정 후: QUEUED 유지
    assertEquals(Status.QUEUED, job.getStatus());
    assertEquals(1, job.getAttemptCount());
  }

  @Test
  @DisplayName("최대 재시도 초과 시 FAILED 확정")
  void maxAttemptsToFailed() {
    PublishingJob job = newJob();
    setId(job, 1L);
    // 이미 3회 시도한 상태
    job.incrementAttempt();
    job.incrementAttempt();
    job.incrementAttempt();
    when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

    worker.claimAndProvision(1L);

    assertEquals(Status.FAILED, job.getStatus());
  }

  @Test
  @DisplayName("markJobFailed는 횟수 내면 QUEUED 유지")
  void markJobFailedRetries() {
    PublishingJob job = newJob();
    setId(job, 1L);
    when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

    worker.markJobFailed(1L, "예상치 못한 오류");

    assertEquals(Status.QUEUED, job.getStatus());
    assertEquals(1, job.getAttemptCount());
  }

  @Test
  @DisplayName("markJobFailed는 횟수 초과 시 FAILED")
  void markJobFailedExhausted() {
    PublishingJob job = newJob();
    setId(job, 1L);
    job.incrementAttempt();
    job.incrementAttempt();
    // markJobFailed에서 increment하므로 3회째에 FAILED
    when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

    worker.markJobFailed(1L, "예상치 못한 오류");

    assertEquals(Status.FAILED, job.getStatus());
    assertEquals(3, job.getAttemptCount());
  }

  private void setId(PublishingJob job, Long id) {
    try {
      var field = PublishingJob.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(job, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
