package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
    named = "ACADEMY_VIDEO_MIGRATION_TEST_URL",
    matches = ".+")
class AcademyVideoLegacyMigrationIntegrationTest {

  @Test
  void givenReadyAndFailedV930Rows_whenAsyncMigrationsRun_thenAvailabilityIsPreservedSafely()
      throws Exception {
    // given
    String url = System.getenv("ACADEMY_VIDEO_MIGRATION_TEST_URL");
    String username = System.getenv().getOrDefault(
        "ACADEMY_VIDEO_MIGRATION_TEST_USERNAME", "f2r_user");
    String password = System.getenv().getOrDefault(
        "ACADEMY_VIDEO_MIGRATION_TEST_PASSWORD", "f2r_password");
    Flyway throughV930 = flyway(url, username, password, "930");
    throughV930.clean();
    throughV930.migrate();

    try (var connection = DriverManager.getConnection(url, username, password);
         var academyQuery = connection.createStatement();
         var academies = academyQuery.executeQuery(
             "SELECT academy_id FROM academies ORDER BY academy_id LIMIT 1")) {
      assertThat(academies.next()).as("pre-V930 migrations provide an academy").isTrue();
      long academyId = academies.getLong(1);
      try (var insert = connection.prepareStatement("""
          INSERT INTO academy_videos (
              academy_id, original_filename, storage_key, thumbnail_key,
              duration_sec, size_bytes, width, height, status
          ) VALUES
              (?, 'ready.mov', 'normalized/ready.mp4', 'thumb/ready.jpg',
                  120, 1000, 720, 1280, 'READY'),
              (?, 'failed.mov', 'normalized/failed.mp4', 'thumb/failed.jpg',
                  60, 500, 720, 1280, 'FAILED')
          """)) {
        insert.setLong(1, academyId);
        insert.setLong(2, academyId);
        insert.executeUpdate();
      }
    }

    // when
    flyway(url, username, password, null).migrate();

    // then
    try (var connection = DriverManager.getConnection(url, username, password);
         var statement = connection.createStatement();
         var rows = statement.executeQuery("""
             SELECT v.original_filename, v.lifecycle_status, v.active_source,
                    v.raw_cleanup_status, j.status
             FROM academy_videos v
             JOIN academy_video_optimization_jobs j ON j.video_id = v.video_id
             ORDER BY v.original_filename
             """)) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString("original_filename")).isEqualTo("failed.mov");
      assertThat(rows.getString("lifecycle_status")).isEqualTo("UNAVAILABLE");
      assertThat(rows.getString("active_source")).isEqualTo("NORMALIZED");
      assertThat(rows.getString("raw_cleanup_status")).isEqualTo("DONE");
      assertThat(rows.getString("status")).isEqualTo("FAILED");

      assertThat(rows.next()).isTrue();
      assertThat(rows.getString("original_filename")).isEqualTo("ready.mov");
      assertThat(rows.getString("lifecycle_status")).isEqualTo("READY");
      assertThat(rows.getString("active_source")).isEqualTo("NORMALIZED");
      assertThat(rows.getString("raw_cleanup_status")).isEqualTo("DONE");
      assertThat(rows.getString("status")).isEqualTo("SUCCEEDED");
      assertThat(rows.next()).isFalse();
    }
  }

  private static Flyway flyway(
      String url, String username, String password, String target) {
    var configuration = Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .cleanDisabled(false);
    if (target != null) {
      configuration.target(target);
    }
    return configuration.load();
  }
}
