package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EasyCollectionLifecycleMigrationTest {

  @Test
  void v986IsAdditiveAndContainsLifecycleCheckpointConstraints() throws Exception {
    String path = "/db/migration/V986__add_easy_collection_lifecycle_and_checkpoints.sql";
    try (var input = getClass().getResourceAsStream(path)) {
      assertThat(input).as("V986 migration resource").isNotNull();
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql)
          .contains("CREATE TABLE easy_collection_economic_sales")
          .contains("CREATE TABLE easy_collection_events")
          .contains("CREATE TABLE easy_collection_checkpoints")
          .contains("UNIQUE KEY uq_collection_event_key")
          .contains("UNIQUE KEY uq_collection_checkpoint")
          .doesNotContain("DROP TABLE")
          .doesNotContain("DROP COLUMN")
          .doesNotContain("TRUNCATE");
    }
  }
}
