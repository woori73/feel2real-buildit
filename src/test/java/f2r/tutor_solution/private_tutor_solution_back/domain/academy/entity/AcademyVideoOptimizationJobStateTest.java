package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AcademyVideoOptimizationJobStateTest {

  @Test
  void givenRetryableAttemptOne_whenItFails_thenExactlyOneDelayedRetryIsAllowed() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
    AcademyVideoOptimizationJob job =
        AcademyVideoOptimizationJob.pending(rawVideo(), now);

    assertThat(job.claim("owner", "token-1", now, now.plusSeconds(90))).isTrue();
    assertThat(job.getAttemptCount()).isEqualTo(1);
    assertThat(job.retry("token-1", "PROCESS_FAILED", "safe", now)).isTrue();
    assertThat(job.getStatus()).isEqualTo(VideoOptimizationStatus.RETRY_WAIT);
    assertThat(job.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
    assertThat(job.claim("owner", "early", now.plusSeconds(59), now.plusSeconds(149))).isFalse();

    LocalDateTime retryAt = now.plusSeconds(60);
    assertThat(job.claim("owner", "token-2", retryAt, retryAt.plusSeconds(90))).isTrue();
    assertThat(job.getAttemptCount()).isEqualTo(2);
    assertThat(job.fail("token-2", "PROCESS_FAILED", "safe", retryAt)).isTrue();
    assertThat(job.getStatus()).isEqualTo(VideoOptimizationStatus.FAILED);
    assertThat(job.claim("owner", "token-3", retryAt.plusHours(1), retryAt.plusHours(2)))
        .isFalse();
    assertThat(job.getVideo().getLifecycleStatus()).isEqualTo(VideoLifecycleStatus.READY);
    assertThat(job.getVideo().getActiveSource()).isEqualTo(VideoActiveSource.RAW);
  }

  @Test
  void givenExpiredLeases_whenRecovered_thenAttemptOneWaitsAndAttemptTwoFails() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
    AcademyVideoOptimizationJob first =
        AcademyVideoOptimizationJob.pending(rawVideo(), now);
    first.claim("owner", "one", now, now.plusSeconds(90));
    first.recoverExpired(now.plusSeconds(90));
    assertThat(first.getStatus()).isEqualTo(VideoOptimizationStatus.RETRY_WAIT);
    assertThat(first.getNextAttemptAt()).isEqualTo(now.plusSeconds(150));

    first.claim("owner", "two", now.plusSeconds(150), now.plusSeconds(240));
    first.recoverExpired(now.plusSeconds(240));
    assertThat(first.getStatus()).isEqualTo(VideoOptimizationStatus.FAILED);
    assertThat(first.getAttemptCount()).isEqualTo(2);
  }

  @Test
  void givenNewLeaseToken_whenStaleOwnerActs_thenEveryMutationIsFenced() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
    AcademyVideoOptimizationJob job =
        AcademyVideoOptimizationJob.pending(rawVideo(), now);
    job.claim("owner", "current", now, now.plusSeconds(90));

    assertThat(job.retry("stale", "X", "X", now)).isFalse();
    assertThat(job.fail("stale", "X", "X", now)).isFalse();
    assertThat(job.cancel("stale", "X", now)).isFalse();
    assertThat(job.succeed("stale", now)).isFalse();
    assertThat(job.getStatus()).isEqualTo(VideoOptimizationStatus.PROCESSING);
  }

  private static AcademyVideo rawVideo() {
    return AcademyVideo.readyRaw(
        mock(Academy.class), "raw.mov", "staging/raw", "video/quicktime",
        10, 1_000, 720, 1280);
  }
}
