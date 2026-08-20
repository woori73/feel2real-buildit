package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademyVideoAsyncEntityContractTest {

  @Test
  void givenValidatedRawMetadata_whenVideoIsAccepted_thenItIsImmediatelyReadyAndVersioned() {
    // given
    Academy academy = academy(10L);

    // when
    AcademyVideo video = AcademyVideo.readyRaw(
        academy, "camera.mov", "raw/key", "video/quicktime",
        209_715_200L, 120_000L, 1080, 1920);

    // then
    assertThat(video.getLifecycleStatus()).isEqualTo(VideoLifecycleStatus.READY);
    assertThat(video.getActiveSource()).isEqualTo(VideoActiveSource.RAW);
    assertThat(video.getRawCleanupStatus()).isEqualTo(VideoRawCleanupStatus.NOT_REQUIRED);
    assertThat(video.getSourceVersion()).isZero();
    assertThat(video.belongsToAcademy(10L)).isTrue();
  }

  @Test
  void givenIncompleteRawMetadata_whenVideoIsAccepted_thenConstructionFailsClosed() {
    // given
    Academy academy = academy(10L);

    // when / then
    assertThatThrownBy(() -> AcademyVideo.readyRaw(
        academy, "camera.mov", "", "video/quicktime", 1L, 1L, 1, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RAW source metadata");
  }

  @Test
  void givenReadyVideo_whenDeletingTwice_thenSourceVersionIncrementsOnlyOnTransition() {
    // given
    AcademyVideo video = AcademyVideo.readyRaw(
        academy(10L), "camera.mov", "raw/key", "video/quicktime", 1L, 1L, 1, 1);

    // when
    video.markDeleting();
    video.markDeleting();

    // then
    assertThat(video.getLifecycleStatus()).isEqualTo(VideoLifecycleStatus.DELETING);
    assertThat(video.getRawCleanupStatus()).isEqualTo(VideoRawCleanupStatus.PENDING);
    assertThat(video.getSourceVersion()).isEqualTo(1);
  }

  @Test
  void givenAttemptThreeOrIncompleteProcessingLease_whenJobIsBuilt_thenItIsRejected() {
    // given
    AcademyVideo video = AcademyVideo.readyRaw(
        academy(10L), "camera.mov", "raw/key", "video/quicktime", 1L, 1L, 1, 1);

    // when / then
    assertThatThrownBy(() -> AcademyVideoOptimizationJob.builder()
        .video(video)
        .status(VideoOptimizationStatus.FAILED)
        .attemptCount(3)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 2");
    assertThatThrownBy(() -> AcademyVideoOptimizationJob.builder()
        .video(video)
        .status(VideoOptimizationStatus.PROCESSING)
        .attemptCount(1)
        .leaseOwner("worker-1")
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("complete lease");
  }

  @Test
  void givenCompletingSession_whenRecoveryFieldsArePersisted_thenFinalizationIdentityIsExplicit() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 10, 0);

    // when
    AcademyVideoUploadSession session = AcademyVideoUploadSession.builder()
        .sessionId("29c42ec1-c67b-4daf-8ace-e678bd1c2d70")
        .academy(academy(10L))
        .requestedFilename("camera.mov")
        .declaredSizeBytes(100L)
        .state(VideoUploadSessionState.COMPLETING)
        .stagingKey("academy-video/staging/session")
        .multipartUploadId("opaque-upload")
        .partSizeBytes(20L)
        .expectedPartCount(5)
        .deadlineAt(now.plusSeconds(180))
        .finalizationToken("f38e9abf-9582-43ec-8655-0508a086888f")
        .partManifestJson("[{\"partNumber\":1,\"eTag\":\"opaque\"}]")
        .manifestHash("a".repeat(64))
        .nextFinalizeAttemptAt(now.plusSeconds(10))
        .finalizeRecoveryOwner("recovery-1")
        .finalizeRecoveryLeaseExpiresAt(now.plusSeconds(30))
        .build();

    // then
    assertThat(session.getNextFinalizeAttemptAt()).isEqualTo(now.plusSeconds(10));
    assertThat(session.getFinalizeRecoveryOwner()).isEqualTo("recovery-1");
    assertThat(session.belongsToAcademy(10L)).isTrue();
  }

  @Test
  void givenTerminalSessionWithToken_whenBuilt_thenItCannotRetainFinalizationAuthority() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 10, 0);

    // when / then
    assertThatThrownBy(() -> AcademyVideoUploadSession.builder()
        .sessionId("29c42ec1-c67b-4daf-8ace-e678bd1c2d70")
        .academy(academy(10L))
        .requestedFilename("camera.mov")
        .declaredSizeBytes(100L)
        .state(VideoUploadSessionState.ABORTED)
        .stagingKey("academy-video/staging/session")
        .multipartUploadId("opaque-upload")
        .partSizeBytes(20L)
        .expectedPartCount(5)
        .deadlineAt(now.plusSeconds(180))
        .finalizationToken("f38e9abf-9582-43ec-8655-0508a086888f")
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("finalization identity");
  }

  private static Academy academy(Long academyId) {
    Academy academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(academyId);
    return academy;
  }
}
