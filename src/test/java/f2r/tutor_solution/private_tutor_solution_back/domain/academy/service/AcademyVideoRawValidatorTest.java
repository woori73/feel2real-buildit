package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AcademyVideoRawValidator — exact duration and canonical raw media")
class AcademyVideoRawValidatorTest {

  @Test
  @DisplayName("given 120.000 seconds when converted then exact inclusive boundary is 120000ms")
  void duration_exact_boundary() {
    // given / when
    long duration = DefaultAcademyVideoRawValidator.durationMillis("120.000");

    // then
    assertThat(duration).isEqualTo(120_000);
  }

  @Test
  @DisplayName("given 120.001 seconds when converted then it is never rounded down")
  void duration_over_boundary_is_not_rounded_down() {
    // given / when
    long duration = DefaultAcademyVideoRawValidator.durationMillis("120.001");

    // then
    assertThat(duration).isEqualTo(120_001);
  }

  @Test
  @DisplayName("given fractional microseconds when converted then positive duration rounds upward")
  void duration_fraction_rounds_up() {
    // given / when / then
    assertThat(DefaultAcademyVideoRawValidator.durationMillis("0.0001")).isEqualTo(1);
  }

  @Test
  @DisplayName("given probed formats when canonicalized then browser MIME is ignored")
  void canonical_media_type_comes_from_probe() {
    // given / when / then
    assertThatThrownBy(() -> DefaultAcademyVideoRawValidator.canonicalMediaType(
        "matroska,webm", null, "vp9"))
        .isInstanceOf(AcademyVideoRawValidationException.class)
        .extracting("errorCode").isEqualTo("UNSUPPORTED_CONTAINER");
    assertThat(DefaultAcademyVideoRawValidator.canonicalMediaType(
        "mov,mp4,m4a,3gp,3g2,mj2", "qt  ", "hevc")).isEqualTo("video/quicktime");
    assertThat(DefaultAcademyVideoRawValidator.canonicalMediaType(
        "mov,mp4,m4a,3gp,3g2,mj2", "isom", "h264")).isEqualTo("video/mp4");
  }
}
