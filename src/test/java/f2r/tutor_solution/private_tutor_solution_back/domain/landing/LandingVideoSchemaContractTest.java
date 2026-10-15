package f2r.tutor_solution.private_tutor_solution_back.domain.landing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LandingVideoSchemaContractTest {

  @Test
  void migrationDefinesBaseEntityColumnsAndSeedsExactlyThreeSlots() throws Exception {
    // given
    String sql;
    try (var input = new ClassPathResource(
        "db/migration/V933__add_landing_video_slots.sql").getInputStream()) {
      sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    // then
    assertThat(sql)
        .contains("slot VARCHAR(40) PRIMARY KEY")
        .contains("created_at DATETIME(6) NOT NULL")
        .contains("updated_at DATETIME(6) NOT NULL")
        .contains("created_by VARCHAR(50) NULL")
        .contains("updated_by VARCHAR(50) NULL")
        .contains("'HERO_BACKGROUND'")
        .contains("'ACADEMY_SHORTS_DEMO'")
        .contains("'PARENT_REPORT_DEMO'");
  }

  @Test
  void applicationYamlBindsExactLandingVideoDefaults() {
    // given
    YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
    factory.setResources(new ClassPathResource("application.yaml"));

    // when
    var properties = factory.getObject();

    // then
    assertThat(properties).isNotNull();
    assertThat(properties.getProperty("app.landing-video.max-duration-sec")).isEqualTo("60");
    assertThat(properties.getProperty("app.landing-video.max-upload-bytes")).isEqualTo("104857600");
    assertThat(properties.getProperty("app.landing-video.max-output-bytes")).isEqualTo("31457280");
    assertThat(properties.getProperty("app.landing-video.max-thumbnail-bytes")).isEqualTo("2097152");
    assertThat(properties.getProperty("app.landing-video.serve-permits")).isEqualTo("3");
  }
}
