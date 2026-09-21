package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EasyBusinessCardMigrationTest {
  @Test
  void v988IsAdditiveAndContainsCardAccountingConstraints() throws Exception {
    String path = "/db/migration/V988__add_easy_business_card_accounting_foundation.sql";
    try (var input = getClass().getResourceAsStream(path)) {
      assertThat(input).as("V988 migration resource").isNotNull();
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql)
          .contains("'LIABILITY'")
          .contains("'CARD_PAYABLE'")
          .contains("CREATE TABLE easy_card_instruments")
          .contains("CREATE TABLE easy_card_events")
          .contains("CREATE TABLE easy_card_payables")
          .contains("CREATE TABLE easy_card_billing_groups")
          .contains("CREATE TABLE easy_card_settlements")
          .contains("CREATE TABLE easy_card_settlement_allocations")
          .contains("chk_easy_card_payable_balance")
          .contains("uq_easy_card_event_fingerprint")
          .contains("uq_easy_card_settlement_allocation")
          .doesNotContain("DROP TABLE")
          .doesNotContain("DROP COLUMN")
          .doesNotContain("TRUNCATE");
    }
  }
}
