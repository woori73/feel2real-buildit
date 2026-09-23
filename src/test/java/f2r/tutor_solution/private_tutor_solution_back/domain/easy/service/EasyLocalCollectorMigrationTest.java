package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EasyLocalCollectorMigrationTest {
  @Test
  void v997IsAdditiveAndContainsCollectorTables() throws Exception {
    String path = "/db/migration/V997__add_easy_local_collector.sql";
    try (var input = getClass().getResourceAsStream(path)) {
      assertThat(input).as("V997 migration resource").isNotNull();
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql)
          .contains("CREATE TABLE easy_local_collectors")
          .contains("CREATE TABLE easy_collector_tasks")
          .contains("token_hash")
          .contains("uq_easy_local_collector_token")
          .contains("fk_easy_collector_task_collector")
          .doesNotContain("DROP TABLE")
          .doesNotContain("DROP COLUMN")
          .doesNotContain("TRUNCATE")
          .doesNotContainIgnoringCase("password")
          .doesNotContainIgnoringCase("private_key")
          .doesNotContainIgnoringCase("certificate");
    }
  }
}
