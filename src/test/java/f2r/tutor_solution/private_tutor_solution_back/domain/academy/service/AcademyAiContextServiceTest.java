package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AcademyAiContextService — normalized AI context 조립")
class AcademyAiContextServiceTest {

  private AcademyService academyService;
  private LessonProductRepository lessonProductRepository;
  private AcademyVideoRepository academyVideoRepository;
  private AcademyAiContextService service;
  private Academy academy;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    lessonProductRepository = mock(LessonProductRepository.class);
    academyVideoRepository = mock(AcademyVideoRepository.class);
    AcademyCompletionCheckService completionCheckService = new AcademyCompletionCheckService(
        academyService, lessonProductRepository, academyVideoRepository);
    service = new AcademyAiContextService(
        academyService, lessonProductRepository, academyVideoRepository, completionCheckService);
    when(academyVideoRepository.findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(VideoLifecycleStatus.READY)))
        .thenReturn(List.of());

    User tutor = User.builder().loginId("tutor").password("pw").name("강사").phone("010").build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);

    Subject english = Subject.builder().nameEn("English").nameKo("영어").build();
    Map<String, String> draft = Map.of(
        "principalName", "김원장",
        "principalRole", "대표 원장",
        "principalDesc", "20년째 학생들을 지도하고 있습니다.",
        "philosophyItem1", "기초부터 탄탄하게",
        "philosophyItem2", "",
        // 민감정보로 취급되어 context에 들어가면 안 되는 값들(§4 검증용)
        "monthlyTuition", "300000",
        "operationYears", "20");

    academy = Academy.builder()
        .tutor(tutor).slug("my-academy").name("우리학원")
        .logoStorageKey("logo").intro("우리 학원 소개입니다")
        .subjects(Set.of(english))
        .targetGroups(Set.of(TargetGroup.MIDDLE, TargetGroup.HIGH))
        .pageSettings(new AcademyPageSettings(
            null, null, null, null, Map.of(), List.of(), draft,
            new AcademyBusinessInfoFixture().withPhoneEmail("010-1234-5678", "hello@academy.co.kr")))
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);

    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(academyService.requireMyAcademyForBuilder(principal)).thenReturn(academy);
  }

  @Test
  @DisplayName("given Academy when context 조립 then A급 필드만 정확히 담긴다")
  void buildContext_includes_a_grade_fields_only() {
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy).name("중등 내신 영어")
        .kicker("출제자의 눈으로 분석") .learnerNeed("서술형에서 감점될 때")
        .highlight("빈출 포인트 정리")
        .build();
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(100L))
        .thenReturn(List.of(lesson));

    AcademyAiContext context = service.buildContext(principal);

    assertThat(context.academyName()).isEqualTo("우리학원");
    assertThat(context.intro()).isEqualTo("우리 학원 소개입니다");
    assertThat(context.subjects()).containsExactly("영어");
    assertThat(context.targetGroups()).containsExactly("MIDDLE", "HIGH"); // 선언 순서
    assertThat(context.principalName()).isEqualTo("김원장");
    assertThat(context.principalRole()).isEqualTo("대표 원장");
    assertThat(context.principalDesc()).isEqualTo("20년째 학생들을 지도하고 있습니다.");
    assertThat(context.philosophyItems()).containsExactly("기초부터 탄탄하게", "", "", "");
    assertThat(context.lessons()).hasSize(1);
    assertThat(context.lessons().get(0).name()).isEqualTo("중등 내신 영어");
    assertThat(context.lessons().get(0).kicker()).isEqualTo("출제자의 눈으로 분석");
    assertThat(context.lessons().get(0).learnerNeed()).isEqualTo("서술형에서 감점될 때");
    assertThat(context.lessons().get(0).highlight()).isEqualTo("빈출 포인트 정리");
  }

  @Test
  @DisplayName("given Academy context when 다른 Academy의 principal로 조회 then 항상 requireMyAcademyForBuilder 기준으로만 조립된다")
  void buildContext_never_takes_academyId_from_caller() {
    // AcademyAiContextService에는애초에 academyId 파라미터를 받는 오버로드가 없다 —
    // requireMyAcademyForBuilder(principal)만으로 scope가 정해진다는 것을 시그니처 수준에서 보장한다.
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
    service.buildContext(principal);
    org.mockito.Mockito.verify(academyService).requireMyAcademyForBuilder(principal);
  }

  @Test
  @DisplayName("given AcademyAiContext record when 필드 목록을 확인 then 민감/불필요 정보를 담을 필드 자체가 없다")
  void context_record_has_no_sensitive_fields() {
    // 사업자 정보(전화/이메일/사업자등록번호), 가격/일정, 내부 식별자(creationKey 등)는
    // 애초에 담을 필드 자체가 record에 없어야 한다 — 개발자가 실수로 채워도 구조상 못 나간다.
    for (RecordComponent component : AcademyAiContext.class.getRecordComponents()) {
      String name = component.getName().toLowerCase();
      assertThat(name).doesNotContain("phone");
      assertThat(name).doesNotContain("email");
      assertThat(name).doesNotContain("password");
      assertThat(name).doesNotContain("token");
      assertThat(name).doesNotContain("businessinfo");
      assertThat(name).doesNotContain("price");
      assertThat(name).doesNotContain("creationkey");
    }
  }

  @Test
  @DisplayName("given lessons가 없을 때 when context 조립 then 빈 목록으로 안전하게 떨어진다")
  void buildContext_handles_no_lessons() {
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(100L))
        .thenReturn(List.of());
    AcademyAiContext context = service.buildContext(principal);
    assertThat(context.lessons()).isEmpty();
  }

  @Test
  @DisplayName("given Academy when composition 조립 then 섹션 순서/노출·콘텐츠 플래그·완성도 요약이 담긴다")
  void buildContext_includes_composition() {
    LessonProduct lesson = LessonProduct.builder().academy(academy).name("중등 영어").build();
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(100L))
        .thenReturn(List.of(lesson));

    AcademyAiContext.Composition composition = service.buildContext(principal).composition();

    assertThat(composition).isNotNull();
    // sec-hero는 항상 0번, 총 16개 관리 섹션.
    assertThat(composition.sections()).hasSize(16);
    assertThat(composition.sections().get(0).key()).isEqualTo("sec-hero");
    // opt-in 섹션(sec-featured 등)은 명시적 ON이 없으므로 숨김.
    assertThat(composition.sections().stream()
        .filter(s -> s.key().equals("sec-featured")).findFirst().orElseThrow().visible()).isFalse();
    // 미구현 섹션은 editable=false.
    assertThat(composition.sections().stream()
        .filter(s -> s.key().equals("sec-reviews")).findFirst().orElseThrow().editable()).isFalse();
    // 대표 수업이 1건 있으므로 sec-lessons는 콘텐츠 있음.
    assertThat(composition.sections().stream()
        .filter(s -> s.key().equals("sec-lessons")).findFirst().orElseThrow().hasContent()).isTrue();
    assertThat(composition.programCardCount()).isEqualTo(1);
    assertThat(composition.hasDirectorVideo()).isFalse();
    assertThat(composition.editableTextTargets()).contains("principalDesc", "featuredCtaLabel");
    // 완성도 요약은 completion 서비스의 deterministic 결과를 그대로 쓴다.
    assertThat(composition.completionScorePercent()).isBetween(0, 100);
    assertThat(composition.completionMissing()).isNotNull();
  }

  @Test
  @DisplayName("given editableTextTargets when allowlist와 대조 then 위험 필드(가격/연락처/원장 이름)를 포함하지 않는다")
  void editableTextTargets_excludes_dangerous_fields() {
    when(lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
    List<String> targets = service.buildContext(principal).composition().editableTextTargets();
    assertThat(targets).doesNotContain(
        "monthlyTuition", "operationYears", "scoreImprovement",
        "principalName", "principalRole", "headerTitle");
  }

  /** AcademyBusinessInfo 생성자 파라미터가 많아 테스트 가독성을 위해 감싼 헬퍼. */
  private record AcademyBusinessInfoFixture() {
    f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyBusinessInfo withPhoneEmail(
        String phone, String email) {
      return new f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyBusinessInfo(
          "학원", "대표", "주소", null, null, null, phone, email);
    }
  }
}
