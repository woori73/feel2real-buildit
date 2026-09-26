package f2r.tutor_solution.private_tutor_solution_back.domain.newstart;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V1009 스키마 계약: 본인채무 상태창은 신규 1:1 테이블만 추가하고, Entity가 요구하는
 * BaseEntity 감사 컬럼(특히 created_by/updated_by)을 DDL에 반드시 포함한다.
 * (EASY V1002/V1003에서 발생한 "새 MySQL 부트 시 validate 실패" 패턴의 재발 방지.)
 */
@DisplayName("NewStart 채무상황 V1009 마이그레이션 계약")
class NewStartMemberDebtStatusMigrationTest {

  private static final String PATH = "/db/migration/V1009__add_newstart_member_debt_status.sql";

  private String sql() throws Exception {
    try (var input = getClass().getResourceAsStream(PATH)) {
      assertThat(input).as("V1009 migration resource").isNotNull();
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void createsOneToOneTableWithSixFlagsAndAuditColumns() throws Exception {
    assertThat(sql())
        .contains("CREATE TABLE `newstart_member_debt_status`")
        .contains("`user_id` BIGINT NOT NULL")
        .contains("`credit4u_checked` TINYINT(1) NOT NULL DEFAULT 0")
        .contains("`creditor_confirmed` TINYINT(1) NOT NULL DEFAULT 0")
        .contains("`personal_loan_confirmed` TINYINT(1) NOT NULL DEFAULT 0")
        .contains("`overdue_registered_confirmed` TINYINT(1) NOT NULL DEFAULT 0")
        .contains("`debt_guarantee_confirmed` TINYINT(1) NOT NULL DEFAULT 0")
        .contains("`creditor_change_confirmed` TINYINT(1) NOT NULL DEFAULT 0")
        .contains("`debt_summary` VARCHAR(1000) NULL")
        .contains("`stage` VARCHAR(30) NULL")
        .contains("`created_at`")
        .contains("`updated_at`")
        .contains("`created_by` VARCHAR(50)")
        .contains("`updated_by` VARCHAR(50)")
        .contains("PRIMARY KEY (`user_id`)")
        .contains("REFERENCES `users` (`user_id`) ON DELETE CASCADE");
  }

  @Test
  void isSchemaOnly_andDoesNotTouchExistingRows() throws Exception {
    // ON UPDATE CURRENT_TIMESTAMP는 DDL 기본기능이라 데이터 변경 문구 검사에서 제외한다.
    String s = sql().replace("ON UPDATE CURRENT_TIMESTAMP", "");
    assertThat(s)
        .doesNotContain("INSERT INTO")
        .doesNotContain("UPDATE ")
        .doesNotContain("DELETE FROM")
        .doesNotContain("DROP TABLE")
        .doesNotContain("ALTER TABLE");
  }
}
