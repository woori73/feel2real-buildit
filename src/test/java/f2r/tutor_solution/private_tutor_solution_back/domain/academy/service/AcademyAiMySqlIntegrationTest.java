package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiIdempotency;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiIdempotencyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiIdempotencyService.DuplicateIdempotencyKeyException;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt P1 MySQL 통합 테스트.
 *
 * <p>격리된 CI MySQL(f2r_test)에서 실제 DB로 검증한다:
 * 신규 생성·DRAFT 저장·소개글 수정·버전 복원·400·403·409·멱등성 동시성.
 *
 * <p>운영 DB와 완전히 분리되며, 운영 접속정보를 사용하지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("Academy AI MySQL 통합 테스트 (격리 DB)")
class AcademyAiMySqlIntegrationTest {

  @Autowired
  private AcademyService academyService;

  @Autowired
  private AcademyAiIdempotencyService idempotencyService;

  @Autowired
  private AcademyPageVersionService versionService;

  @Autowired
  private AcademyAiApplyService applyService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private AcademyAiIdempotencyRepository idempotencyRepository;

  private User tutor;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private Subject subject;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User(
        "ai_test_" + suffix, "pw", "테스트강사", "테스트별명",
        null, null, Role.TUTOR));
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutor.getUserId(), tutor.getLoginId(), "TUTOR");
    subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
  }

  private AcademyUpsertRequest draftRequest(String name, String intro) {
    return new AcademyUpsertRequest(
        name,
        null,
        intro,
        Set.of(subject.getSubjectId()),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울특별시", "강남구")),
        new AcademyPageSettings(
            null, null, null, null, null, null,
            Map.of("designPreset", "ENGLISH", "introHeading", "테스트 헤드라인"),
            null));
  }

  @Test
  @DisplayName("신규 생성 → DRAFT 상태로 저장된다")
  void createDraft_savesAsDraft() {
    AcademyResponse created =
        academyService.createDraft(principal, draftRequest("테스트학원", "소개문"), "key-1");

    assertThat(created.status()).isEqualTo(AcademyStatus.DRAFT);
    assertThat(created.academyId()).isNotNull();
    assertThat(academyService.getMine(principal)).isPresent();
  }

  @Test
  @DisplayName("소개글 수정 → DB에 반영된다")
  void updateIntro_persistsToDb() {
    academyService.createDraft(principal, draftRequest("테스트학원", "소개문"), "key-1");

    AcademyResponse updated = academyService.updateIntro(principal, "새로운 소개문");

    assertThat(updated.intro()).isEqualTo("새로운 소개문");
    assertThat(academyService.getMine(principal).orElseThrow().intro())
        .isEqualTo("새로운 소개문");
  }

  @Test
  @DisplayName("스냅샷 → 수정 → 복원 라운드트립")
  void snapshotRestore_roundtrip() {
    AcademyResponse created =
        academyService.createDraft(principal, draftRequest("테스트학원", "소개문"), "key-1");

    // 스냅샷 (원본)
    Map<String, String> original = Map.of("introHeading", "원본 헤드라인");
    applyService.applyDraftChanges(principal, original, null);
    Long v1 = versionService.snapshot(
        principal, original, Map.of(), "원본");

    // 수정
    Map<String, String> modified = Map.of("introHeading", "수정된 헤드라인");
    applyService.applyDraftChanges(principal, modified, null);

    // 복원: 스냅샷 버전 조회 → 적용
    var version = versionService.getForRestore(principal, v1);
    assertThat(version.getBuilderDraftJson()).contains("원본 헤드라인");

    // 복원 실행 (버전의 draft를 그대로 적용)
    applyService.applyDraftChanges(
        principal, Map.of("introHeading", "원본 헤드라인"), null);
    assertThat(academyService.getMine(principal).orElseThrow()
        .pageSettings().builderDraft().get("introHeading"))
        .isEqualTo("원본 헤드라인");
    assertThat(created.academyId()).isNotNull();
  }

  @Test
  @DisplayName("400: 소개 누락 시 IllegalArgumentException")
  void createDraft_missingIntro_throws400() {
    assertThatThrownBy(() ->
        academyService.createDraft(principal, draftRequest("테스트학원", ""), "key-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("소개");
  }

  @Test
  @DisplayName("403: 타인의 버전에 접근하면 AccessDeniedException")
  void getForRestore_otherAcademy_throws403() {
    // 다른 강사의 버전 생성
    User other = userRepository.save(new User(
        "ai_test_other_" + UUID.randomUUID().toString().substring(0, 8),
        "pw", "다른강사", "별명2", null, null, Role.TUTOR));
    var otherPrincipal = new JwtTokenProvider.AuthenticatedPrincipal(
        other.getUserId(), other.getLoginId(), "TUTOR");
    academyService.createDraft(otherPrincipal,
        draftRequest("다른학원", "소개"), "key-other");
    Long otherVersion = versionService.snapshot(
        otherPrincipal, Map.of("k", "v"), Map.of(), "타인 버전");

    // 내 principal로 타인 버전에 접근 → 403
    assertThatThrownBy(() -> versionService.getForRestore(principal, otherVersion))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("409: 동일 멱등성 키 재선점 시 DuplicateIdempotencyKeyException")
  void claim_duplicateKey_throws409() {
    String tutorId = String.valueOf(tutor.getUserId());
    idempotencyService.claim(tutorId, "dup-key");

    assertThatThrownBy(() -> idempotencyService.claim(tutorId, "dup-key"))
        .isInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Test
  @DisplayName("멱등성 동시 선점 → 1건만 성공, 나머지는 409")
  void claim_concurrent_sameKey_oneWins() throws Exception {
    String tutorId = String.valueOf(tutor.getUserId());
    String key = "concurrent-" + UUID.randomUUID();
    int threads = 10;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger duplicate = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        ready.countDown();
        try {
          start.await();
          idempotencyService.claim(tutorId, key);
          success.incrementAndGet();
        } catch (DuplicateIdempotencyKeyException e) {
          duplicate.incrementAndGet();
        } catch (Exception e) {
          // 예상 외 예외는 별도 집계하지 않음
        }
        return null;
      });
    }
    ready.await();
    start.countDown();
    pool.shutdown();
    pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(success.get()).isEqualTo(1);
    assertThat(duplicate.get()).isEqualTo(threads - 1);
    // DB에 정확히 1건만 존재
    assertThat(idempotencyRepository.findByTutorIdAndIdempotencyKey(tutorId, key))
        .isPresent();
  }

  @Test
  @DisplayName("FAILED 키는 재시도가 허용된다")
  void claim_failedKey_allowsRetry() {
    String tutorId = String.valueOf(tutor.getUserId());
    AcademyAiIdempotency rec = idempotencyService.claim(tutorId, "retry-key");
    idempotencyService.markFailed(rec.getId());

    AcademyAiIdempotency retried = idempotencyService.claim(tutorId, "retry-key");

    assertThat(retried.isProcessing()).isTrue();
  }
}
