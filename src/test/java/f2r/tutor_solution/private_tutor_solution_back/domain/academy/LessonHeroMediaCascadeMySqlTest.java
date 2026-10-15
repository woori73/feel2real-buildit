package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비동기 영상 cleanup tombstone과 수업상품 영상 참조의 삭제 순서를 검증한다.
 *
 * <p>V938부터 academy_videos는 cleanup 완료 전 Academy/User 물리 삭제를 RESTRICT한다.
 * cleanup/job 제거 후 영상을 물리 삭제하면 lesson_products의 대표/OT 참조는 SET NULL로 풀린다.
 */
@SpringBootTest
@DisplayName("비동기 영상 cleanup FK")
class LessonHeroMediaCascadeMySqlTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long tutorId;
  private Long academyId;
  private Long videoId;
  private Long lessonId;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    jdbcTemplate.update("""
        INSERT INTO users (login_id, password, name, nickname, role)
        VALUES (?, 'pw', '캐스케이드강사', '캐스케이드강사', 'TUTOR')
        """, "cascade-" + suffix);
    tutorId = jdbcTemplate.queryForObject(
        "SELECT user_id FROM users WHERE login_id = ?", Long.class, "cascade-" + suffix);

    jdbcTemplate.update("""
        INSERT INTO academies
          (tutor_user_id, slug, name, logo_storage_key, intro, status, is_visible)
        VALUES (?, ?, 'Cascade Academy', 'logo', 'intro', 'PUBLISHED', TRUE)
        """, tutorId, "cascade-academy-" + suffix);
    academyId = jdbcTemplate.queryForObject(
        "SELECT academy_id FROM academies WHERE tutor_user_id = ?", Long.class, tutorId);

    jdbcTemplate.update("""
        INSERT INTO academy_videos
          (academy_id, original_filename, lifecycle_status, active_source,
           normalized_storage_key, normalized_media_type, normalized_size_bytes,
           thumbnail_key, duration_ms, width, height, source_version, raw_cleanup_status)
        VALUES (?, 'promo.mp4', 'READY', 'NORMALIZED',
                'mp4-key', 'video/mp4', 100, 'thumb-key',
                1000, 720, 1280, 0, 'DONE')
        """, academyId);
    videoId = jdbcTemplate.queryForObject(
        "SELECT video_id FROM academy_videos WHERE academy_id = ?", Long.class, academyId);
    jdbcTemplate.update("""
        INSERT INTO academy_video_optimization_jobs
          (video_id, status, attempt_count, finished_at)
        VALUES (?, 'SUCCEEDED', 0, CURRENT_TIMESTAMP(6))
        """, videoId);

    jdbcTemplate.update("""
        INSERT INTO lesson_products
          (academy_id, name, current_count, status, is_visible, authoring_revision,
           media_video_id, ot_video_id)
        VALUES (?, '미디어 수업', 0, 'PUBLISHED', TRUE, 1, ?, ?)
        """, academyId, videoId, videoId);
    lessonId = jdbcTemplate.queryForObject("""
        SELECT lesson_id FROM lesson_products WHERE academy_id = ? AND name = '미디어 수업'
        """, Long.class, academyId);
  }

  @AfterEach
  void tearDown() {
    if (tutorId != null) {
      if (academyId != null) {
        jdbcTemplate.update("DELETE FROM lesson_products WHERE academy_id = ?", academyId);
        jdbcTemplate.update("""
            DELETE FROM academy_video_optimization_jobs
            WHERE video_id IN (
                SELECT video_id FROM academy_videos WHERE academy_id = ?
            )
            """, academyId);
        jdbcTemplate.update("DELETE FROM academy_videos WHERE academy_id = ?", academyId);
        jdbcTemplate.update("DELETE FROM academies WHERE academy_id = ?", academyId);
      }
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", tutorId);
    }
  }

  @Test
  @DisplayName("given cleanup 전 영상 when 학원 삭제 then RESTRICT로 차단된다")
  void deletingAcademy_beforeVideoCleanup_isRestricted() {
    // given — 영상/job과 수업상품 참조가 살아 있다

    // when / then
    assertThatThrownBy(() ->
        jdbcTemplate.update("DELETE FROM academies WHERE academy_id = ?", academyId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM lesson_products WHERE lesson_id = ?", Integer.class, lessonId))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM academy_videos WHERE video_id = ?", Integer.class, videoId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("given cleanup 전 영상 when 강사 유저 삭제 then Academy 경로에서 차단된다")
  void deletingUser_beforeVideoCleanup_isRestricted() {
    // given — users -> academies cascade가 academy_videos RESTRICT에 도달한다

    // when / then
    assertThatThrownBy(() ->
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", tutorId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM academies WHERE academy_id = ?", Integer.class, academyId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("given 대표와 OT에 영상이 걸린 수업상품 when 영상 직접 삭제 then 두 참조가 NULL로 풀린다")
  void deletingVideoDirectly_setsReferenceNull() {
    // given — cleanup 완료를 나타내도록 durable job을 먼저 물리 제거한다
    jdbcTemplate.update(
        "DELETE FROM academy_video_optimization_jobs WHERE video_id = ?", videoId);

    // when
    jdbcTemplate.update("DELETE FROM academy_videos WHERE video_id = ?", videoId);

    // then
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM lesson_products WHERE lesson_id = ?", Integer.class, lessonId))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT media_video_id FROM lesson_products WHERE lesson_id = ?", Long.class, lessonId))
        .isNull();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT ot_video_id FROM lesson_products WHERE lesson_id = ?", Long.class, lessonId))
        .isNull();
  }
}
