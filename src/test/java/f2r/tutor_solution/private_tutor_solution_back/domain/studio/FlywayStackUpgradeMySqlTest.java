package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Isolated MySQL only: V1093 -> V1094 -> V1095 -> V1098 and fresh schema, no repair/clean. */
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
class FlywayStackUpgradeMySqlTest {
  private static final String URL = "jdbc:mysql://127.0.0.1:" + testPort() + "/";

  private static int testPort() {
    int port = Integer.parseInt(System.getenv().getOrDefault("F2R_STUDIO_TEST_PORT", "3318"));
    if (port < 1024 || port > 65535)
      throw new IllegalArgumentException("Isolated test port required");
    return port;
  }

  private Flyway flyway(String schema, String target) {
    var config =
        Flyway.configure()
            .dataSource(URL + schema, "root", "isolated_test_root")
            .locations("classpath:db/migration");
    if (target != null) config.target(target);
    return config.load();
  }

  private Map<String, Integer> checksums(String schema) throws SQLException {
    var result = new TreeMap<String, Integer>();
    try (var connection = DriverManager.getConnection(URL + schema, "root", "isolated_test_root");
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT version,checksum FROM flyway_schema_history WHERE version IN"
                    + " ('1094','1095')")) {
      while (rows.next()) result.put(rows.getString(1), rows.getInt(2));
    }
    return result;
  }

  @Test
  void
      givenMainAndExistingFinalSchemas_whenUpgradedOrValidated_thenFullMigrationsPassAndChecksumsStayUnchanged()
          throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String upgrade = "f2r_upgrade_" + suffix, fresh = "f2r_fresh_" + suffix;
    try (var connection = DriverManager.getConnection(URL, "root", "isolated_test_root");
        var statement = connection.createStatement()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
      for (String schema : List.of(upgrade, fresh))
        statement.execute(
            "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
    var base = flyway(upgrade, "1093");
    base.migrate();
    base.validate();
    assertThat(base.info().current().getVersion().toString()).isEqualTo("1093");
    var stepA = flyway(upgrade, "1094");
    assertThat(stepA.migrate().migrationsExecuted).isEqualTo(1);
    stepA.validate();
    var stepC = flyway(upgrade, "1095");
    assertThat(stepC.migrate().migrationsExecuted).isEqualTo(1);
    stepC.validate();
    assertThat(stepC.info().current().getVersion().toString()).isEqualTo("1095");
    var collaboration = flyway(upgrade, null);
    assertThat(collaboration.migrate().migrationsExecuted).isEqualTo(3);
    collaboration.validate();
    assertThat(collaboration.info().current().getVersion().toString()).isEqualTo("1098");
    var all = flyway(fresh, null);
    assertThat(all.migrate().migrationsExecuted).isGreaterThanOrEqualTo(190);
    all.validate();
    var expected = Map.of("1094", -1031052192, "1095", -1470147673);
    assertThat(checksums(upgrade)).isEqualTo(expected);
    assertThat(checksums(fresh)).isEqualTo(expected);
    var existing = flyway("f2r_studio_test", null);
    // On CI this test may run before the Spring tests: migrate is additive and idempotent.
    existing.migrate();
    existing.validate();
    assertThat(checksums("f2r_studio_test")).isEqualTo(expected);
    assertThat(existing.migrate().migrationsExecuted).isZero();
  }
}
