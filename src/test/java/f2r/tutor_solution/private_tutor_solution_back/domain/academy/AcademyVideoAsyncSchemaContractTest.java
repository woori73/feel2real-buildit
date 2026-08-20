package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyVideoAsyncSchemaContractTest {

  @Test
  void givenLegacyTable_whenMigrationsRun_thenTheyUseNullableBackfillEnforceStages()
      throws Exception {
    // given
    String add = migration("V936__add_async_academy_video_state_nullable.sql");
    String backfill = migration("V937__backfill_async_academy_video_state.sql");
    String enforce = migration("V938__enforce_async_academy_video_state.sql");

    // when
    boolean nullableBeforeBackfill =
        add.contains("lifecycle_status VARCHAR(20) NULL")
            && backfill.contains("UPDATE academy_videos")
            && enforce.contains("MODIFY COLUMN lifecycle_status VARCHAR(20) NOT NULL");

    // then
    assertThat(nullableBeforeBackfill).isTrue();
    assertThat(add).contains("MODIFY COLUMN thumbnail_key VARCHAR(500) NULL");
    assertThat(backfill)
        .contains("WHEN 'READY' THEN 'READY'")
        .contains("ELSE 'UNAVAILABLE'")
        .contains("WHEN 'READY' THEN 'SUCCEEDED' ELSE 'FAILED'");
    assertThat(enforce)
        .contains("DROP COLUMN storage_key")
        .contains("DROP COLUMN duration_sec")
        .contains("DROP COLUMN size_bytes")
        .contains("DROP COLUMN status");
  }

  @Test
  void givenFinalSchema_whenInspected_thenSourceJobLeaseAndCleanupInvariantsExist()
      throws Exception {
    // given
    String add = migration("V936__add_async_academy_video_state_nullable.sql");
    String enforce = migration("V938__enforce_async_academy_video_state.sql");

    // when / then
    assertThat(add)
        .contains("next_finalize_attempt_at DATETIME(6) NULL")
        .contains("part_manifest_json JSON NULL")
        .contains("uk_academy_video_upload_sessions_accepted_video UNIQUE (accepted_video_id)")
        .contains("CONSTRAINT uk_academy_video_optimization_jobs_video UNIQUE (video_id)")
        .contains("CHECK (attempt_count BETWEEN 0 AND 2)")
        .contains("status = 'PROCESSING'")
        .contains("lease_owner IS NOT NULL")
        .contains("lease_token IS NOT NULL")
        .contains("lease_expires_at IS NOT NULL")
        .contains("heartbeat_at IS NOT NULL")
        .contains("CONSTRAINT uk_academy_video_asset_cleanups_dedupe UNIQUE (dedupe_key)");
    assertThat(enforce)
        .contains("CHECK (source_version >= 0)")
        .contains("active_source = 'RAW'")
        .contains("raw_cleanup_status = 'NOT_REQUIRED'")
        .contains("lifecycle_status = 'DELETING'")
        .contains("raw_cleanup_status = 'PENDING'")
        .contains("active_source = 'NORMALIZED'")
        .contains("normalized_media_type = 'video/mp4'")
        .contains("raw_cleanup_status = 'PENDING'")
        .contains("raw_cleanup_status = 'DONE'")
        .contains("ON DELETE RESTRICT");
  }

  @Test
  void givenParentRelationships_whenInspected_thenOwnerIsDerivedAndDeletionIsRestricted()
      throws Exception {
    // given
    String add = migration("V936__add_async_academy_video_state_nullable.sql");
    String enforce = migration("V938__enforce_async_academy_video_state.sql");

    // when / then
    assertThat(add)
        .contains("academy_id BIGINT NOT NULL")
        .doesNotContain("owner_user_id")
        .contains("REFERENCES academies(academy_id) ON DELETE RESTRICT")
        .contains("REFERENCES academy_video_upload_sessions(session_id) ON DELETE RESTRICT")
        .contains("REFERENCES academy_video_optimization_jobs(job_id) ON DELETE RESTRICT");
    assertThat(enforce)
        .contains("REFERENCES academies(academy_id) ON DELETE RESTRICT");
  }

  private static String migration(String filename) throws Exception {
    try (var input = new ClassPathResource("db/migration/" + filename).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
