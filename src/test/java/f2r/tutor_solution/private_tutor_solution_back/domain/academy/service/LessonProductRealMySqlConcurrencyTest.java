package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionItemRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionSaveResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionException;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.service.AdminVisibilityService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.service.LessonApplicationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Import(LessonProductRealMySqlConcurrencyTest.RepositoryPauseConfiguration.class)
class LessonProductRealMySqlConcurrencyTest {

  @Autowired
  private LessonCollectionService collectionService;

  @Autowired
  private AdminVisibilityService adminVisibilityService;

  @Autowired
  private LessonApplicationService applicationService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private RepositoryPauseController pauseController;

  private ExecutorService executor;
  private Long tutorId;
  private Long academyId;
  private Long lessonId;
  private Long studentUserId;
  private JwtTokenProvider.AuthenticatedPrincipal tutorPrincipal;
  private JwtTokenProvider.AuthenticatedPrincipal studentPrincipal;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(3);
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    jdbcTemplate.update("""
        INSERT INTO users (login_id, password, name, nickname, role)
        VALUES (?, 'pw', '동시성 강사', '동시성 강사', 'TUTOR')
        """, "g006-tutor-" + suffix);
    tutorId = jdbcTemplate.queryForObject(
        "SELECT user_id FROM users WHERE login_id = ?", Long.class, "g006-tutor-" + suffix);
    jdbcTemplate.update("""
        INSERT INTO academies
          (tutor_user_id, slug, name, logo_storage_key, intro, status, is_visible)
        VALUES (?, ?, 'G006 Academy', 'logo', 'intro', 'PUBLISHED', TRUE)
        """, tutorId, "g006-academy-" + suffix);
    academyId = jdbcTemplate.queryForObject(
        "SELECT academy_id FROM academies WHERE tutor_user_id = ?", Long.class, tutorId);
    jdbcTemplate.update("""
        INSERT INTO lesson_products
          (academy_id, name, description, lesson_type, target_group,
           price_unit, price_summary, max_capacity, current_count,
           schedule_type, schedule_summary, status, is_visible, authoring_revision)
        VALUES (?, '기존 수업', '설명', 'SUBSCRIPTION', 'HIGH',
                'NEGOTIABLE', '협의', 8, 3, 'CONSULTATION', '일정 협의',
                'PUBLISHED', TRUE, 0)
        """, academyId);
    lessonId = jdbcTemplate.queryForObject(
        "SELECT lesson_id FROM lesson_products WHERE academy_id = ? AND name = '기존 수업'",
        Long.class, academyId);

    jdbcTemplate.update("""
        INSERT INTO users (login_id, password, name, nickname, role)
        VALUES (?, 'pw', '동시성 학생', '동시성 학생', 'STUDENT')
        """, "g006-student-" + suffix);
    studentUserId = jdbcTemplate.queryForObject(
        "SELECT user_id FROM users WHERE login_id = ?", Long.class, "g006-student-" + suffix);
    jdbcTemplate.update("""
        INSERT INTO students (user_id, school_name, grade)
        VALUES (?, 'G006 School', 2)
        """, studentUserId);

