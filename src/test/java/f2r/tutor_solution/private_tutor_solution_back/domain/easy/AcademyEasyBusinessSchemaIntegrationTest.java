package f2r.tutor_solution.private_tutor_solution_back.domain.easy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AcademyEasyBusinessSchemaIntegrationTest {

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Academy-EasyBusiness 연결표는 pair UNIQUE와 양쪽 FK를 강제한다")
  void linkTableHasForeignKeysAndUniqueConstraint() {
    Integer uniqueColumns = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'academy_easy_business_links'
          AND index_name = 'uk_academy_easy_business'
          AND non_unique = 0
        """, Integer.class);
    List<String> referencedTables = jdbcTemplate.queryForList("""
        SELECT referenced_table_name
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'academy_easy_business_links'
          AND referenced_table_name IS NOT NULL
        ORDER BY referenced_table_name
        """, String.class);

    assertThat(uniqueColumns).isEqualTo(2);
    assertThat(referencedTables).containsExactly("academies", "easy_businesses");
  }
}
