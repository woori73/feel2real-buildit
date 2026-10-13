package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 운영 Flyway 이력 사고(2026-10-12) 재현 테스트.
 *
 * <p>운영 DB에는 version 1053이 <b>다른 파일</b>(V1053__academy_tenant_features_eduholic.sql)로 이미 기록되어 있었다.
 * 그 상태에서 P1의 V1053(academy_page_versions 등 생성)은 실행되지 않고(repair가 이력을 현재 파일에 맞춰 버림),
 * V1055의 ALTER가 "Table 'academy_page_versions' doesn't exist"로 실패해 배포가 롤백됐다.
 * 이 테스트는 그 이력 상태를 가짜 행으로 만들고 운영과 같은 순서(repair → migrate, 단계적 target)로 끝까지
 * 마이그레이션이 성공하는지 확인한다. 격리 스키마를 만들고 지운다.
 */
@DisplayName("Flyway: 운영 이력(V1053 슬롯이 다른 파일)에서도 끝까지 마이그레이션된다")
class FlywayProdHistoryMySqlTest {

  private static String env(String key, String fallback) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? fallback : v;
  }

  private static final String USER = env("SPRING_DATASOURCE_USERNAME", "root");
  private static final String PASSWORD = env("SPRING_DATASOURCE_PASSWORD", "ci_test_root_pw");
  private static final String PARAMS =
      "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci";

  private static Flyway flyway(String schema, String target) {
    var cfg = Flyway.configure()
        .dataSource("jdbc:mysql://localhost:3306/" + schema + PARAMS, USER, PASSWORD)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .validateOnMigrate(true);
    if (target != null) {
      cfg.target(target);
    }
    return cfg.load();
  }

  @Test
  @DisplayName("given 1053 슬롯이 다른 파일로 기록된 이력 when repair+migrate then V1058까지 성공하고 P1·편집기 테이블이 모두 있다")
  void migratesThroughProdLikeHistory() throws Exception {
    String schema = "f2r_fw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    try (Connection root = DriverManager.getConnection("jdbc:mysql://localhost:3306/" + PARAMS, USER, PASSWORD);
        Statement st = root.createStatement()) {
      st.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      try {
        // 1) 운영에 있던 상태: 1052까지
        flyway(schema, "1052").migrate();
        // 2) 운영의 1053은 다른 파일(체크섬도 다름)
        st.execute("INSERT INTO " + schema + ".flyway_schema_history (installed_rank, version, description, type, "
            + "script, checksum, installed_by, installed_on, execution_time, success) "
            + "SELECT MAX(installed_rank) + 1, '1053', 'academy tenant features eduholic', 'SQL', "
            + "'V1053__academy_tenant_features_eduholic.sql', 123456, 'root', NOW(), 5, 1 "
            + "FROM " + schema + ".flyway_schema_history");
        // 3) 첫 배포 시도 재현: FlywayRepairConfig(repair → migrate)가 1054까지 적용된 상태
        Flyway first = flyway(schema, "1054");
        first.repair();
        first.migrate();
        // 4) 수정된 배포: 끝까지
        Flyway full = flyway(schema, null);
        full.repair();
        full.migrate();

        try (ResultSet rs = st.executeQuery("SELECT version, success FROM " + schema
            + ".flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
          assertThat(rs.next()).isTrue();
          assertThat(Integer.parseInt(rs.getString("version"))).isGreaterThanOrEqualTo(1058);
          assertThat(rs.getInt("success")).isEqualTo(1);
        }
        for (String table : new String[] {"academy_page_versions", "academy_ai_sessions", "academy_ai_actions",
            "academy_ai_idempotency", "academy_visual_editor_draft", "academy_visual_editor_published",
            "academy_visual_editor_proposal", "academy_visual_editor_agent_token"}) {
          try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
              + "WHERE table_schema = '" + schema + "' AND table_name = '" + table + "'")) {
            rs.next();
            assertThat(rs.getInt(1)).as(table).isEqualTo(1);
          }
        }
      } finally {
        st.execute("DROP DATABASE IF EXISTS " + schema);
      }
    }
  }

  @Test
  @DisplayName("given 빈 DB when migrate then 처음부터 끝까지 성공한다(V1055의 보강 CREATE가 V1053과 충돌하지 않음)")
  void migratesFromScratch() throws Exception {
    String schema = "f2r_fw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    try (Connection root = DriverManager.getConnection("jdbc:mysql://localhost:3306/" + PARAMS, USER, PASSWORD);
        Statement st = root.createStatement()) {
      st.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      try {
        flyway(schema, null).migrate();
        try (ResultSet rs = st.executeQuery("SELECT version FROM " + schema
            + ".flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
          assertThat(rs.next()).isTrue();
          assertThat(Integer.parseInt(rs.getString(1))).isGreaterThanOrEqualTo(1058);
        }
      } finally {
        st.execute("DROP DATABASE IF EXISTS " + schema);
      }
    }
  }
}
