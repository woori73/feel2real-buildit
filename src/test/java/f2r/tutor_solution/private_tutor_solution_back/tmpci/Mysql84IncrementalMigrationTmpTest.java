package f2r.tutor_solution.private_tutor_solution_back.tmpci;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [임시·병합 금지] MySQL 8.4 컨테이너에서 V1~V1085 전체 적용과 V1077→V1085 증분 적용을 격리 검증한다.
 * 운영 접속정보를 쓰지 않는다(CI 서비스 컨테이너의 root 계정만 사용).
 */
@DisplayName("[임시] MySQL 8.4 — V1~V1085 전체 / V1077→V1085 증분 Flyway 검증")
class Mysql84IncrementalMigrationTmpTest {

  private static final String BASE = System.getenv().getOrDefault("MYSQL84_URL", "jdbc:mysql://localhost:3306/");
  private static final String USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "root");
  private static final String PASS = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "ci_test_root_pw");
  private static final String OPTS = "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&allowPublicKeyRetrieval=true&useSSL=false";

  private static final List<String> ADMISSIONS_TABLES = List.of(
      "admissions_universities", "admissions_official_materials", "admissions_departments", "admissions_types",
      "admissions_schedules", "admissions_knowledge_items", "admissions_knowledge_embeddings",
      "admissions_conflicts", "admissions_youtube_analyses", "admissions_counseling_sessions");

  private static Connection root() throws Exception {
    return DriverManager.getConnection(BASE + OPTS, USER, PASS);
  }

  private static Flyway flyway(String schema, String target) {
    var cfg = Flyway.configure()
        .dataSource(BASE + schema + OPTS, USER, PASS)
        .locations("classpath:db/migration");
    if (target != null) {
      cfg.target(target);
    }
    return cfg.load();
  }

  private static void recreate(String schema) throws Exception {
    try (Connection c = root(); Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + schema);
      s.execute("CREATE DATABASE " + schema + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
  }

  private static String scalar(String schema, String sql) throws Exception {
    try (Connection c = DriverManager.getConnection(BASE + schema + OPTS, USER, PASS);
         Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      rs.next();
      return rs.getString(1);
    }
  }

  private static List<String> column(String schema, String sql) throws Exception {
    List<String> out = new ArrayList<>();
    try (Connection c = DriverManager.getConnection(BASE + schema + OPTS, USER, PASS);
         Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    }
    return out;
  }

  private static String fingerprint(String schema) throws Exception {
    return String.join("|", column(schema,
        "SELECT CONCAT(table_name,'.',column_name,':',column_type,':',is_nullable) FROM information_schema.columns "
            + "WHERE table_schema='" + schema + "' ORDER BY table_name, ordinal_position"));
  }

  @Test
  @DisplayName("given MySQL 8.4 when 빈 DB에 V1~V1085를 적용하면 then 전부 성공하고 V1078 테이블 10개와 TESTPREP 세트 7개가 생긴다")
  void full_migration_on_mysql84() throws Exception {
    // given
    System.out.println("MYSQL_VERSION=" + scalar("", "SELECT VERSION()"));
    recreate("f2r_full");

    // when
    var result = flyway("f2r_full", null).migrate();

    // then
    System.out.println("FULL migrationsExecuted=" + result.migrationsExecuted);
    assertThat(scalar("f2r_full", "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL")).isEqualTo("1085");
    assertThat(scalar("f2r_full", "SELECT SUM(success=0) FROM flyway_schema_history")).isEqualTo("0");
    assertAdmissionsAndTestprep("f2r_full");
  }

  @Test
  @DisplayName("given V1077까지 적용된 8.4 DB when V1078~V1085를 증분 적용하면 then 기존 TESTPREP 시드는 그대로이고 V1078 테이블·V1079~V1085 INSERT 결과가 전체 적용과 같다")
  void incremental_1077_to_1085_on_mysql84() throws Exception {
    // given: 운영과 같은 상태(V1077까지)
    recreate("f2r_inc");
    flyway("f2r_inc", "1077").migrate();
    assertThat(scalar("f2r_inc", "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL")).isEqualTo("1077");
    String examsBefore = scalar("f2r_inc", "SELECT COUNT(*) FROM tp_exams");
    String questionsBefore = scalar("f2r_inc", "SELECT COUNT(*) FROM tp_questions");
    String admissionsBefore = scalar("f2r_inc", "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='f2r_inc' AND table_name LIKE 'admissions_%'");
    System.out.println("INC before: exams=" + examsBefore + " questions=" + questionsBefore + " admissionsTables=" + admissionsBefore);
    assertThat(admissionsBefore).isEqualTo("0");

    // when
    var result = flyway("f2r_inc", null).migrate();

    // then
    System.out.println("INC migrationsExecuted=" + result.migrationsExecuted);
    assertThat(result.migrationsExecuted).isEqualTo(8);
    assertThat(scalar("f2r_inc", "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE version IS NOT NULL")).isEqualTo("1085");
    assertThat(scalar("f2r_inc", "SELECT SUM(success=0) FROM flyway_schema_history")).isEqualTo("0");
    assertThat(scalar("f2r_inc", "SELECT COUNT(*) FROM tp_exams")).isEqualTo(String.valueOf(Integer.parseInt(examsBefore) + 7));
    assertAdmissionsAndTestprep("f2r_inc");
    // 전체 적용과 스키마·건수가 같다
    assertThat(fingerprint("f2r_inc")).isEqualTo(fingerprint("f2r_full").replace("f2r_full", "f2r_inc"));
    assertThat(scalar("f2r_inc", "SELECT COUNT(*) FROM tp_questions")).isEqualTo(scalar("f2r_full", "SELECT COUNT(*) FROM tp_questions"));
  }

  private static void assertAdmissionsAndTestprep(String schema) throws Exception {
    List<String> tables = column(schema, "SELECT table_name FROM information_schema.tables WHERE table_schema='" + schema + "' AND table_name LIKE 'admissions_%'");
    System.out.println(schema + " admissions tables=" + tables.size());
    assertThat(tables).containsExactlyInAnyOrderElementsOf(ADMISSIONS_TABLES);
    // V1079~V1085: SET02~SET08 각 1개 시험 + 120문항
    for (int n = 2; n <= 8; n++) {
      String code = "TP-PRACTICE-2026-SET0" + n;
      String exams = scalar(schema, "SELECT COUNT(*) FROM tp_exams WHERE exam_code='" + code + "'");
      String qs = scalar(schema, "SELECT COUNT(*) FROM tp_questions q JOIN tp_exams e ON e.exam_id=q.exam_id WHERE e.exam_code='" + code + "'");
      System.out.println(schema + " " + code + " exams=" + exams + " questions=" + qs);
      assertThat(exams).as(code + " exams").isEqualTo("1");
      assertThat(qs).as(code + " questions").isEqualTo("120");
    }
  }
}
