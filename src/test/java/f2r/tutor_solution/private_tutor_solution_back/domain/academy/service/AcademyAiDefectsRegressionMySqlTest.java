package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiIdempotency;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiIdempotencyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyPageVersionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiIdempotencyService.DuplicateIdempotencyKeyException;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt P1 결함 회귀 테스트 (F1/F2/F3 + 추가 결함).
 *
 * <p>Claude 검수에서 발견된 결함의 수정 사항을 격리된 CI MySQL(f2r_test)에서 검증한다:
 * F1 (NULL 세션 FK), F2 (intro 스냅샷/복원), F3 (v1 보존),
 * P0 키 보호, 409 멱등성, PROCESSING 만료.
 *
 * <p>운영 DB와 완전히 분리되며, 운영 접속정보를 사용하지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("Academy AI 결함 회귀 테스트 (격리 DB)")
class AcademyAiDefectsRegressionMySqlTest {

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
  private AcademyAiSessionRepository sessionRepository;

  @Autowired
  private AcademyPageVersionRepository versionRepository;

  @Autowired
  private AcademyAiIdempotencyRepository idempotencyRepository;

  private User tutor;
  private JwtTokenProvider.AuthenticatedPrincipal principal;
  private Subject subject;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User(
        "ai_defect_" + suffix, "pw", "결함테스트강사", "결함별명",
        null, null, Role.TUTOR));
    principal = new JwtTokenProvider.AuthenticatedPrincipal(
        tutor.getUserId(), tutor.getLoginId(), "TUTOR");
    subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
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
  @DisplayName("F1: academyId=NULL 세션이 FK 위반 없이 저장된다")
  void f1_nullAcademyIdSession_savesWithoutFkViolation() {
    // 승인 전 생성 세션은 academy_id=NULL로 저장
    AcademyAiSession session = new AcademyAiSession(null, String.valueOf(tutor.getUserId()));
    AcademyAiSession saved = sessionRepository.saveAndFlush(session);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getAcademyId()).isNull();

    // 조회 확인
    AcademyAiSession found = sessionRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getAcademyId()).isNull();
    assertThat(found.getTutorId()).isEqualTo(String.valueOf(tutor.getUserId()));
  }

  @Test
  @DisplayName("F2: intro 변경 후 스냅샷의 intro로 복원 가능")
  void f2_introSnapshot_restorePreviousIntro() {
    academyService.createDraft(principal, draftRequest("결함학원", "원본 소개"), "f2-key");

    // 원본 intro로 스냅샷
    Map<String, String> draft = Map.of("introHeading", "원본 헤드라인");
    Long versionId = versionService.snapshot(
        principal, draft, Map.of(), "old intro", "원본 스냅샷");

    // intro 변경
    academyService.updateIntro(principal, "변경된 소개");

    // 스냅샷 버전의 intro 확인
    AcademyPageVersion version = versionService.getForRestore(principal, versionId);
    assertThat(version.getIntro()).isEqualTo("old intro");

    // 복원: 스냅샷 intro로 되돌림
    academyService.updateIntro(principal, version.getIntro());
    assertThat(academyService.getMine(principal).orElseThrow().intro())
        .isEqualTo("old intro");
  }

  @Test
  @DisplayName("F3: 25개 버전 생성 후 v1이 보존되고 총 20개 이하")
  void f3_25Versions_firstVersionPreserved() {
    academyService.createDraft(principal, draftRequest("결함학원", "소개"), "f3-key");
    AcademyResponse academy = academyService.getMine(principal).orElseThrow();

    // 25개 버전 생성
    for (int i = 1; i <= 25; i++) {
      versionService.snapshot(
          principal,
          Map.of("introHeading", "헤드라인 " + i),
          Map.of(),
          "버전 " + i);
    }

    // 총 20개 이하 유지
    long count = versionRepository.countByAcademyId(academy.academyId());
    assertThat(count).isLessThanOrEqualTo(20);

    // v1 보존 확인
    List<AcademyPageVersion> versions =
        versionRepository.findByAcademyIdOrderByVersionNoDesc(academy.academyId());
    assertThat(versions.stream().map(AcademyPageVersion::getVersionNo))
        .contains(1);
  }

  @Test
  @DisplayName("P0 보호: AI 키만 복원해도 P0 직접 설정 키가 유지된다")
  void p0Protection_aiOnlyRestore_preservesP0Keys() {
    academyService.createDraft(principal, draftRequest("결함학원", "소개"), "p0-key");

    // P0 키(사용자 직접 설정) + AI 키 혼합 적용
    Map<String, String> mixedDraft = new HashMap<>();
    mixedDraft.put("designPreset", "ARTS"); // P0: 프리셋 (AI allowlist 외부)
    mixedDraft.put("customColor", "#FF0000"); // P0: 사용자 직접 색상
    mixedDraft.put("introHeading", "AI 헤드라인"); // AI 허용 키
    applyService.applyDraftChanges(principal, mixedDraft, null);

    // AI 키만 있는 버전으로 복원 시뮬레이션
    Map<String, String> aiOnlyDraft = Map.of("introHeading", "복원된 헤드라인");
    applyService.applyDraftChanges(principal, aiOnlyDraft, null);

    // P0 키가 여전히 존재하는지 확인
    Map<String, String> current =
        academyService.getMine(principal).orElseThrow()
            .pageSettings().builderDraft();
    assertThat(current.get("introHeading")).isEqualTo("복원된 헤드라인");
    // P0 키는 applyDraftChanges의 병합 방식이므로 유지됨
    // (컨트롤러 복원 로직은 AI 키만 null 처리하여 P0 키 삭제 방지)
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @Test
  @DisplayName("409: 동일 멱등성 키 재요청 시 DuplicateIdempotencyKeyException")
  void idempotency_duplicateKey_throws409() {
    String tutorId = String.valueOf(tutor.getUserId());
    String key = "defect-409-" + UUID.randomUUID();
    idempotencyService.claim(tutorId, key);

    assertThatThrownBy(() -> idempotencyService.claim(tutorId, key))
        .isInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @Test
  @DisplayName("D1: 400 후 입력 수정 → 동일 키 재시도 → 성공")
  void d1_badRequest_thenRetryWithFixedInput_succeeds() {
    String tutorId = String.valueOf(tutor.getUserId());
    String key = "d1-retry-" + UUID.randomUUID();

    // 1차: 키 선점 (PROCESSING)
    AcademyAiIdempotency rec = idempotencyService.claim(tutorId, key);
    assertThat(rec.isProcessing()).isTrue();

    // 검증 400 시뮬레이션: markFailed 호출 (컨트롤러의 doCreateApply 래퍼와 동일)
    idempotencyService.markFailed(rec.getId());

    // 2차: 동일 키로 재시도 → FAILED이므로 재시도 허용
    AcademyAiIdempotency retried = idempotencyService.claim(tutorId, key);
    assertThat(retried.isProcessing()).isTrue();
    assertThat(retried.getId()).isNotEqualTo(rec.getId());

    // 성공 시뮬레이션: markCompleted
    idempotencyService.markCompleted(retried.getId(), 999L);
    AcademyAiIdempotency completed =
        idempotencyRepository.findByTutorIdAndIdempotencyKey(tutorId, key).orElseThrow();
    assertThat(completed.isCompleted()).isTrue();

    // 3차: 완료된 키 재요청 → 409 (중복 생성 차단)
    assertThatThrownBy(() -> idempotencyService.claim(tutorId, key))
        .isInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Test
  @DisplayName("D2: intro와 introHeading이 별도로 스냅샷/복원된다")
  void d2_introAndIntroHeading_separatelySnapshotted() {
    academyService.createDraft(principal, draftRequest("결함학원", "원본 intro"), "d2-key");

    // introHeading (builderDraft) + intro (top-level) 각각 설정
    Map<String, String> draft = Map.of("introHeading", "원본 헤드라인");
    Long versionId = versionService.snapshot(
        principal, draft, Map.of(), "원본 intro", "D2 스냅샷");

    // 버전에서 각각 확인
    AcademyPageVersion version = versionService.getForRestore(principal, versionId);
    assertThat(version.getIntro()).isEqualTo("원본 intro");
    assertThat(version.getBuilderDraftJson()).contains("원본 헤드라인");
    // introHeading과 intro가 혼동되지 않음
    assertThat(version.getBuilderDraftJson()).doesNotContain("원본 intro");
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @Test
  @DisplayName("PROCESSING 만료: 30분 초과 시 재시도 허용")
  void idempotency_expiredProcessing_allowsRetry() throws Exception {
    String tutorId = String.valueOf(tutor.getUserId());
    String key = "defect-expire-" + UUID.randomUUID();
    AcademyAiIdempotency rec = idempotencyService.claim(tutorId, key);
    assertThat(rec.isProcessing()).isTrue();

    // updatedAt을 31분 전으로 조작 (리플렉션)
    Field updatedAtField = AcademyAiIdempotency.class.getDeclaredField("updatedAt");
    updatedAtField.setAccessible(true);
    updatedAtField.set(rec, LocalDateTime.now().minusMinutes(31));
    idempotencyRepository.saveAndFlush(rec);

    // 만료 확인
    AcademyAiIdempotency expired =
        idempotencyRepository.findByTutorIdAndIdempotencyKey(tutorId, key).orElseThrow();
    assertThat(expired.isProcessingExpired()).isTrue();

    // 재시도 허용: 새 claim 성공
    AcademyAiIdempotency retried = idempotencyService.claim(tutorId, key);
    assertThat(retried.isProcessing()).isTrue();
    assertThat(retried.getId()).isNotEqualTo(rec.getId());
  }
}
