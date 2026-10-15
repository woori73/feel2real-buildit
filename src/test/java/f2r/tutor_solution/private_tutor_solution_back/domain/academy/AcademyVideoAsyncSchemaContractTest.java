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

  /**
   * Production 회귀 방지: V971 이 외부 링크 영상(active_source='EXTERNAL')을 도입했지만 V938 의
   * chk_academy_videos_active_source / chk_academy_videos_source_metadata 가 'EXTERNAL' 을 거부해
   * 실 MySQL 에서 insert 가 항상 실패했다(단위 테스트는 실 MySQL 미실행으로 놓침). V972 가 두
   * CHECK 를 EXTERNAL shape 를 허용하도록 재정의한다.
   */
  @Test
  void givenExternalLinkVideos_whenMigrationsRun_thenCheckConstraintsPermitExternalSource()
      throws Exception {
    // given
    String v971 = migration("V971__add_academy_video_external_link.sql");
    String v972 = migration("V972__allow_external_academy_video_source.sql");

    // when / then — V971: 4개 외부 컬럼
    assertThat(v971)
        .contains("ADD COLUMN source_kind")
        .contains("ADD COLUMN external_url")
        .contains("ADD COLUMN external_provider")
        .contains("ADD COLUMN external_embed_url");

    // V972: active_source CHECK 가 EXTERNAL 을 허용하고, source_metadata CHECK 에 EXTERNAL 분기가 있다.
    assertThat(v972)
        .contains("DROP CHECK chk_academy_videos_active_source")
        .contains("DROP CHECK chk_academy_videos_source_metadata")
        .contains("active_source IN ('RAW', 'NORMALIZED', 'EXTERNAL')");
    // EXTERNAL 분기 = AcademyVideo.validateSourceContract() 불변식과 일치.
    assertThat(v972)
        .contains("active_source = 'EXTERNAL'")
        .contains("external_url IS NOT NULL")
        .contains("raw_storage_key IS NULL")
        .contains("normalized_storage_key IS NULL")
        .contains("thumbnail_key IS NULL")
        .contains("raw_cleanup_status = 'NOT_REQUIRED'")
        .contains("lifecycle_status IN ('READY', 'DELETING')");
    // RAW / NORMALIZED 분기는 V938 원문 그대로 보존한다(기존 경로 무영향).
    assertThat(v972)
        .contains("active_source = 'RAW'")
        .contains("active_source = 'NORMALIZED'")
        .contains("normalized_media_type = 'video/mp4'");
  }

  private static String migration(String filename) throws Exception {
    try (var input = new ClassPathResource("db/migration/" + filename).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
