package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoRawCleanupStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoAssetCleanupRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoOptimizationJobRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoOptimizationTransactions;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoOptimizationTransactions.PublishedAssets;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.OptimizationAttemptBudget;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessDeadlineExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "app.video.optimization-poll-ms=600000",
    "app.video.optimization-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "ACADEMY_VIDEO_MIGRATION_TEST_URL", matches = ".+")
class AcademyVideoOptimizationTransactionsMySqlTest {
  private static final String RAW_KEY = "raw/g003-transaction-test";

  @Autowired AcademyRepository academyRepository;
  @Autowired AcademyVideoRepository videoRepository;
  @Autowired AcademyVideoOptimizationJobRepository jobRepository;
  @Autowired AcademyVideoAssetCleanupRepository cleanupRepository;
  @Autowired AcademyVideoOptimizationTransactions transactions;
  @Autowired JdbcTemplate jdbc;

  private Long videoId;

  @AfterEach
  void clean() {
    if (videoId == null) return;
    cleanupRepository.findAll().stream()
        .filter(cleanup -> cleanup.getVideo() != null
            && videoId.equals(cleanup.getVideo().getVideoId()))
        .forEach(cleanupRepository::delete);
    jobRepository.findByVideo_VideoId(videoId).ifPresent(jobRepository::delete);
    videoRepository.deleteById(videoId);
  }

  @Test
  void givenDueRawJob_whenClaimHeartbeatAndPublishRun_thenMySqlFencesAndCommitsBarrier() {
    var academy = academyRepository.findAll().stream().findFirst().orElseThrow();
    AcademyVideo video = videoRepository.saveAndFlush(AcademyVideo.readyRaw(
        academy, "g003.mov", RAW_KEY, "video/quicktime", 100, 2_000, 720, 1280));
    videoId = video.getVideoId();
    jobRepository.saveAndFlush(
        AcademyVideoOptimizationJob.pending(video, jobRepository.databaseNow()));

    var claim = transactions.claimNext("g003-test-owner");
    assertThat(claim).isNotNull();
    assertThat(claim.videoId()).isEqualTo(videoId);
    assertThat(claim.attempt()).isEqualTo(1);
    assertThat(transactions.heartbeat(claim.jobId(), "stale-token", false)).isFalse();
    assertThat(transactions.heartbeat(claim.jobId(), claim.leaseToken(), true)).isTrue();

    var staleClaim = new AcademyVideoOptimizationTransactions.OptimizationClaim(
        claim.jobId(), claim.videoId(), claim.attempt(), "stale-token",
        claim.sourceVersion(), claim.rawStorageKey());
    assertThat(transactions.publish(
        staleClaim, assets(staleClaim), OptimizationAttemptBudget.start())).isFalse();

    assertThat(transactions.publish(
        claim,
        new PublishedAssets(
            claim.attemptPrefix() + "/normalized.mp4", 80,
            claim.attemptPrefix() + "/thumbnail.jpg", 2_000, 720, 1280),
        OptimizationAttemptBudget.start()))
        .isTrue();

    AcademyVideo published = videoRepository.findById(videoId).orElseThrow();
    AcademyVideoOptimizationJob job =
        jobRepository.findByVideo_VideoId(videoId).orElseThrow();
    assertThat(published.getActiveSource()).isEqualTo(VideoActiveSource.NORMALIZED);
    assertThat(published.getSourceVersion()).isEqualTo(1);
    assertThat(published.getRawCleanupStatus()).isEqualTo(VideoRawCleanupStatus.PENDING);
    assertThat(job.getStatus()).isEqualTo(VideoOptimizationStatus.SUCCEEDED);
    assertThat(cleanupRepository.findAll()).anySatisfy(cleanup ->
        assertThat(cleanup.getAssetKey()).isEqualTo(RAW_KEY));
  }

