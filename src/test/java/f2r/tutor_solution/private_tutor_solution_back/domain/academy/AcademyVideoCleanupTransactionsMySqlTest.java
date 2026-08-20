package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoCleanupTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "app.video.optimization-enabled=false",
    "app.video.cleanup-poll-ms=600000",
    "app.video.cleanup-orphan-min-age-ms=600000"
})
@EnabledIfEnvironmentVariable(named = "ACADEMY_VIDEO_MIGRATION_TEST_URL", matches = ".+")
@DisplayName("Academy video cleanup transactions — real MySQL barriers")
class AcademyVideoCleanupTransactionsMySqlTest {
  @Autowired AcademyRepository academyRepository;
  @Autowired AcademyVideoRepository videoRepository;
  @Autowired AcademyVideoAssetCleanupRepository cleanupRepository;
  @Autowired AcademyVideoUploadSessionRepository sessionRepository;
  @Autowired AcademyVideoCleanupTransactions transactions;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void clean() {
    jdbc.update("DELETE FROM academy_video_asset_cleanups WHERE reason LIKE 'G004_%'");
    jdbc.update("DELETE FROM academy_video_upload_sessions WHERE session_id LIKE 'g004-%'");
    jdbc.update("DELETE FROM academy_videos WHERE original_filename LIKE 'g004-%'");
  }

  @Test
  void givenDueCleanup_whenClaimExpiresAndIsReclaimed_thenLeaseTokenFencesAndBackoffPersists() {
    // given
    insertOrphan("claim", "G004_CLAIM");

    // when
    var first = transactions.claimNext("worker-one");

    // then
    assertThat(first).isNotNull();
    assertThat(first.attempt()).isEqualTo(1);
    assertThat(transactions.complete(new AcademyVideoCleanupTransactions.CleanupClaim(
        first.cleanupId(), "stale-token", first.attempt(), first.assetClass(),
        first.assetKey(), first.stagingKey(), first.videoId()))).isFalse();

    // given expired lease / when reclaimed
    jdbc.update("UPDATE academy_video_asset_cleanups SET lease_expires_at = "
        + "TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6)) WHERE cleanup_id = ?", first.cleanupId());
    var reclaimed = transactions.claimNext("worker-two");

    // then
    assertThat(reclaimed).isNotNull();
    assertThat(reclaimed.attempt()).isEqualTo(2);
    assertThat(reclaimed.leaseToken()).isNotEqualTo(first.leaseToken());
    assertThat(transactions.complete(first)).isFalse();
    assertThat(transactions.retry(reclaimed, "TRANSIENT_STORAGE")).isTrue();
    AcademyVideoAssetCleanup retried = cleanupRepository.findById(first.cleanupId()).orElseThrow();
    assertThat(retried.getState()).isEqualTo(VideoAssetCleanupState.PENDING);
    assertThat(retried.getNextAttemptAt()).isAfter(cleanupRepository.databaseNow());
    assertThat(retried.getLastErrorCode()).isEqualTo("TRANSIENT_STORAGE");
  }

  @Test
  void givenNormalizedRawCleanup_whenConfirmedCleanupCompletes_thenRawFieldsClearAtomicallyNeverEarly() {
    // given
    var academy = academyRepository.findAll().stream().findFirst().orElseThrow();
    AcademyVideo video = AcademyVideo.readyRaw(
        academy, "g004-raw.mov", "raw/g004", "video/quicktime", 100, 1_000, 720, 1280);
    assertThat(video.publishNormalized(0, "normalized/g004.mp4", 80,
        "thumbnail/g004.jpg", 1_000, 720, 1280)).isTrue();
    video = videoRepository.saveAndFlush(video);
    Long videoId = video.getVideoId();
    String dedupe = hex64("raw-cleanup");
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        cleanupRepository.insertPendingIgnoreDuplicate(
            dedupe, null, videoId, null, VideoAssetClass.RAW_VIDEO.name(),
            "raw/g004", "G004_RAW", cleanupRepository.databaseNow()));

    // then: the public pointer switched, but raw metadata cannot clear before confirmed DONE.
    AcademyVideo before = videoRepository.findById(videoId).orElseThrow();
    assertThat(before.getActiveSource()).isEqualTo(VideoActiveSource.NORMALIZED);
    assertThat(before.getRawStorageKey()).isEqualTo("raw/g004");
    assertThat(before.getRawCleanupStatus()).isEqualTo(VideoRawCleanupStatus.PENDING);

    // when
    var claim = transactions.claimNext("raw-cleaner");
    assertThat(transactions.complete(claim)).isTrue();