    tutorPrincipal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutorId, "g006-tutor-" + suffix, Role.TUTOR.name());
    studentPrincipal = new JwtTokenProvider.AuthenticatedPrincipal(
        studentUserId, "g006-student-" + suffix, Role.STUDENT.name());
    authenticate(tutorPrincipal);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    pauseController.clear();
    SecurityContextHolder.clearContext();
    executor.shutdownNow();
    executor.awaitTermination(5, SECONDS);
    if (academyId != null) {
      jdbcTemplate.update("DELETE FROM lesson_applications WHERE lesson_id IN (SELECT lesson_id FROM lesson_products WHERE academy_id = ?)", academyId);
      jdbcTemplate.update("DELETE FROM conversations WHERE student_id IN (SELECT student_id FROM students WHERE user_id = ?)", studentUserId);
      jdbcTemplate.update("DELETE FROM lesson_products WHERE academy_id = ?", academyId);
      jdbcTemplate.update("DELETE FROM academies WHERE academy_id = ?", academyId);
    }
    if (studentUserId != null) {
      jdbcTemplate.update("DELETE FROM students WHERE user_id = ?", studentUserId);
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", studentUserId);
    }
    if (tutorId != null) {
      jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", tutorId);
    }
  }

  @Test
  void orderedFreshLockSeesCompetingCommit_andRejectsDifferingStaleState() throws Exception {
    // given
    String anchorKey = insertCreationKeyAnchor();
    CountDownLatch preResolved = new CountDownLatch(1);
    CountDownLatch allowLockHydration = new CountDownLatch(1);
    AtomicBoolean noLessonManagedBeforeLock = new AtomicBoolean();
    pauseAfterScalarPreResolution(preResolved, allowLockHydration, noLessonManagedBeforeLock);

    LessonCollectionSaveRequest staleRequest = new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "T1 수정"),
        newItem(anchorKey, "앵커 수업")));
    Future<Throwable> staleSave = executor.submit(tutorResult(() ->
        captureFailure(() -> collectionService.save(tutorPrincipal, staleRequest))));
    assertThat(preResolved.await(5, SECONDS)).isTrue();

    // when
    collectionService.save(tutorPrincipal, new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "T2 커밋"))));
    allowLockHydration.countDown();

    // then
    assertThat(staleSave.get(10, SECONDS))
        .isInstanceOf(LessonCollectionException.class)
        .extracting("code")
        .isEqualTo(LessonCollectionException.AUTHORING_STALE);
    assertThat(noLessonManagedBeforeLock).isTrue();
    assertLesson("T2 커밋", true, 3, 1L);
  }

  @Test
  void orderedFreshLockSeesCompetingCommit_andReplaysEquivalentStaleState() throws Exception {
    // given
    String anchorKey = insertCreationKeyAnchor();
    CountDownLatch preResolved = new CountDownLatch(1);
    CountDownLatch allowLockHydration = new CountDownLatch(1);
    AtomicBoolean noLessonManagedBeforeLock = new AtomicBoolean();
    pauseAfterScalarPreResolution(preResolved, allowLockHydration, noLessonManagedBeforeLock);

    LessonCollectionSaveRequest replayRequest = new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "경쟁 커밋"),
        newItem(anchorKey, "앵커 수업")));
    Future<LessonCollectionSaveResponse> replay = executor.submit(tutorResult(() ->
        collectionService.save(tutorPrincipal, replayRequest)));
    assertThat(preResolved.await(5, SECONDS)).isTrue();

    // when
    collectionService.save(tutorPrincipal, new LessonCollectionSaveRequest(List.of(
        existingItem(lessonId, 0L, "경쟁 커밋"))));
    allowLockHydration.countDown();

    // then
    assertThat(replay.get(10, SECONDS).replayedCount()).isEqualTo(2);
    assertThat(noLessonManagedBeforeLock).isTrue();
    assertLesson("경쟁 커밋", true, 3, 1L);
  }

  @Test
  void adminVisibilityFirst_thenAuthoring_preservesBothMutations() throws Exception {
    // given
    CountDownLatch adminLocked = new CountDownLatch(1);
    CountDownLatch releaseAdmin = new CountDownLatch(1);
    pauseAfterSingleLessonLock("admin-first", adminLocked, releaseAdmin);
    Future<?> admin = executor.submit(named("admin-first", () ->
        adminVisibilityService.updateLessonVisibility(lessonId, false)));
    assertThat(adminLocked.await(5, SECONDS)).isTrue();

    // when
    Future<?> authoring = executor.submit(tutorResult(() -> collectionService.save(
        tutorPrincipal,
        new LessonCollectionSaveRequest(List.of(existingItem(lessonId, 0L, "저작 후 수정"))))));
    releaseAdmin.countDown();
    admin.get(10, SECONDS);
    authoring.get(10, SECONDS);

    // then
    assertLesson("저작 후 수정", false, 3, 1L);
  }

  @Test
  void authoringFirst_thenAdminVisibility_preservesBothMutations() throws Exception {
    // given
    CountDownLatch authoringLocked = new CountDownLatch(1);
    CountDownLatch releaseAuthoring = new CountDownLatch(1);
    pauseAfterOrderedLessonLock("authoring-first", authoringLocked, releaseAuthoring);
    Future<?> authoring = executor.submit(named("authoring-first", () -> collectionService.save(
        tutorPrincipal,
        new LessonCollectionSaveRequest(List.of(existingItem(lessonId, 0L, "저작 먼저 수정"))))));
    assertThat(authoringLocked.await(5, SECONDS)).isTrue();

    // when
    Future<?> admin = executor.submit(() ->
        adminVisibilityService.updateLessonVisibility(lessonId, false));
    releaseAuthoring.countDown();
    authoring.get(10, SECONDS);
    admin.get(10, SECONDS);

    // then
    assertLesson("저작 먼저 수정", false, 3, 1L);
  }

  @Test
  void enrollmentFirst_thenAuthoring_preservesCountAndRevisionNeutrality() throws Exception {
    // given
    CountDownLatch enrollmentLocked = new CountDownLatch(1);
    CountDownLatch releaseEnrollment = new CountDownLatch(1);
    pauseAfterSingleLessonLock("enrollment-first", enrollmentLocked, releaseEnrollment);
    Future<?> enrollment = executor.submit(named("enrollment-first", () ->
        applicationService.apply(studentPrincipal, lessonId, null)));
    assertThat(enrollmentLocked.await(5, SECONDS)).isTrue();

    // when
    Future<?> authoring = executor.submit(tutorResult(() -> collectionService.save(
        tutorPrincipal,
        new LessonCollectionSaveRequest(List.of(existingItem(lessonId, 0L, "신청 후 수정"))))));
    releaseEnrollment.countDown();
    enrollment.get(10, SECONDS);
    authoring.get(10, SECONDS);

    // then
    assertLesson("신청 후 수정", true, 4, 1L);
  }

  @Test
  void authoringFirst_thenEnrollment_preservesCountAndRevisionNeutrality() throws Exception {
    // given
    CountDownLatch authoringLocked = new CountDownLatch(1);
    CountDownLatch releaseAuthoring = new CountDownLatch(1);
    pauseAfterOrderedLessonLock("authoring-before-enrollment", authoringLocked, releaseAuthoring);
    Future<?> authoring = executor.submit(named("authoring-before-enrollment", () ->
        collectionService.save(tutorPrincipal, new LessonCollectionSaveRequest(List.of(
            existingItem(lessonId, 0L, "신청 전 수정"))))));
    assertThat(authoringLocked.await(5, SECONDS)).isTrue();

    // when
    Future<?> enrollment = executor.submit(() ->
        applicationService.apply(studentPrincipal, lessonId, null));
    releaseAuthoring.countDown();
    authoring.get(10, SECONDS);
    enrollment.get(10, SECONDS);

    // then
    assertLesson("신청 전 수정", true, 4, 1L);
  }

  @Test
  void concurrentSameKeyInsert_createsOneRowAndRetryReplaysWinner() throws Exception {
    // given
    String creationKey = UUID.randomUUID().toString();
    CyclicBarrier bothPreResolvedEmpty = new CyclicBarrier(2);
    pauseController.after("findCreationKeyBindings", () -> {
      if (Thread.currentThread().getName().startsWith("same-key")) {
        bothPreResolvedEmpty.await(5, SECONDS);
      }
    });
    LessonCollectionSaveRequest request = new LessonCollectionSaveRequest(List.of(
        newItem(creationKey, "동일 키 수업")));

    // when
    Future<Object> first = executor.submit(namedResult("same-key-1", () -> saveOrFailure(request)));
    Future<Object> second = executor.submit(namedResult("same-key-2", () -> saveOrFailure(request)));
    List<Object> outcomes = List.of(first.get(15, SECONDS), second.get(15, SECONDS));

    // then
    assertThat(outcomes).as("outcomes=%s", outcomes)
        .filteredOn(LessonCollectionSaveResponse.class::isInstance).hasSize(1);
    assertThat(outcomes).as("outcomes=%s", outcomes)
        .filteredOn(LessonCollectionException.class::isInstance)
        .singleElement()
        .extracting("code")
        .isEqualTo(LessonCollectionException.CREATION_KEY_RACE);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM lesson_products WHERE academy_id = ? AND creation_key = ?
        """, Integer.class, academyId, creationKey)).isEqualTo(1);

    LessonCollectionSaveResponse retry = collectionService.save(tutorPrincipal, request);
    assertThat(retry.replayedCount()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM lesson_products WHERE academy_id = ? AND creation_key = ?
        """, Integer.class, academyId, creationKey)).isEqualTo(1);
  }

  private void pauseAfterScalarPreResolution(
      CountDownLatch preResolved,
      CountDownLatch allowLockHydration,
      AtomicBoolean noLessonManagedBeforeLock) {
    pauseController.after("findCreationKeyBindings", () -> {
      if (Thread.currentThread().getName().startsWith("pool-")) {
        SharedSessionContractImplementor session =
            entityManager.unwrap(SharedSessionContractImplementor.class);
        noLessonManagedBeforeLock.set(session.getPersistenceContextInternal()
            .getEntitiesByKey()
            .values()
            .stream()
            .noneMatch(LessonProduct.class::isInstance));
        preResolved.countDown();
        assertThat(allowLockHydration.await(5, SECONDS)).isTrue();
      }
    });
  }

  private void pauseAfterSingleLessonLock(
      String threadName,
      CountDownLatch locked,
      CountDownLatch release) {
    pauseController.after("findForUpdateByLessonId", () -> {
      if (Thread.currentThread().getName().equals(threadName)) {
        locked.countDown();
        assertThat(release.await(5, SECONDS)).isTrue();
      }
    });
  }

  private void pauseAfterOrderedLessonLock(
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

  private String insertCreationKeyAnchor() {
    String key = UUID.randomUUID().toString();
    jdbcTemplate.update("""
        INSERT INTO lesson_products
          (academy_id, name, description, lesson_type, target_group,
           price_unit, price_summary, max_capacity, current_count,
           schedule_type, schedule_summary, status, is_visible,
           creation_key, authoring_revision, theme_key)
        VALUES (?, '앵커 수업', '설명', 'SUBSCRIPTION', 'HIGH',
                'NEGOTIABLE', '협의', 8, 0, 'CONSULTATION', '일정 협의',
                'DRAFT', TRUE, ?, 1, 'NAVY')
        """, academyId, key);
    return key;
  }

  private LessonCollectionItemRequest existingItem(Long id, Long revision, String name) {
    return item(id, null, revision, name, LessonStatus.PUBLISHED);
  }

  private LessonCollectionItemRequest newItem(String creationKey, String name) {
    return item(null, creationKey, null, name, LessonStatus.DRAFT);
  }

  private LessonCollectionItemRequest item(
      Long id,
      String creationKey,
      Long revision,
      String name,
      LessonStatus status) {
    return new LessonCollectionItemRequest(
        id, creationKey, revision, name, "설명", null, null, LessonType.SUBSCRIPTION,
        TargetGroup.HIGH, null, PriceUnit.NEGOTIABLE, "협의", 8,
        ScheduleType.CONSULTATION, null, Set.of(), null, null, null, null,
        "일정 협의", status, null, null, null, null, null, null, null,
        null, null, LessonTheme.NAVY,
        null,
        null,
        null);
  }

  private void assertLesson(
      String name,
      boolean visible,
      int currentCount,
      long authoringRevision) {
    assertThat(jdbcTemplate.queryForMap("""
        SELECT name, is_visible, current_count, authoring_revision
        FROM lesson_products WHERE lesson_id = ?
        """, lessonId))
        .containsEntry("name", name)
        .containsEntry("is_visible", visible)
        .containsEntry("current_count", currentCount)
        .containsEntry("authoring_revision", authoringRevision);
  }

  private Object saveOrFailure(LessonCollectionSaveRequest request) {
    try {
      return collectionService.save(tutorPrincipal, request);
    } catch (Throwable failure) {
      return failure;
    }
  }

  private Throwable captureFailure(ThrowingRunnable action) {
    try {
      action.run();
      return null;
    } catch (Throwable failure) {
      return failure;
    }
  }

  private Runnable named(String name, ThrowingRunnable action) {
    return () -> {
      Thread.currentThread().setName(name);
      authenticate(tutorPrincipal);
      try {
        action.run();
      } catch (RuntimeException runtimeException) {
        throw runtimeException;
      } catch (Exception exception) {
        throw new CompletionException(exception);
      }
    };
  }

  private <T> Callable<T> namedResult(String name, Callable<T> action) {
    return () -> {
      Thread.currentThread().setName(name);
      authenticate(tutorPrincipal);
      return action.call();
    };
  }

  private <T> Callable<T> tutorResult(Callable<T> action) {
    return () -> {
      authenticate(tutorPrincipal);
      return action.call();
    };
  }

  private void authenticate(JwtTokenProvider.AuthenticatedPrincipal principal) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @TestConfiguration
  static class RepositoryPauseConfiguration {

    @Bean
    RepositoryPauseController repositoryPauseController() {
      return new RepositoryPauseController();
    }

    @Bean
    static BeanPostProcessor lessonProductRepositoryPausePostProcessor(
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
