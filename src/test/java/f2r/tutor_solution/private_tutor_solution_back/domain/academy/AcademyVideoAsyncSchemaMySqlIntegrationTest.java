package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import f2r.tutor_solution.private_tutor_solution_back.PrivateTutorSolutionBackApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(
    named = "ACADEMY_VIDEO_MIGRATION_TEST_URL",
    matches = ".+")
class AcademyVideoAsyncSchemaMySqlIntegrationTest {

  private String url;
  private String username;
  private String password;
  private long academyId;

  @BeforeAll
  void cleanMigrateCurrentSchema() throws Exception {
    // given
    url = System.getenv("ACADEMY_VIDEO_MIGRATION_TEST_URL");
    username = System.getenv().getOrDefault(
        "ACADEMY_VIDEO_MIGRATION_TEST_USERNAME", "f2r_user");
    password = System.getenv().getOrDefault(
        "ACADEMY_VIDEO_MIGRATION_TEST_PASSWORD", "f2r_password");
    Flyway flyway = Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load();

    // when
    flyway.clean();
    var result = flyway.migrate();

    // then
    assertThat(result.targetSchemaVersion).hasToString("938");
    try (Connection connection = connection();
         Statement statement = connection.createStatement();
         var rows = statement.executeQuery(
             "SELECT academy_id FROM academies ORDER BY academy_id LIMIT 1")) {
      assertThat(rows.next()).as("current migrations provide an academy fixture").isTrue();
      academyId = rows.getLong(1);
    }
  }

