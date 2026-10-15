package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LessonProductCreationKeyConstraintMySqlTest {

  private static final String CONSTRAINT_NAME =
      "uk_lesson_products_academy_creation_key";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void migrationCreatesExactNamedAcademyCreationKeyConstraint() {
    // given
    String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

    // when
    List<String> constraintTypes = jdbcTemplate.queryForList("""
        SELECT CONSTRAINT_TYPE
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = ?
          AND TABLE_NAME = 'lesson_products'
          AND CONSTRAINT_NAME = ?
        """, String.class, schema, CONSTRAINT_NAME);

    List<String> columns = jdbcTemplate.queryForList("""
        SELECT COLUMN_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE CONSTRAINT_SCHEMA = ?
          AND TABLE_NAME = 'lesson_products'
          AND CONSTRAINT_NAME = ?
        ORDER BY ORDINAL_POSITION
        """, String.class, schema, CONSTRAINT_NAME);

    // then
    assertThat(constraintTypes).containsExactly("UNIQUE");
    assertThat(columns).containsExactly("academy_id", "creation_key");
  }

  @Test
  void creationKeyColumnRemainsNullableForLegacyAndUnboundRows() {
    // given
    String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

    // when
    List<String> nullableValues = jdbcTemplate.queryForList("""
        SELECT IS_NULLABLE
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = ?
          AND TABLE_NAME = 'lesson_products'
          AND COLUMN_NAME = 'creation_key'
        """, String.class, schema);

    // then
    assertThat(nullableValues).containsExactly("YES");
  }
}