  @Test
  void givenExpiredAttempts_whenProductionRecoveryRuns_thenAttemptOneWaitsAndAttemptTwoFails() {
    var first = createClaim("g003-expired");
    jdbc.update("""
        UPDATE academy_video_optimization_jobs
        SET lease_expires_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
        WHERE job_id = ?
        """, first.jobId());

    assertThat(transactions.claimNext("recovery")).isNull();
    AcademyVideoOptimizationJob retry =
        jobRepository.findById(first.jobId()).orElseThrow();
    assertThat(retry.getStatus()).isEqualTo(VideoOptimizationStatus.RETRY_WAIT);
    assertThat(retry.getAttemptCount()).isEqualTo(1);

    jdbc.update("""
        UPDATE academy_video_optimization_jobs
        SET next_attempt_at = CURRENT_TIMESTAMP(6)
        WHERE job_id = ?
        """, first.jobId());
    var second = transactions.claimNext("attempt-two");
    assertThat(second).isNotNull();
    assertThat(second.attempt()).isEqualTo(2);
    jdbc.update("""
        UPDATE academy_video_optimization_jobs
        SET lease_expires_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
        WHERE job_id = ?
        """, second.jobId());

    assertThat(transactions.claimNext("recovery")).isNull();
    AcademyVideoOptimizationJob failed =
        jobRepository.findById(first.jobId()).orElseThrow();
    AcademyVideo raw = videoRepository.findById(videoId).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(VideoOptimizationStatus.FAILED);
    assertThat(failed.getAttemptCount()).isEqualTo(2);
    assertThat(raw.getActiveSource()).isEqualTo(VideoActiveSource.RAW);
  }

  @Test
  void given599999msElapsed_whenPublicationCommits_thenSourceSwitchIsAllowed() {
    var claim = createClaim("g003-599999");
    AtomicLong now = new AtomicLong();
    OptimizationAttemptBudget budget =
        OptimizationAttemptBudget.start(Duration.ofMillis(600_000), now::get);
    now.set(Duration.ofMillis(599_999).toNanos());

    assertThat(transactions.publish(
        claim,
        assets(claim),
        budget)).isTrue();
    assertThat(videoRepository.findById(videoId).orElseThrow().getActiveSource())
        .isEqualTo(VideoActiveSource.NORMALIZED);
  }

  @Test
  void given600000msReachedBeforeCommit_whenPublicationRuns_thenTransactionRollsBack() {
    var claim = createClaim("g003-600000");
    OptimizationAttemptBudget budget = OptimizationAttemptBudget.start(
        Duration.ofMillis(600_000),
        new SequencedNanos(0, 599_999, 599_999, 600_000));

    assertThatThrownBy(() -> transactions.publish(claim, assets(claim), budget))
        .isInstanceOf(ProcessDeadlineExceededException.class);

    AcademyVideo video = videoRepository.findById(videoId).orElseThrow();
    AcademyVideoOptimizationJob job =
        jobRepository.findByVideo_VideoId(videoId).orElseThrow();
    assertThat(video.getActiveSource()).isEqualTo(VideoActiveSource.RAW);
    assertThat(video.getSourceVersion()).isZero();
    assertThat(job.getStatus()).isEqualTo(VideoOptimizationStatus.PROCESSING);
    assertThat(cleanupRepository.findAll().stream().noneMatch(cleanup ->
        cleanup.getVideo() != null && videoId.equals(cleanup.getVideo().getVideoId())))
        .isTrue();
  }

  private AcademyVideoOptimizationTransactions.OptimizationClaim createClaim(String label) {
    var academy = academyRepository.findAll().stream().findFirst().orElseThrow();
    AcademyVideo video = videoRepository.saveAndFlush(AcademyVideo.readyRaw(
        academy, label + ".mov", "raw/" + label, "video/quicktime",
        100, 2_000, 720, 1280));
    videoId = video.getVideoId();
    jobRepository.saveAndFlush(
        AcademyVideoOptimizationJob.pending(video, jobRepository.databaseNow()));
    var claim = transactions.claimNext("g003-test-owner");
    assertThat(claim).isNotNull();
    return claim;
  }

  private static PublishedAssets assets(
      AcademyVideoOptimizationTransactions.OptimizationClaim claim) {
    return new PublishedAssets(
        claim.attemptPrefix() + "/normalized.mp4", 80,
        claim.attemptPrefix() + "/thumbnail.jpg", 2_000, 720, 1280);
  }

  private static final class SequencedNanos implements java.util.function.LongSupplier {
    private final long[] millis;
    private final AtomicInteger index = new AtomicInteger();

    private SequencedNanos(long... millis) {
      this.millis = millis;
    }

    @Override
    public long getAsLong() {
      int current = Math.min(index.getAndIncrement(), millis.length - 1);
      return Duration.ofMillis(millis[current]).toNanos();
    }
  }
}