    // then
    AcademyVideo after = videoRepository.findById(videoId).orElseThrow();
    assertThat(after.getNormalizedStorageKey()).isEqualTo("normalized/g004.mp4");
    assertThat(after.getRawStorageKey()).isNull();
    assertThat(after.getRawMediaType()).isNull();
    assertThat(after.getRawSizeBytes()).isNull();
    assertThat(after.getRawCleanupStatus()).isEqualTo(VideoRawCleanupStatus.DONE);
    assertThat(cleanupRepository.findById(claim.cleanupId()).orElseThrow().getState())
        .isEqualTo(VideoAssetCleanupState.DONE);
  }

  @Test
  void givenConcurrentDuplicateEnqueue_whenBothTransactionsCrossBarrier_thenExactlyOneRowExists() throws Exception {
    // given
    String dedupe = hex64("dedupe-race");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var task = (java.util.concurrent.Callable<Integer>) () -> {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return new TransactionTemplate(transactionManager).execute(status ->
            cleanupRepository.insertPendingIgnoreDuplicate(
                dedupe, null, null, null, VideoAssetClass.ATTEMPT_OUTPUT.name(),
                "academy-video/attempts/g004", "G004_DEDUPE", LocalDateTime.now()));
      };
      var first = executor.submit(task);
      var second = executor.submit(task);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

      // when
      start.countDown();

      // then
      assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
      assertThat(cleanupRepository.findByDedupeKey(dedupe)).isPresent();
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM academy_video_asset_cleanups WHERE dedupe_key = ?",
          Integer.class, dedupe)).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void givenUploadInitiationOwnsAcademyLock_whenParentDeleteChecks_thenItWaitsAndSeesSession() throws Exception {
    // given
    long academyId = academyRepository.findAll().stream().findFirst().orElseThrow().getAcademyId();
    long tutorId = jdbc.queryForObject(
        "SELECT tutor_user_id FROM academies WHERE academy_id = ?", Long.class, academyId);
    String sessionId = "g004-upload-first";
    CountDownLatch uploadLocked = new CountDownLatch(1);
    CountDownLatch releaseUpload = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var upload = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
        academyRepository.findForUpdateByAcademyId(academyId).orElseThrow();
        uploadLocked.countDown();
        awaitUnchecked(releaseUpload);
        sessionRepository.insertInitiatedUsingDatabaseClock(
            sessionId, academyId, "g004.mov", 100, "staging/g004", "upload-g004",
            20L * 1024 * 1024, 1, 180_000_000L);
        return true;
      }));
      assertThat(uploadLocked.await(5, TimeUnit.SECONDS)).isTrue();
      var deleteGuard = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
        academyRepository.findForUpdateByTutorUserId(tutorId).orElseThrow();
        return sessionRepository.existsByAcademy_AcademyId(academyId);
      }));

      // when / then: parent delete cannot pass its guard while initiation owns the shared row lock.
      Thread.sleep(150);
      assertThat(deleteGuard.isDone()).isFalse();
      releaseUpload.countDown();
      assertThat(upload.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(deleteGuard.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseUpload.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void givenParentDeleteOwnsTutorScopedLock_whenUploadInitiates_thenUploadWaitsUntilGuardTransactionEnds() throws Exception {
    // given
    long academyId = academyRepository.findAll().stream().findFirst().orElseThrow().getAcademyId();
    long tutorId = jdbc.queryForObject(
        "SELECT tutor_user_id FROM academies WHERE academy_id = ?", Long.class, academyId);
    CountDownLatch deleteLocked = new CountDownLatch(1);
    CountDownLatch releaseDelete = new CountDownLatch(1);
    CountDownLatch uploadLocked = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var parent = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
        academyRepository.findForUpdateByTutorUserId(tutorId).orElseThrow();
        deleteLocked.countDown();
        awaitUnchecked(releaseDelete);
        return true;
      }));
      assertThat(deleteLocked.await(5, TimeUnit.SECONDS)).isTrue();
      var upload = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
        academyRepository.findForUpdateByAcademyId(academyId).orElseThrow();
        uploadLocked.countDown();
        return true;
      }));

      // when / then
      assertThat(uploadLocked.await(150, TimeUnit.MILLISECONDS)).isFalse();
      releaseDelete.countDown();
      assertThat(parent.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(upload.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(uploadLocked.getCount()).isZero();
    } finally {
      releaseDelete.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void givenGenericCleanup_whenLeaseCompletes_thenDoneIsAtomicAndFenced() {
    // given
    insertOrphan("generic", "G004_GENERIC");
    var claim = transactions.claimNext("generic-cleaner");

    // when
    assertThat(transactions.complete(claim)).isTrue();

    // then
    AcademyVideoAssetCleanup row = cleanupRepository.findById(claim.cleanupId()).orElseThrow();
    assertThat(row.getState()).isEqualTo(VideoAssetCleanupState.DONE);
    assertThat(row.getLeaseOwner()).isNull();
    assertThat(row.getLeaseToken()).isNull();
    assertThat(transactions.complete(claim)).isFalse();
  }

  private static void awaitUnchecked(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("barrier timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("barrier interrupted", e);
    }
  }

  private void insertOrphan(String key, String reason) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        cleanupRepository.insertPendingIgnoreDuplicate(
            hex64(key), null, null, null, VideoAssetClass.ATTEMPT_OUTPUT.name(),
            "academy-video/attempts/" + key, reason, cleanupRepository.databaseNow()));
  }

  private static String hex64(String value) {
    byte[] bytes = new byte[32];
    byte[] source = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    System.arraycopy(source, 0, bytes, 0, Math.min(source.length, bytes.length));
    return HexFormat.of().formatHex(bytes);
  }
}