  @AfterAll
  void restoreCleanMigratedSchema() {
    Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure()
        .dataSource(url, username, password)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void givenSchemaMigratedThroughV938_whenJpaStartsWithValidate_thenMetadataMatchesEntities() {
    // given
    var application = new SpringApplicationBuilder(PrivateTutorSolutionBackApplication.class)
        .web(WebApplicationType.NONE);

    // when / then
    assertThatCode(() -> {
      try (var ignored = application.run(
          "--spring.datasource.url=" + url,
          "--spring.datasource.username=" + username,
          "--spring.datasource.password=" + password,
          "--spring.flyway.enabled=false",
          "--spring.jpa.hibernate.ddl-auto=validate",
          "--spring.devtools.restart.enabled=false",
          "--app.video.optimization-enabled=false")) {
        // Context creation initializes Hibernate's validate action.
      }
    }).doesNotThrowAnyException();
  }

  @Test
  void givenVideoHasOptimizationJob_whenSecondJobIsInserted_thenUniqueVideoContractRejectsIt()
      throws Exception {
    // given
    long videoId = insertReadyRawVideo("one-job");
    execute("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count, next_attempt_at)
        VALUES (?, 'PENDING', 0, CURRENT_TIMESTAMP(6))
        """, videoId);

    // when / then
    assertSqlRejected("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count, next_attempt_at)
        VALUES (?, 'PENDING', 0, CURRENT_TIMESTAMP(6))
        """, videoId);
  }

  @Test
  void givenAcceptedSessionRelationships_whenDuplicatesOrParentsDelete_thenFkContractsHold()
      throws Exception {
    // given
    long videoId = insertReadyRawVideo("accepted-session");
    insertAcceptedSession("session-" + token(), academyId, videoId);

    // when / then
    assertSqlRejected(
        acceptedSessionSql(),
        "session-" + token(), academyId, videoId);
    assertSqlRejected(
        initiatedSessionSql(),
        "session-" + token(), Long.MAX_VALUE);
    assertSqlRejected("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count, next_attempt_at)
        VALUES (?, 'PENDING', 0, CURRENT_TIMESTAMP(6))
        """, Long.MAX_VALUE);
    assertSqlRejected("""
        INSERT INTO academy_video_asset_cleanups
            (dedupe_key, session_id, asset_class, asset_key, reason, state, attempt_count)
        VALUES (?, ?, 'STAGED_OBJECT', 'staging/missing', 'TEST', 'PENDING', 0)
        """, hex64(), "missing-session-" + token());
    assertSqlRejected("DELETE FROM academy_videos WHERE video_id = ?", videoId);
    assertSqlRejected("DELETE FROM academies WHERE academy_id = ?", academyId);
  }

  @Test
  void givenLifecycleAndSourceMetadata_whenRowsAreInserted_thenChecksAcceptOnlyValidShapes()
      throws Exception {
    // given / when / then
    assertThat(insertReadyRawVideo("valid-raw")).isPositive();
    assertThat(insertNormalizedDoneVideo("valid-normalized")).isPositive();

    assertSqlRejected("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            raw_media_type, raw_size_bytes, duration_ms, source_version, raw_cleanup_status
        ) VALUES (?, 'missing-raw-key', 'READY', 'RAW',
            'video/quicktime', 100, 1000, 0, 'NOT_REQUIRED')
        """, academyId);
    assertSqlRejected("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            raw_storage_key, raw_media_type, raw_size_bytes,
            duration_ms, source_version, raw_cleanup_status
        ) VALUES (?, 'bad-ready-cleanup', 'READY', 'RAW',
            'raw/bad', 'video/quicktime', 100, 1000, 0, 'DONE')
        """, academyId);
    assertSqlRejected("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            normalized_storage_key, normalized_media_type, normalized_size_bytes,
            duration_ms, source_version, raw_cleanup_status
        ) VALUES (?, 'missing-thumbnail', 'READY', 'NORMALIZED',
            'normalized/bad.mp4', 'video/mp4', 100, 1000, 0, 'DONE')
        """, academyId);
    assertSqlRejected("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            raw_storage_key, raw_media_type, raw_size_bytes,
            duration_ms, source_version, raw_cleanup_status
        ) VALUES (?, 'bad-lifecycle', 'PROCESSING', 'RAW',
            'raw/bad-lifecycle', 'video/quicktime', 100, 1000, 0, 'NOT_REQUIRED')
        """, academyId);
  }

  @Test
  void givenOptimizationStates_whenLeaseOrAttemptIsInvalid_thenDatabaseRejectsIt()
      throws Exception {
    // given
    long pendingVideo = insertReadyRawVideo("pending-job");
    long processingVideo = insertReadyRawVideo("processing-job");
    execute("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count, next_attempt_at)
        VALUES (?, 'PENDING', 0, CURRENT_TIMESTAMP(6))
        """, pendingVideo);
    execute("""
        INSERT INTO academy_video_optimization_jobs (
            video_id, status, attempt_count, lease_owner, lease_token,
            lease_expires_at, heartbeat_at, started_at
        ) VALUES (?, 'PROCESSING', 1, 'worker-1', ?, ?, ?, ?)
        """,
        processingVideo,
        uuid(),
        LocalDateTime.now().plusSeconds(90),
        LocalDateTime.now(),
        LocalDateTime.now());

