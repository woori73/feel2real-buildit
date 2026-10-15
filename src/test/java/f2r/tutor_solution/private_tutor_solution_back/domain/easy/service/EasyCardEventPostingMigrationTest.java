package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EasyCardEventPostingMigrationTest {
  @Test
  void v990IsAdditiveAndLinksCardEventsToTransactions() throws Exception {
    String path = "/db/migration/V990__add_easy_card_event_posting.sql";
    try (var input = getClass().getResourceAsStream(path)) {
      assertThat(input).as("V990 migration resource").isNotNull();
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql)
          .contains("ALTER TABLE easy_card_events")
          .contains("posted_txn_id")
          .contains("fk_easy_card_event_posted_txn")
          .contains("uq_easy_card_event_posted_txn")
          .doesNotContain("DROP TABLE")
          .doesNotContain("DROP COLUMN")
          .doesNotContain("TRUNCATE");
    }
  }
}
