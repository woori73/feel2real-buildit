package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionItemRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonTheme;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.PriceUnit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.ScheduleType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionException;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import(LessonCollectionServiceMySqlVerificationMatrixTest.RepositoryPauseConfiguration.class)
class LessonCollectionServiceMySqlVerificationMatrixTest {

  private static final String INTEGRITY_CHECK =
      "chk_g007_lesson_collection_integrity";
  private static final String INTEGRITY_SENTINEL =
      "G007_INTEGRITY_SENTINEL";

  @Autowired
  private LessonCollectionService collectionService;

  @Autowired
  private LessonProductService lessonProductService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private RepositoryPauseController pauseController;

  private ExecutorService executor;
  private Long tutorId;
  private Long academyId;
  private Long lessonAId;
  private Long lessonBId;
  private Long lessonCId;
  private Long secondTutorId;
  private Long secondAcademyId;
  private JwtTokenProvider.AuthenticatedPrincipal tutorPrincipal;
  private JwtTokenProvider.AuthenticatedPrincipal secondTutorPrincipal;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(3);
    String suffix = shortSuffix();
    tutorId = insertTutor("g007-primary-" + suffix, "검증 강사");
    academyId = insertAcademy(tutorId, "g007-primary-" + suffix, "G007 Academy");
    lessonAId = insertLesson(academyId, "수업 A", 0L, null);
    lessonBId = insertLesson(academyId, "수업 B", null, null);
    lessonCId = insertLesson(academyId, "수업 C", 0L, null);
    tutorPrincipal = principal(tutorId, "g007-primary-" + suffix);
    authenticate(tutorPrincipal);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    pauseController.clear();
    SecurityContextHolder.clearContext();
    executor.shutdownNow();
    executor.awaitTermination(5, SECONDS);
    dropIntegrityCheckIfPresent();
    deleteAcademyAndTutor(secondAcademyId, secondTutorId);
    deleteAcademyAndTutor(academyId, tutorId);
  }

  @Test
  void savingDirtyA_doesNotWaitForOrNormalizeConcurrentOmittedB() throws Exception {
    // given
    CountDownLatch lessonBLocked = new CountDownLatch(1);
    CountDownLatch releaseLessonB = new CountDownLatch(1);
    pauseAfterOrderedLock("update-omitted-b", lessonBLocked, releaseLessonB);
    Future<LessonCollectionSaveResponse> updateB = executor.submit(namedSave(
        "update-omitted-b",
        tutorPrincipal,
        request(existingItem(lessonBId, 0L, "수업 B 경쟁 수정"))));
    assertThat(lessonBLocked.await(5, SECONDS)).isTrue();

    // when
    Future<LessonCollectionSaveResponse> saveA = executor.submit(namedSave(
        "save-dirty-a",
        tutorPrincipal,
        request(existingItem(lessonAId, 0L, "수업 A 단독 수정"))));

    // then
    assertThat(saveA.get(3, SECONDS).updatedCount()).isEqualTo(1);
    assertThat(row(lessonBId))
        .containsEntry("name", "수업 B")
        .containsEntry("theme_key", null)
        .containsEntry("authoring_revision", null);

    releaseLessonB.countDown();
    assertThat(updateB.get(10, SECONDS).updatedCount()).isEqualTo(1);
    assertThat(row(lessonBId))
        .containsEntry("name", "수업 B 경쟁 수정")
        .containsEntry("theme_key", "NAVY")
        .containsEntry("authoring_revision", 1L);
  }

  @Test
  void differingCreationKeyReplay_returnsBindingConflictAndKeepsWinnerRow() {
    // given
    String creationKey = UUID.randomUUID().toString();
    LessonCollectionSaveResponse winner = collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, "생성 승자")));
    Long winnerId = winner.savedItems().getFirst().lessonId();

    // when / then
    assertThatThrownBy(() -> collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, "다른 재시도"))))
        .isInstanceOf(LessonCollectionException.class)
        .extracting("code")
        .isEqualTo(LessonCollectionException.CREATION_KEY_BINDING_CONFLICT);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM lesson_products
        WHERE academy_id = ? AND creation_key = ?
        """, Integer.class, academyId, creationKey)).isEqualTo(1);
    assertThat(row(winnerId))
        .containsEntry("name", "생성 승자")
        .containsEntry("authoring_revision", 1L);
  }

  @Test
  void sameCreationKeyAcrossAcademies_createsIndependentBindings() {
    // given
    createSecondAcademy();
    String creationKey = UUID.randomUUID().toString();

    // when
    LessonCollectionSaveResponse primary = collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, "첫 Academy 수업")));
    authenticate(secondTutorPrincipal);
    LessonCollectionSaveResponse secondary = collectionService.save(
        secondTutorPrincipal,
        request(newItem(creationKey, "둘째 Academy 수업")));
    authenticate(tutorPrincipal);
    LessonCollectionSaveResponse primaryReplay = collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, "첫 Academy 수업")));

    // then
    assertThat(primary.savedItems().getFirst().lessonId())
        .isNotEqualTo(secondary.savedItems().getFirst().lessonId());
    assertThat(primaryReplay.savedItems().getFirst().lessonId())
        .isEqualTo(primary.savedItems().getFirst().lessonId());
    assertThat(primaryReplay.replayedCount()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForList("""
        SELECT academy_id, name FROM lesson_products
        WHERE creation_key = ? ORDER BY academy_id
        """, creationKey))
        .containsExactly(
            Map.of("academy_id", academyId, "name", "첫 Academy 수업"),
            Map.of("academy_id", secondAcademyId, "name", "둘째 Academy 수업"));
  }

  @Test
  void overlappingMultiRowSaves_completeInLockOrderWithoutPartialWrites() throws Exception {
    // given
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    pauseAfterOrderedLock("overlap-first", firstLocked, releaseFirst);
    Future<LessonCollectionSaveResponse> first = executor.submit(namedSave(
        "overlap-first",
        tutorPrincipal,
        request(
            existingItem(lessonAId, 0L, "수업 A 첫 저장"),
            existingItem(lessonBId, 0L, "수업 B 공통 저장"))));
    assertThat(firstLocked.await(5, SECONDS)).isTrue();

    // when
    Future<LessonCollectionSaveResponse> second = executor.submit(namedSave(
        "overlap-second",
        tutorPrincipal,
        request(
            existingItem(lessonBId, 0L, "수업 B 공통 저장"),
            existingItem(lessonCId, 0L, "수업 C 둘째 저장"))));
    assertThat(second.isDone()).isFalse();
    MILLISECONDS.sleep(250);
    assertThat(second.isDone()).isFalse();
    releaseFirst.countDown();

    // then
    assertThat(first.get(10, SECONDS).updatedCount()).isEqualTo(2);
    LessonCollectionSaveResponse secondResponse = second.get(10, SECONDS);
    assertThat(secondResponse.updatedCount()).isEqualTo(1);
    assertThat(secondResponse.replayedCount()).isEqualTo(1);
    assertAuthoredState(lessonAId, "수업 A 첫 저장", 1L);
    assertAuthoredState(lessonBId, "수업 B 공통 저장", 1L);
    assertAuthoredState(lessonCId, "수업 C 둘째 저장", 1L);
  }

  @Test
  void foreignLessonInCollection_failsAtomicallyWithoutOwnershipLeak() {
    // given
    createSecondAcademy();
    Long foreignLessonId = insertLesson(secondAcademyId, "비공개 외부 수업", 0L, null);
    LessonCollectionSaveRequest mixedRequest = request(
        existingItem(lessonAId, 0L, "롤백되어야 하는 수정"),
        existingItem(foreignLessonId, 0L, "외부 수업 탈취 시도"));

    // when / then
    assertThatThrownBy(() -> collectionService.save(tutorPrincipal, mixedRequest))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(failure -> {
          ResponseStatusException response = (ResponseStatusException) failure;
          assertThat(response.getStatusCode().value()).isEqualTo(404);
          assertThat(response.getReason())
              .isEqualTo("수업 상품을 찾을 수 없습니다.")
              .doesNotContain(foreignLessonId.toString())
              .doesNotContain("비공개 외부 수업");
        });
    assertAuthoredState(lessonAId, "수업 A", 0L);
    assertAuthoredState(foreignLessonId, "비공개 외부 수업", 0L);
  }

  @Test
  void saveBasedOnPreHideRevision_returnsStaleAndCannotUnhide() {
    // given
    LessonCollectionSaveRequest preHideSave = request(
        existingItem(lessonAId, 0L, "숨김 전 편집 내용"));
    lessonProductService.hide(tutorPrincipal, lessonAId);

    // when / then
    assertThatThrownBy(() -> collectionService.save(tutorPrincipal, preHideSave))
        .isInstanceOf(LessonCollectionException.class)
        .extracting("code")
        .isEqualTo(LessonCollectionException.AUTHORING_STALE);
    assertThat(row(lessonAId))
        .containsEntry("name", "수업 A")
        .containsEntry("status", "HIDDEN")
        .containsEntry("is_visible", false)
        .containsEntry("authoring_revision", 1L);
  }

  @Test
  void unrelatedRealIntegrityViolation_isNotClassifiedAsCreationKeyRace(
      CapturedOutput output) {
    // given
    jdbcTemplate.execute("""
        ALTER TABLE lesson_products
        ADD CONSTRAINT chk_g007_lesson_collection_integrity
        CHECK (name <> 'G007_INTEGRITY_SENTINEL')
        """);
    String creationKey = UUID.randomUUID().toString();

    // when / then
    assertThatThrownBy(() -> collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, INTEGRITY_SENTINEL))))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(LessonCollectionException.class);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM lesson_products
        WHERE academy_id = ? AND creation_key = ?
        """, Integer.class, academyId, creationKey)).isZero();
    assertThat(output)
        .doesNotContain(LessonCollectionException.CREATION_KEY_RACE)
        .doesNotContain(creationKey);
  }

  @Test
  void bindingConflict_usesStableSafeCodeWithoutPayloadLogging(CapturedOutput output) {
    // given
    String creationKey = UUID.randomUUID().toString();
    String secretWinnerName = "기밀 승자 수업명";
    String secretReplayName = "기밀 충돌 수업명";
    collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, secretWinnerName)));

    // when / then
    assertThatThrownBy(() -> collectionService.save(
        tutorPrincipal,
        request(newItem(creationKey, secretReplayName))))
        .isInstanceOfSatisfying(LessonCollectionException.class, conflict -> {
          assertThat(conflict.getCode())
              .isEqualTo(LessonCollectionException.CREATION_KEY_BINDING_CONFLICT);
          assertThat(conflict.isRetryable()).isFalse();
          assertThat(conflict.getMessage())
              .isEqualTo("이미 저장된 수업 상품과 생성 요청 내용이 일치하지 않습니다.")
              .doesNotContain(secretWinnerName)
              .doesNotContain(secretReplayName)
              .doesNotContain(creationKey);
        });
    assertThat(output)
        .doesNotContain(secretWinnerName)
        .doesNotContain(secretReplayName)
        .doesNotContain(creationKey)
        .doesNotContain("insert into lesson_products");
  }

  private void createSecondAcademy() {
    String suffix = shortSuffix();
    secondTutorId = insertTutor("g007-secondary-" + suffix, "다른 강사");
    secondAcademyId = insertAcademy(
        secondTutorId,
        "g007-secondary-" + suffix,
        "G007 Other Academy");
    secondTutorPrincipal = principal(secondTutorId, "g007-secondary-" + suffix);
  }

  private Long insertTutor(String loginId, String name) {
    jdbcTemplate.update("""
        INSERT INTO users (login_id, password, name, nickname, role)
        VALUES (?, 'pw', ?, ?, 'TUTOR')
        """, loginId, name, name);
    return jdbcTemplate.queryForObject(
        "SELECT user_id FROM users WHERE login_id = ?",
        Long.class,
        loginId);
  }

  private Long insertAcademy(Long ownerId, String slug, String name) {
    jdbcTemplate.update("""
        INSERT INTO academies
          (tutor_user_id, slug, name, logo_storage_key, intro, status, is_visible)
        VALUES (?, ?, ?, 'logo', 'intro', 'PUBLISHED', TRUE)
        """, ownerId, slug, name);
    return jdbcTemplate.queryForObject(
        "SELECT academy_id FROM academies WHERE tutor_user_id = ?",
        Long.class,
        ownerId);
  }

  private Long insertLesson(
      Long ownerAcademyId,
      String name,
      Long authoringRevision,
      String themeKey) {
    jdbcTemplate.update("""
        INSERT INTO lesson_products
          (academy_id, name, description, lesson_type, target_group,
           price_unit, price_summary, max_capacity, current_count,
           schedule_type, schedule_summary, status, is_visible,
           authoring_revision, theme_key)
        VALUES (?, ?, '설명', 'SUBSCRIPTION', 'HIGH',
                'NEGOTIABLE', '협의', 8, 0, 'CONSULTATION', '일정 협의',
                'PUBLISHED', TRUE, ?, ?)
        """, ownerAcademyId, name, authoringRevision, themeKey);
    return jdbcTemplate.queryForObject("""
        SELECT lesson_id FROM lesson_products
        WHERE academy_id = ? AND name = ?
        """, Long.class, ownerAcademyId, name);
  }

  private void deleteAcademyAndTutor(Long ownerAcademyId, Long ownerTutorId) {
    if (ownerAcademyId != null) {
      jdbcTemplate.update(
          "DELETE FROM lesson_weekdays WHERE lesson_id IN "
              + "(SELECT lesson_id FROM lesson_products WHERE academy_id = ?)",
          ownerAcademyId);
      jdbcTemplate.update(
          "DELETE FROM lesson_products WHERE academy_id = ?",
          ownerAcademyId);
      jdbcTemplate.update(
          "DELETE FROM academies WHERE academy_id = ?",
          ownerAcademyId);
    }
    if (ownerTutorId != null) {
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", ownerTutorId);
    }
  }

  private void dropIntegrityCheckIfPresent() {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'lesson_products'
          AND CONSTRAINT_NAME = ?
        """, Integer.class, INTEGRITY_CHECK);
    if (count != null && count > 0) {
      jdbcTemplate.execute("ALTER TABLE lesson_products DROP CHECK " + INTEGRITY_CHECK);
    }
  }

  private void pauseAfterOrderedLock(
      String threadName,
      CountDownLatch locked,
      CountDownLatch release) {
    pauseController.after("findAllForUpdateByAcademyIdAndLessonIds", () -> {
      if (Thread.currentThread().getName().equals(threadName)) {
        locked.countDown();
        assertThat(release.await(5, SECONDS)).isTrue();
      }
    });
  }

  private Callable<LessonCollectionSaveResponse> namedSave(
      String threadName,
      JwtTokenProvider.AuthenticatedPrincipal principal,
      LessonCollectionSaveRequest request) {
    return () -> {
      Thread.currentThread().setName(threadName);
      authenticate(principal);
      try {
        return collectionService.save(principal, request);
      } catch (RuntimeException runtimeException) {
        throw runtimeException;
      } catch (Exception exception) {
        throw new CompletionException(exception);
      }
    };
  }

  private LessonCollectionSaveRequest request(LessonCollectionItemRequest... items) {
    return new LessonCollectionSaveRequest(List.of(items));
  }

  private LessonCollectionItemRequest existingItem(
      Long lessonId,
      Long expectedRevision,
      String name) {
    return item(lessonId, null, expectedRevision, name, LessonStatus.PUBLISHED);
  }

  private LessonCollectionItemRequest newItem(String creationKey, String name) {
    return item(null, creationKey, null, name, LessonStatus.DRAFT);
  }

  private LessonCollectionItemRequest item(
      Long lessonId,
      String creationKey,
      Long expectedRevision,
      String name,
      LessonStatus status) {
    return new LessonCollectionItemRequest(
        lessonId,
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
        status,
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

  private Map<String, Object> row(Long lessonId) {
    return jdbcTemplate.queryForMap("""
        SELECT name, status, is_visible, authoring_revision, theme_key
        FROM lesson_products WHERE lesson_id = ?
        """, lessonId);
  }

  private void assertAuthoredState(Long lessonId, String name, long revision) {
    assertThat(row(lessonId))
        .containsEntry("name", name)
        .containsEntry("authoring_revision", revision);
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal(Long userId, String loginId) {
    return new JwtTokenProvider.AuthenticatedPrincipal(
        userId,
        loginId,
        Role.TUTOR.name());
  }

  private void authenticate(JwtTokenProvider.AuthenticatedPrincipal principal) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private String shortSuffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @TestConfiguration
  static class RepositoryPauseConfiguration {

    @Bean
    RepositoryPauseController verificationMatrixRepositoryPauseController() {
      return new RepositoryPauseController();
    }

    @Bean
    static BeanPostProcessor verificationMatrixRepositoryPausePostProcessor(
        RepositoryPauseController controller) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (!"lessonProductRepository".equals(beanName)) {
            return bean;
          }
          ProxyFactory proxyFactory = new ProxyFactory(bean);
          proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            controller.runAfter(invocation.getMethod().getName());
            return result;
          });
          return proxyFactory.getProxy();
        }
      };
    }
  }

  static final class RepositoryPauseController {
    private volatile String methodName;
    private volatile ThrowingRunnable hook;

    void after(String methodName, ThrowingRunnable hook) {
      this.methodName = methodName;
      this.hook = hook;
    }

    void runAfter(String invokedMethodName) throws Exception {
      ThrowingRunnable current = hook;
      if (current != null && invokedMethodName.equals(methodName)) {
        current.run();
      }
    }

    void clear() {
      methodName = null;
      hook = null;
    }
  }
}