    // when / then
    assertSqlRejected("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count, lease_owner)
        VALUES (?, 'PROCESSING', 1, 'worker-incomplete')
        """, insertReadyRawVideo("incomplete-lease"));
    assertSqlRejected("""
        INSERT INTO academy_video_optimization_jobs (
            video_id, status, attempt_count, lease_owner, lease_token,
            lease_expires_at, heartbeat_at
        ) VALUES (?, 'FAILED', 1, 'stale-worker', ?, ?, ?)
        """,
        insertReadyRawVideo("stale-lease"),
        uuid(),
        LocalDateTime.now().plusSeconds(90),
        LocalDateTime.now());
    assertSqlRejected("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count)
        VALUES (?, 'FAILED', -1)
        """, insertReadyRawVideo("attempt-negative"));
    assertSqlRejected("""
        INSERT INTO academy_video_optimization_jobs
            (video_id, status, attempt_count)
        VALUES (?, 'FAILED', 3)
        """, insertReadyRawVideo("attempt-three"));
  }

  @Test
  void givenNormalizedVideo_whenRawCleanupStateChanges_thenRawMetadataRelationIsEnforced()
      throws Exception {
    // given / when
    long pendingId = insertNormalizedPendingVideo("pending-with-raw");
    long doneId = insertNormalizedDoneVideo("done-without-raw");

    // then
    assertThat(pendingId).isPositive();
    assertThat(doneId).isPositive();
    assertSqlRejected("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            normalized_storage_key, normalized_media_type, normalized_size_bytes,
            thumbnail_key, duration_ms, source_version, raw_cleanup_status
        ) VALUES (?, 'pending-cleared-too-early', 'READY', 'NORMALIZED',
            'normalized/pending-cleared.mp4', 'video/mp4', 100,
            'thumb/pending-cleared.jpg', 1000, 1, 'PENDING')
        """, academyId);
    assertSqlRejected("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            raw_storage_key, raw_media_type, raw_size_bytes,
            normalized_storage_key, normalized_media_type, normalized_size_bytes,
            thumbnail_key, duration_ms, source_version, raw_cleanup_status
        ) VALUES (?, 'done-retains-raw', 'READY', 'NORMALIZED',
            'raw/not-cleared', 'video/quicktime', 100,
            'normalized/done-retains.mp4', 'video/mp4', 100,
            'thumb/done-retains.jpg', 1000, 1, 'DONE')
        """, academyId);
  }

  @Test
  void givenTwoDueJobs_whenConcurrentTransactionsClaimWithSkipLocked_thenTheyDoNotOverlap()
      throws Exception {
    long firstJob = insertPendingJob(insertReadyRawVideo("claim-first"));
    long secondJob = insertPendingJob(insertReadyRawVideo("claim-second"));
    String claimSql = """
        SELECT job_id
        FROM academy_video_optimization_jobs
        WHERE job_id IN (?, ?) AND status = 'PENDING'
        ORDER BY job_id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """;
    try (Connection ownerOne = connection(); Connection ownerTwo = connection()) {
      ownerOne.setAutoCommit(false);
      ownerTwo.setAutoCommit(false);
      long claimedOne;
      try (var claim = ownerOne.prepareStatement(claimSql)) {
        claim.setLong(1, firstJob);
        claim.setLong(2, secondJob);
        try (var row = claim.executeQuery()) {
          assertThat(row.next()).isTrue();
          claimedOne = row.getLong(1);
        }
      }
      long claimedTwo;
      try (var claim = ownerTwo.prepareStatement(claimSql)) {
        claim.setLong(1, firstJob);
        claim.setLong(2, secondJob);
        try (var row = claim.executeQuery()) {
          assertThat(row.next()).isTrue();
          claimedTwo = row.getLong(1);
        }
      }
      assertThat(claimedTwo).isNotEqualTo(claimedOne);
      ownerOne.rollback();
      ownerTwo.rollback();
    }
  }

  @Test
  void givenProcessingLease_whenHeartbeatTokensRace_thenOnlyCurrentUnexpiredTokenExtends()
      throws Exception {
    long videoId = insertReadyRawVideo("heartbeat");
    String current = uuid();
    execute("""
        INSERT INTO academy_video_optimization_jobs (
            video_id, status, attempt_count, lease_owner, lease_token,
            lease_expires_at, heartbeat_at, started_at
        ) VALUES (?, 'PROCESSING', 1, 'owner', ?,
            TIMESTAMPADD(SECOND, 90, CURRENT_TIMESTAMP(6)),
            CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """, videoId, current);
    long jobId;
    try (Connection connection = connection();
         var query = connection.prepareStatement(
             "SELECT job_id FROM academy_video_optimization_jobs WHERE video_id = ?")) {
      query.setLong(1, videoId);
      try (var row = query.executeQuery()) {
        assertThat(row.next()).isTrue();
        jobId = row.getLong(1);
      }
    }
    String heartbeatSql = """
        UPDATE academy_video_optimization_jobs
        SET heartbeat_at = CURRENT_TIMESTAMP(6),
            lease_expires_at = TIMESTAMPADD(SECOND, 90, CURRENT_TIMESTAMP(6))
        WHERE job_id = ? AND status = 'PROCESSING' AND lease_token = ?
          AND lease_expires_at > CURRENT_TIMESTAMP(6)
        """;
    assertThat(update(heartbeatSql, jobId, uuid())).isZero();
    assertThat(update(heartbeatSql, jobId, current)).isEqualTo(1);
  }

  @Test
  void givenRawVideoAndProcessingJob_whenPublicationBarrierRuns_thenStaleTokenCannotSwitch()
      throws Exception {
    long videoId = insertReadyRawVideo("publication");
    String current = uuid();
    execute("""
        INSERT INTO academy_video_optimization_jobs (
            video_id, status, attempt_count, lease_owner, lease_token,
            lease_expires_at, heartbeat_at, started_at
        ) VALUES (?, 'PROCESSING', 1, 'owner', ?,
            TIMESTAMPADD(SECOND, 90, CURRENT_TIMESTAMP(6)),
            CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """, videoId, current);
    long jobId;
    try (Connection connection = connection();
         var query = connection.prepareStatement(
             "SELECT job_id FROM academy_video_optimization_jobs WHERE video_id = ?")) {
      query.setLong(1, videoId);
      try (var row = query.executeQuery()) {
        assertThat(row.next()).isTrue();
        jobId = row.getLong(1);
      }
    }

    assertThat(publishBarrier(videoId, jobId, uuid())).isFalse();
    assertThat(publishBarrier(videoId, jobId, current)).isTrue();
    try (Connection connection = connection();
         var query = connection.prepareStatement("""
             SELECT v.active_source, v.source_version, v.raw_cleanup_status, j.status
             FROM academy_videos v
             JOIN academy_video_optimization_jobs j ON j.video_id = v.video_id
             WHERE v.video_id = ?
             """)) {
      query.setLong(1, videoId);
      try (var row = query.executeQuery()) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString("active_source")).isEqualTo("NORMALIZED");
        assertThat(row.getLong("source_version")).isEqualTo(1);
        assertThat(row.getString("raw_cleanup_status")).isEqualTo("PENDING");
        assertThat(row.getString("status")).isEqualTo("SUCCEEDED");
      }
    }
  }

  private long insertReadyRawVideo(String label) throws Exception {
    return insertVideo("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            raw_storage_key, raw_media_type, raw_size_bytes,
            duration_ms, width, height, source_version, raw_cleanup_status
        ) VALUES (?, ?, 'READY', 'RAW', ?, 'video/quicktime', 100,
            1000, 720, 1280, 0, 'NOT_REQUIRED')
        """, label, "raw/" + label + "-" + token());
  }

  private long insertPendingJob(long videoId) throws Exception {
    try (Connection connection = connection();
         var statement = connection.prepareStatement("""
             INSERT INTO academy_video_optimization_jobs
                 (video_id, status, attempt_count, next_attempt_at)
             VALUES (?, 'PENDING', 0, CURRENT_TIMESTAMP(6))
             """, Statement.RETURN_GENERATED_KEYS)) {
      statement.setLong(1, videoId);
      statement.executeUpdate();
      try (var keys = statement.getGeneratedKeys()) {
        assertThat(keys.next()).isTrue();
        return keys.getLong(1);
      }
    }
  }

  private boolean publishBarrier(long videoId, long jobId, String token) throws Exception {
    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      int jobUpdated;
      try (var job = connection.prepareStatement("""
          UPDATE academy_video_optimization_jobs
          SET status = 'SUCCEEDED', lease_owner = NULL, lease_token = NULL,
              lease_expires_at = NULL, heartbeat_at = NULL, finished_at = CURRENT_TIMESTAMP(6)
          WHERE job_id = ? AND video_id = ? AND status = 'PROCESSING' AND lease_token = ?
          """)) {
        job.setLong(1, jobId);
        job.setLong(2, videoId);
        job.setString(3, token);
        jobUpdated = job.executeUpdate();
      }
      if (jobUpdated != 1) {
        connection.rollback();
        return false;
      }
      int videoUpdated;
      try (var video = connection.prepareStatement("""
          UPDATE academy_videos
          SET normalized_storage_key = 'attempt/normalized.mp4',
              normalized_media_type = 'video/mp4', normalized_size_bytes = 80,
              thumbnail_key = 'attempt/thumbnail.jpg', active_source = 'NORMALIZED',
              raw_cleanup_status = 'PENDING', source_version = source_version + 1
          WHERE video_id = ? AND lifecycle_status = 'READY'
            AND active_source = 'RAW' AND source_version = 0
          """)) {
        video.setLong(1, videoId);
        videoUpdated = video.executeUpdate();
      }
      if (videoUpdated != 1) {
        connection.rollback();
        return false;
      }
      connection.commit();
      return true;
    }
  }

  private long insertNormalizedPendingVideo(String label) throws Exception {
    return insertVideo("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            raw_storage_key, raw_media_type, raw_size_bytes,
            normalized_storage_key, normalized_media_type, normalized_size_bytes,
            thumbnail_key, duration_ms, width, height, source_version, raw_cleanup_status
        ) VALUES (?, ?, 'READY', 'NORMALIZED',
            ?, 'video/quicktime', 100,
            ?, 'video/mp4', 80, ?,
            1000, 720, 1280, 1, 'PENDING')
        """,
        label,
        "raw/" + label + "-" + token(),
        "normalized/" + label + "-" + token() + ".mp4",
        "thumb/" + label + "-" + token() + ".jpg");
  }

  private long insertNormalizedDoneVideo(String label) throws Exception {
    return insertVideo("""
        INSERT INTO academy_videos (
            academy_id, original_filename, lifecycle_status, active_source,
            normalized_storage_key, normalized_media_type, normalized_size_bytes,
            thumbnail_key, duration_ms, width, height, source_version, raw_cleanup_status
        ) VALUES (?, ?, 'READY', 'NORMALIZED',
            ?, 'video/mp4', 80, ?, 1000, 720, 1280, 1, 'DONE')
        """,
        label,
        "normalized/" + label + "-" + token() + ".mp4",
        "thumb/" + label + "-" + token() + ".jpg");
  }

  private long insertVideo(String sql, String label, Object... values) throws Exception {
    try (Connection connection = connection();
         var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setLong(1, academyId);
      statement.setString(2, label + "-" + token() + ".mov");
      for (int index = 0; index < values.length; index++) {
        statement.setObject(index + 3, values[index]);
      }
      statement.executeUpdate();
      try (var keys = statement.getGeneratedKeys()) {
        assertThat(keys.next()).isTrue();
        return keys.getLong(1);
      }
    }
  }

  private void insertAcceptedSession(String sessionId, long academy, long video)
      throws Exception {
    execute(acceptedSessionSql(), sessionId, academy, video);
  }

  private static String acceptedSessionSql() {
    return """
        INSERT INTO academy_video_upload_sessions (
            session_id, academy_id, requested_filename, declared_size_bytes, state,
            staging_key, multipart_upload_id, part_size_bytes, expected_part_count,
            deadline_at, accepted_video_id, completed_at
        ) VALUES (?, ?, 'accepted.mov', 100, 'ACCEPTED',
            'staging/accepted', 'upload-accepted', 20, 5,
            CURRENT_TIMESTAMP(6), ?, CURRENT_TIMESTAMP(6))
        """;
  }

  private static String initiatedSessionSql() {
    return """
        INSERT INTO academy_video_upload_sessions (
            session_id, academy_id, requested_filename, declared_size_bytes, state,
            staging_key, multipart_upload_id, part_size_bytes, expected_part_count,
            deadline_at
        ) VALUES (?, ?, 'initiated.mov', 100, 'INITIATED',
            'staging/initiated', 'upload-initiated', 20, 5,
            CURRENT_TIMESTAMP(6))
        """;
  }

  private void execute(String sql, Object... arguments) throws Exception {
    try (Connection connection = connection();
         var statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < arguments.length; index++) {
        statement.setObject(index + 1, arguments[index]);
      }
      statement.executeUpdate();
    }
  }

  private int update(String sql, Object... arguments) throws Exception {
    try (Connection connection = connection();
         var statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < arguments.length; index++) {
        statement.setObject(index + 1, arguments[index]);
      }
      return statement.executeUpdate();
    }
  }

  private void assertSqlRejected(String sql, Object... arguments) {
    assertThatThrownBy(() -> execute(sql, arguments))
        .isInstanceOf(SQLException.class);
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }

  private static String token() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private static String hex64() {
    return UUID.randomUUID().toString().replace("-", "")
        + UUID.randomUUID().toString().replace("-", "");
  }
}
