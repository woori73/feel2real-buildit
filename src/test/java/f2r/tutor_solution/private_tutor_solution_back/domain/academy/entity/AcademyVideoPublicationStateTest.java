package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AcademyVideoPublicationStateTest {

  @Test
  void givenReadyRawAndMatchingVersion_whenPublished_thenNormalizedBecomesActiveAndRawIsPending() {
    AcademyVideo video = raw();

    assertThat(video.publishNormalized(
        0, "attempt/normalized.mp4", 50, "attempt/thumbnail.jpg",
        1_500, 720, 1280)).isTrue();
    assertThat(video.getActiveSource()).isEqualTo(VideoActiveSource.NORMALIZED);
    assertThat(video.getSourceVersion()).isEqualTo(1);
    assertThat(video.getRawCleanupStatus()).isEqualTo(VideoRawCleanupStatus.PENDING);
    assertThat(video.getRawStorageKey()).isEqualTo("staging/raw");
  }

  @Test
  void givenStaleSourceVersion_whenPublished_thenSourceDoesNotSwitch() {
    AcademyVideo video = raw();
    video.markDeleting();

    assertThat(video.publishNormalized(
        0, "attempt/normalized.mp4", 50, "attempt/thumbnail.jpg",
        1_500, 720, 1280)).isFalse();
    assertThat(video.getActiveSource()).isEqualTo(VideoActiveSource.RAW);
  }

  private static AcademyVideo raw() {
    return AcademyVideo.readyRaw(
        mock(Academy.class), "raw.mov", "staging/raw", "video/quicktime",
        10, 1_000, 720, 1280);
  }
}
