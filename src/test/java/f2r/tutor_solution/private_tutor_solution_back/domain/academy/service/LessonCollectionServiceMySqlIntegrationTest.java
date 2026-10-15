package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionItemRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionException;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class LessonCollectionServiceMySqlIntegrationTest {

  private static final String ROLLBACK_CHECK =
      "chk_test_lesson_collection_rollback";

  @Autowired
  private LessonCollectionService service;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository
      lessonProductRepository;

  private Long tutorId;
  private Long academyId;
  private Long lessonId;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    jdbcTemplate.update("""
        INSERT INTO users (login_id, password, name, nickname, role)
        VALUES (?, 'pw', '통합강사', '통합강사', 'TUTOR')
        """, "g003-" + suffix);
    tutorId = jdbcTemplate.queryForObject(
        "SELECT user_id FROM users WHERE login_id = ?",
        Long.class,
        "g003-" + suffix);
    jdbcTemplate.update("""
        INSERT INTO academies
          (tutor_user_id, slug, name, logo_storage_key, intro, status, is_visible)
        VALUES (?, ?, 'G003 Academy', 'logo', 'intro', 'PUBLISHED', TRUE)
        """, tutorId, "g003-academy-" + suffix);
    academyId = jdbcTemplate.queryForObject(
        "SELECT academy_id FROM academies WHERE tutor_user_id = ?",
        Long.class,
        tutorId);
    jdbcTemplate.update("""
        INSERT INTO lesson_products
          (academy_id, name, description, lesson_type, target_group,
           price_unit, price_summary, max_capacity, current_count,
           schedule_type, schedule_summary, status, is_visible, authoring_revision)
        VALUES (?, '기존 수업', '설명', 'SUBSCRIPTION', 'HIGH',
                'NEGOTIABLE', '협의', 8, 3, 'CONSULTATION',
                '일정 협의', 'DRAFT', FALSE, 0)
        """, academyId);
    lessonId = jdbcTemplate.queryForObject("""
        SELECT lesson_id FROM lesson_products
        WHERE academy_id = ? AND name = '기존 수업'
        """, Long.class, academyId);
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutorId,
        "g003-" + suffix,
        Role.TUTOR.name());
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    dropRollbackCheckIfPresent();
    SecurityContextHolder.clearContext();
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
    if (tutorId != null) {
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", tutorId);
    }
  }

  @Test
  void saveCommitsUpdateAndCreateAtomically_withoutWritingCountOrVisibility(
      CapturedOutput output) {
    // given
    LessonCollectionSaveRequest request = new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "수정 수업"),
        newItem(UUID.randomUUID().toString(), "신규 수업")));

    // when
    LessonCollectionSaveResponse response = service.save(principal, request);

    // then
    assertThat(response.requestedCount()).isEqualTo(2);
    assertThat(response.createdCount()).isEqualTo(1);
    assertThat(response.updatedCount()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT current_count FROM lesson_products WHERE lesson_id = ?
        """, Integer.class, lessonId)).isEqualTo(3);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT is_visible FROM lesson_products WHERE lesson_id = ?
        """, Boolean.class, lessonId)).isFalse();
    assertThat(jdbcTemplate.queryForObject("""
        SELECT authoring_revision FROM lesson_products WHERE lesson_id = ?
        """, Long.class, lessonId)).isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM lesson_products WHERE academy_id = ?
        """, Integer.class, academyId)).isEqualTo(2);
    assertThat(output)
        .contains("code=LESSON_COLLECTION_SAVE_COMMITTED")
        .contains("requested=2 created=1 updated=1 replayed=0")
        .doesNotContain("수정 수업")
        .doesNotContain("신규 수업");
  }

  @Test
  void staleEquivalentReplays_butStaleDifferingStateChangesNothing() {
    // given
    service.save(principal, new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "서버 수업"))));

    // when
    LessonCollectionSaveResponse replay = service.save(
        principal,
        new LessonCollectionSaveRequest(List.of(
            existingItem(lessonId, 0L, "서버 수업"))));

    // then
    assertThat(replay.replayedCount()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT authoring_revision FROM lesson_products WHERE lesson_id = ?
        """, Long.class, lessonId)).isEqualTo(1L);

    assertThatThrownBy(() -> service.save(
        principal,
        new LessonCollectionSaveRequest(List.of(
            existingItem(lessonId, 0L, "충돌 수업")))))
        .isInstanceOf(LessonCollectionException.class)
        .extracting("code")
        .isEqualTo(LessonCollectionException.AUTHORING_STALE);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT name FROM lesson_products WHERE lesson_id = ?
        """, String.class, lessonId)).isEqualTo("서버 수업");
  }

  @Test
  void finalInsertFailureRollsBackEarlierUpdateAndCreate(CapturedOutput output) {
    // given
    jdbcTemplate.execute("""
        ALTER TABLE lesson_products
        ADD CONSTRAINT chk_test_lesson_collection_rollback
        CHECK (name <> 'ROLLBACK_SENTINEL')
        """);
    LessonCollectionSaveRequest request = new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "롤백될 수정"),
        newItem("11111111-1111-4111-8111-111111111111", "롤백될 생성"),
        newItem("ffffffff-ffff-4fff-8fff-ffffffffffff", "ROLLBACK_SENTINEL")));

    // when / then
    assertThatThrownBy(() -> service.save(principal, request))
        .isInstanceOf(RuntimeException.class);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT name FROM lesson_products WHERE lesson_id = ?
        """, String.class, lessonId)).isEqualTo("기존 수업");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT authoring_revision FROM lesson_products WHERE lesson_id = ?
        """, Long.class, lessonId)).isZero();
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM lesson_products WHERE academy_id = ?
        """, Integer.class, academyId)).isEqualTo(1);
    assertThat(output).doesNotContain("code=LESSON_COLLECTION_SAVE_COMMITTED");
  }

  private LessonCollectionItemRequest existingItem(
      Long id,
      Long expectedRevision,
      String name) {
    return item(id, null, expectedRevision, name);
  }

  private void dropRollbackCheckIfPresent() {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'lesson_products'
          AND CONSTRAINT_NAME = ?
        """, Integer.class, ROLLBACK_CHECK);
    if (count != null && count > 0) {
      jdbcTemplate.execute("ALTER TABLE lesson_products DROP CHECK " + ROLLBACK_CHECK);
    }
  }

  @Test
  void save_createsLesson_whenOnlyReferenceHtmlFieldsArePresent() {
    // given — 편집기가 예시 HTML 기준 필드만 보내고 가격·정원·일정·분류는 전부 비운다.
    String creationKey = UUID.randomUUID().toString();
    LessonCollectionItemRequest bare = new LessonCollectionItemRequest(
        null, creationKey, null,
        "예시 기준 수업",
        "상품 설명",
        null,  // schoolName
        null,  // gradeLabel
        null,  // lessonType
        null,  // targetGroup
        null,  // priceAmount
        null,  // priceUnit
        null,  // priceSummary
        null,  // maxCapacity
        null,  // scheduleType
        null,  // weeklyCount
        Set.of(),
        null, null, null, null,
        null,  // scheduleSummary
        null,  // status
        "안내 문구", "짧은 이름", "키커", "강조",
        "학습자 요구", "핵심 제목", "설명1", "설명2", "설명3",
        LessonTheme.NAVY,
        null,
        null,
        null);

    // when
    LessonCollectionSaveResponse response =
        service.save(principal, new LessonCollectionSaveRequest(List.of(bare)));

    // then
    assertThat(response.createdCount()).isEqualTo(1);
    Long savedId = response.savedItems().get(0).lessonId();

    Integer nonNullOperationalColumns = jdbcTemplate.queryForObject(
        """
        select (lesson_type is not null) + (price_unit is not null)
             + (price_summary is not null) + (max_capacity is not null)
             + (schedule_type is not null) + (schedule_summary is not null)
             + (target_group is not null)
        from lesson_products where lesson_id = ?
        """,
        Integer.class, savedId);
    assertThat(nonNullOperationalColumns).isZero();

    String status = jdbcTemplate.queryForObject(
        "select status from lesson_products where lesson_id = ?", String.class, savedId);
    assertThat(status).isEqualTo("PUBLISHED");
  }

  @Test
  void save_keepsNullCapacityUnlimited_whenLessonHasNoCapacity() {
    // given
    String creationKey = UUID.randomUUID().toString();
    LessonCollectionItemRequest bare = new LessonCollectionItemRequest(
        null, creationKey, null, "정원 없는 수업", null,
        null, null,  // schoolName, gradeLabel
        null, null, null, null, null, null, null, null, Set.of(),
        null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null,
        LessonTheme.NAVY,
        null,
        null,
        null);
    LessonCollectionSaveResponse response =
        service.save(principal, new LessonCollectionSaveRequest(List.of(bare)));
    Long savedId = response.savedItems().get(0).lessonId();

    // when — 신청 마감 판정이 지나는 경로를 그대로 태운다.
    LessonProduct saved = lessonProductRepository.findById(savedId).orElseThrow();

    // then
    assertThat(saved.getMaxCapacity()).isNull();
    assertThat(saved.isFull()).isFalse();
    assertThat(saved.remainingCapacity()).isNull();
    saved.approveOneSeat();
    assertThat(saved.getCurrentCount()).isEqualTo(1);
  }

  /* ── 대표 미디어 ──────────────────────────────────────────── */

  @Test
  void newLesson_persistsHeroMedia_whenCreatedViaCreationKey() {
    // given — 신규 항목은 applyTo()가 아니라 newLesson() 빌더를 탄다.
    // 빌더에서 미디어를 빠뜨리면 created 응답이 나가면서 업로드가 조용히 사라진다.
    String creationKey = UUID.randomUUID().toString();
    LessonCollectionItemRequest item = withMedia(
        newItem(creationKey, "미디어 신규 수업"), "hero/abc.jpg", null);

    // when
    LessonCollectionSaveResponse response =
        service.save(principal, new LessonCollectionSaveRequest(List.of(item)));

    // then
    assertThat(response.createdCount()).isEqualTo(1);
    Long savedId = response.savedItems().get(0).lessonId();
    assertThat(jdbcTemplate.queryForObject("""
        SELECT media_image_key FROM lesson_products WHERE lesson_id = ?
        """, String.class, savedId)).isEqualTo("hero/abc.jpg");
  }

  @Test
  void mediaOnlyChange_isClassifiedAsUpdated_notReplayed() {
    // given — canonical state에 미디어가 없으면 equivalent==true로 판정돼 replayed로 빠지고
    // 변경이 조용히 버려진다.
    LessonCollectionItemRequest item = withMedia(
        existingItem(lessonId, 0L, "기존 수업"), "hero/xyz.jpg", null);

    // when
    LessonCollectionSaveResponse response =
        service.save(principal, new LessonCollectionSaveRequest(List.of(item)));

    // then
    assertThat(response.updatedCount()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT media_image_key FROM lesson_products WHERE lesson_id = ?
        """, String.class, lessonId)).isEqualTo("hero/xyz.jpg");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT authoring_revision FROM lesson_products WHERE lesson_id = ?
        """, Long.class, lessonId)).isEqualTo(1L);
  }

  @Test
  void save_rejectsMediaImageAndVideoTogether() {
    // given
    LessonCollectionItemRequest item = withMedia(
        newItem(UUID.randomUUID().toString(), "XOR 위반"), "hero/abc.jpg", 1L);

    // when / then
    assertThatThrownBy(() ->
        service.save(principal, new LessonCollectionSaveRequest(List.of(item))))
        .hasMessageContaining("하나만");
  }

  @Test
  void save_rejectsVideoOwnedByAnotherAcademy() {
    // given — 클라이언트가 보낸 videoId를 그대로 믿으면 남의 학원 영상을 걸 수 있다(IDOR).
    LessonCollectionItemRequest item = withMedia(
        newItem(UUID.randomUUID().toString(), "IDOR 시도"), null, 999_999L);

    // when / then
    assertThatThrownBy(() ->
        service.save(principal, new LessonCollectionSaveRequest(List.of(item))))
        .hasMessageContaining("수업상품 영상으로 지정할 수 없는");
  }

  @Test
  void newLesson_persistsOtVideo_independentlyFromHeroMedia() {
    // given
    jdbcTemplate.update("""
        INSERT INTO academy_videos
          (academy_id, original_filename, lifecycle_status, active_source,
           normalized_storage_key, normalized_media_type, normalized_size_bytes,
           thumbnail_key, duration_ms, width, height, source_version, raw_cleanup_status)
        VALUES (?, 'ot.mp4', 'READY', 'NORMALIZED',
                'ot-key', 'video/mp4', 100, 'ot-thumb',
                1000, 720, 1280, 0, 'DONE')
        """, academyId);
    Long otVideoId = jdbcTemplate.queryForObject(
        """
        SELECT video_id FROM academy_videos
        WHERE academy_id = ? AND normalized_storage_key = 'ot-key'
        """,
        Long.class, academyId);
    jdbcTemplate.update("""
        INSERT INTO academy_video_optimization_jobs
          (video_id, status, attempt_count, finished_at)
        VALUES (?, 'SUCCEEDED', 0, CURRENT_TIMESTAMP(6))
        """, otVideoId);
    LessonCollectionItemRequest base = withMedia(
        newItem(UUID.randomUUID().toString(), "OT 독립 수업"), "hero/abc.jpg", null);
    LessonCollectionItemRequest item = withOtVideo(base, otVideoId);

    // when
    LessonCollectionSaveResponse response =
        service.save(principal, new LessonCollectionSaveRequest(List.of(item)));

    // then
    Long savedId = response.savedItems().get(0).lessonId();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT media_image_key FROM lesson_products WHERE lesson_id = ?",
        String.class, savedId)).isEqualTo("hero/abc.jpg");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT media_video_id FROM lesson_products WHERE lesson_id = ?",
        Long.class, savedId)).isNull();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT ot_video_id FROM lesson_products WHERE lesson_id = ?",
        Long.class, savedId)).isEqualTo(otVideoId);
    assertThat(response.savedItems().get(0).otVideoId()).isEqualTo(otVideoId);
  }

  @Test
  void save_rejectsOtVideoOwnedByAnotherAcademy() {
    // given
    LessonCollectionItemRequest item = withOtVideo(
        newItem(UUID.randomUUID().toString(), "OT IDOR 시도"), 999_999L);

    // when / then
    assertThatThrownBy(() ->
        service.save(principal, new LessonCollectionSaveRequest(List.of(item))))
        .hasMessageContaining("수업상품 영상으로 지정할 수 없는");
  }

  private LessonCollectionItemRequest withMedia(
      LessonCollectionItemRequest base, String imageKey, Long videoId) {
    return new LessonCollectionItemRequest(
        base.lessonId(), base.creationKey(), base.expectedAuthoringRevision(), base.name(),
        base.description(), base.schoolName(), base.gradeLabel(), base.lessonType(), base.targetGroup(), base.priceAmount(),
        base.priceUnit(), base.priceSummary(), base.maxCapacity(), base.scheduleType(),
        base.weeklyCount(), base.weekdays(), base.startDate(), base.endDate(),
        base.durationWeeks(), base.durationMonths(), base.scheduleSummary(), base.status(),
        base.tabGuidanceLabel(), base.tabShortName(), base.kicker(), base.highlight(),
        base.learnerNeed(), base.coreDetailTitle(), base.explanation1(), base.explanation2(),
        base.explanation3(), base.theme(), imageKey, videoId, base.otVideoId());
  }

  private LessonCollectionItemRequest withOtVideo(
      LessonCollectionItemRequest base, Long otVideoId) {
    return new LessonCollectionItemRequest(
        base.lessonId(), base.creationKey(), base.expectedAuthoringRevision(), base.name(),
        base.description(), base.schoolName(), base.gradeLabel(), base.lessonType(), base.targetGroup(), base.priceAmount(),
        base.priceUnit(), base.priceSummary(), base.maxCapacity(), base.scheduleType(),
        base.weeklyCount(), base.weekdays(), base.startDate(), base.endDate(),
        base.durationWeeks(), base.durationMonths(), base.scheduleSummary(), base.status(),
        base.tabGuidanceLabel(), base.tabShortName(), base.kicker(), base.highlight(),
        base.learnerNeed(), base.coreDetailTitle(), base.explanation1(), base.explanation2(),
        base.explanation3(), base.theme(), base.mediaImageKey(), base.mediaVideoId(), otVideoId);
  }

  private LessonCollectionItemRequest newItem(String creationKey, String name) {
    return item(null, creationKey, null, name);
  }

  private LessonCollectionItemRequest item(
      Long id,
      String creationKey,
      Long expectedRevision,
      String name) {
    return new LessonCollectionItemRequest(
        id,
        creationKey,
        expectedRevision,
        name,
        "설명",
        null,
        null,
        LessonType.SUBSCRIPTION,
        TargetGroup.HIGH,
        null,
        PriceUnit.NEGOTIABLE,
        "협의",
        8,
        ScheduleType.CONSULTATION,
        null,
        Set.of(),
        null,
        null,
        null,
        null,
        "일정 협의",
        LessonStatus.DRAFT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        LessonTheme.NAVY,
        null,
        null,
        null);
  }
}
