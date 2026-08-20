package f2r.tutor_solution.private_tutor_solution_back.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Academy video upload product limit configuration")
class AcademyVideoUploadPropertiesTest {

  @Test
  @DisplayName("given defaults when validated then canonical durable limits are wired")
  void defaults_are_canonical_product_limits() {
    // given
    AcademyVideoUploadProperties properties = new AcademyVideoUploadProperties();

    // when
    properties.validate();

    // then
    assertThat(properties.getMaxRawBytes()).isEqualTo(209_715_200L);
    assertThat(properties.getMaxDurationMs()).isEqualTo(120_000L);
    assertThat(properties.getUploadDeadlineMs()).isEqualTo(180_000L);
    assertThat(properties.getUploadPartSizeBytes()).isEqualTo(20L * 1024 * 1024);
    assertThat(properties.getUploadMaxCount()).isEqualTo(10);
    assertThat(properties.getMaxOutputBytes()).isEqualTo(209_715_200L);
    assertThat(properties.getDeliverySignedUrlTtlMs()).isEqualTo(30_000);
  }

  @Test
  @DisplayName("given an environment override that drifts a product limit when validated then startup fails")
  void product_limit_drift_is_rejected() {
    // given
    AcademyVideoUploadProperties properties = new AcademyVideoUploadProperties();
    properties.setMaxDurationMs(60_000);

    // when / then
    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not drift");
  }

  @Test
  @DisplayName("given playback signing TTL over 30 seconds when validated then startup fails")
  void delivery_ttl_over_thirty_seconds_is_rejected() {
    AcademyVideoUploadProperties properties = new AcademyVideoUploadProperties();
    properties.setDeliverySignedUrlTtlMs(30_001);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cleanup settings");
  }
}
