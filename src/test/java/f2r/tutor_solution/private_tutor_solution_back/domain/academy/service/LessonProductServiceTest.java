package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LessonProductServiceTest {

  private LessonProductRepository lessonProductRepository;
  private AcademyRepository academyRepository;
  private LessonApplicationRepository lessonApplicationRepository;
  private AuthenticatedUserResolver authResolver;
  private LessonProductService service;

  private final JwtTokenProvider.AuthenticatedPrincipal principal =
      new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor1", Role.TUTOR.name());

  @BeforeEach
  void setUp() {
    lessonProductRepository = mock(LessonProductRepository.class);
    academyRepository = mock(AcademyRepository.class);
    lessonApplicationRepository = mock(LessonApplicationRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    service = new LessonProductService(
        lessonProductRepository, academyRepository, lessonApplicationRepository, authResolver);
  }

  @Test
  void create_savesWeeklySubscriptionLessonWithStructuredFields() {
    // given
    Academy academy = academy();
    LessonUpsertRequest request = weeklyRequest();
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(lessonProductRepository.save(any(LessonProduct.class))).thenAnswer(invocation -> {
      LessonProduct lesson = invocation.getArgument(0);
      ReflectionTestUtils.setField(lesson, "lessonId", 200L);
      return lesson;
    });

    // when
    LessonResponse response = service.create(principal, request);

    // then
    assertThat(response.lessonId()).isEqualTo(200L);
    assertThat(response.lessonType()).isEqualTo(LessonType.SUBSCRIPTION);
    assertThat(response.targetGroup()).isEqualTo(TargetGroup.HIGH);
    assertThat(response.priceAmount()).isEqualTo(500_000);
    assertThat(response.weekdays()).containsExactlyInAnyOrder(LessonWeekday.MONDAY, LessonWeekday.WEDNESDAY);
    verify(authResolver).verifyOwnsAcademy(academy);
  }

  @Test
  void create_throwsWhenWeeklyScheduleHasNoWeekdays() {
    // given
    LessonUpsertRequest request = new LessonUpsertRequest(
        "중3 영어 내신반",
        "설명",
        LessonType.SUBSCRIPTION,
        500_000,
        PriceUnit.MONTH,
        "50만원/1개월",
        15,
        ScheduleType.WEEKLY,
        2,
        Set.of(),
        null,
        null,
        null,
        null,
        "주2회",
        LessonStatus.PUBLISHED);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("요일");
  }

  @Test
  void create_allowsNegotiablePriceWithoutAmountForConsultationSchedule() {
    // given
    Academy academy = academy();
    LessonUpsertRequest request = new LessonUpsertRequest(
        "기간협의반",
        null,
        LessonType.SHORT_TERM,
        null,
        PriceUnit.NEGOTIABLE,
        "기간협의",
        8,
        ScheduleType.CONSULTATION,
        null,
        null,
        null,
        null,
        null,
        null,
        "일정협의",
        LessonStatus.PUBLISHED);
    when(academyRepository.findByTutor_UserId(10L)).thenReturn(Optional.of(academy));
    when(lessonProductRepository.save(any(LessonProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // when
    LessonResponse response = service.create(principal, request);

    // then
    assertThat(response.priceAmount()).isNull();
    assertThat(response.scheduleType()).isEqualTo(ScheduleType.CONSULTATION);
  }

  @Test
  void create_throwsWhenFixedPriceHasNoAmount() {
    // given
    LessonUpsertRequest request = new LessonUpsertRequest(
        "고1 수학",
        null,
        LessonType.SUBSCRIPTION,
        null,
        PriceUnit.MONTH,
        "50만원/1개월",
        10,
        ScheduleType.CONSULTATION,
        null,
        null,
        null,
        null,
        null,
        null,
        "일정협의",
        LessonStatus.PUBLISHED);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("고정 가격");
  }

  @Test
  void create_throwsWhenPeriodScheduleHasNoDateRangeOrDuration() {
    // given
    LessonUpsertRequest request = new LessonUpsertRequest(
        "압구정고 시험대비반",
        null,
        LessonType.SHORT_TERM,
        100_000,
        PriceUnit.COURSE,
        "10만원/기간협의",
        12,
        ScheduleType.PERIOD,
        null,
        null,
        null,
        null,
        null,
        null,
        "기간형",
        LessonStatus.PUBLISHED);

    // when & then
    assertThatThrownBy(() -> service.create(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("날짜 범위 또는 기간");
  }

  @Test
  void update_checksLessonAcademyOwnerAndUpdatesFields() {
    // given
    LessonProduct lesson = lesson(academy());
    LessonUpsertRequest request = new LessonUpsertRequest(
        "수정 수업",
        "수정 설명",
        LessonType.SHORT_TERM,
        100_000,
        PriceUnit.COURSE,
        "10만원/코스",
        6,
        ScheduleType.PERIOD,
        null,
        null,
        LocalDate.of(2026, 6, 2),
        LocalDate.of(2026, 6, 11),
        null,
        null,
        "2026.06.02 ~ 2026.06.11",
        LessonStatus.HIDDEN);
    lesson.updateVisibility(false);
    ReflectionTestUtils.setField(lesson, "authoringRevision", 4L);
    when(lessonProductRepository.findForUpdateByLessonId(200L)).thenReturn(Optional.of(lesson));

    // when
    LessonResponse response = service.update(principal, 200L, request);

    // then
    assertThat(response.name()).isEqualTo("수정 수업");
    assertThat(response.status()).isEqualTo(LessonStatus.HIDDEN);
    assertThat(response.currentCount()).isEqualTo(2);
    assertThat(response.isVisible()).isFalse();
    assertThat(response.authoringRevision()).isEqualTo(5L);
    verify(authResolver).verifyOwnsAcademy(lesson.getAcademy());
    verify(lessonProductRepository, never()).findById(200L);
  }

  @Test
  void update_throwsNotFoundWhenLessonMissing() {
    // given
    when(lessonProductRepository.findForUpdateByLessonId(999L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.update(principal, 999L, weeklyRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void update_withEquivalentLegacyPayloadDoesNotIncrementRevision() {
    // given
    LessonProduct lesson = lesson(academy());
    ReflectionTestUtils.setField(lesson, "authoringRevision", 3L);
    LessonUpsertRequest equivalentRequest = new LessonUpsertRequest(
        "기존 수업",
        null,
        LessonType.SUBSCRIPTION,
        500_000,
        PriceUnit.MONTH,
        "50만원/1개월",
        15,
        ScheduleType.WEEKLY,
        2,
        Set.of(LessonWeekday.MONDAY),
        null,
        null,
        null,
        null,
        "주2회",
        LessonStatus.PUBLISHED);
    when(lessonProductRepository.findForUpdateByLessonId(200L)).thenReturn(Optional.of(lesson));

    // when
    LessonResponse response = service.update(principal, 200L, equivalentRequest);

    // then
    assertThat(response.authoringRevision()).isEqualTo(3L);
    assertThat(response.currentCount()).isEqualTo(2);
    assertThat(response.isVisible()).isTrue();
  }

  @Test
  void hide_usesLockedLookupAndIncrementsRevisionOnceWithoutChangingEnrollmentCount() {
    // given
    LessonProduct lesson = lesson(academy());
    ReflectionTestUtils.setField(lesson, "authoringRevision", 2L);
    when(lessonProductRepository.findForUpdateByLessonId(200L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.existsByLesson_LessonIdAndStatus(
        200L, LessonApplicationStatus.APPROVED)).thenReturn(false);

    // when
    service.hide(principal, 200L);

    // then
    assertThat(lesson.getStatus()).isEqualTo(LessonStatus.HIDDEN);
    assertThat(lesson.isVisible()).isFalse();
    assertThat(lesson.getCurrentCount()).isEqualTo(2);
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(3L);
    verify(lessonProductRepository, never()).findById(200L);
  }

  @Test
  void hide_withApprovedApplicationLeavesAuthoredStateUnchanged() {
    // given
    LessonProduct lesson = lesson(academy());
    ReflectionTestUtils.setField(lesson, "authoringRevision", 2L);
    when(lessonProductRepository.findForUpdateByLessonId(200L)).thenReturn(Optional.of(lesson));
    when(lessonApplicationRepository.existsByLesson_LessonIdAndStatus(
        200L, LessonApplicationStatus.APPROVED)).thenReturn(true);

    // when & then
    assertThatThrownBy(() -> service.hide(principal, 200L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PUBLISHED);
    assertThat(lesson.isVisible()).isTrue();
    assertThat(lesson.getCurrentCount()).isEqualTo(2);
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(2L);
  }

  private LessonUpsertRequest weeklyRequest() {
    return new LessonUpsertRequest(
        "중3 영어 내신반",
        "설명",
        LessonType.SUBSCRIPTION,
        500_000,
        PriceUnit.MONTH,
        "50만원/1개월",
        15,
        ScheduleType.WEEKLY,
        2,
        Set.of(LessonWeekday.MONDAY, LessonWeekday.WEDNESDAY),
        null,
        null,
        null,
        null,
        "주2회 [월,수]",
        LessonStatus.PUBLISHED);
  }

  private LessonProduct lesson(Academy academy) {
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy)
        .name("기존 수업")
        .lessonType(LessonType.SUBSCRIPTION)
        // 레거시 단건 API는 수강 대상 미지정을 계속 HIGH로 정규화한다
        // (LessonProductService.normalizeTargetGroup). 엔티티가 더 이상 기본값을 넣지 않으므로
        // 레거시 수업임을 픽스처에서 명시한다.
        .targetGroup(TargetGroup.HIGH)
        .priceAmount(500_000)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .currentCount(2)
        .scheduleType(ScheduleType.WEEKLY)
        .weeklyCount(2)
        .scheduleSummary("주2회")
        .status(LessonStatus.PUBLISHED)
        .weekdays(new HashSet<>(Set.of(LessonWeekday.MONDAY)))
        .build();
    ReflectionTestUtils.setField(lesson, "lessonId", 200L);
    return lesson;
  }

  private Academy academy() {
    User tutor = User.builder()
        .loginId("tutor1")
        .password("pw")
        .name("강사")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("test-academy")
        .name("테스트 Academy")
        .logoStorageKey("logo.jpg")
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .targetGroups(new HashSet<>(Set.of(TargetGroup.MIDDLE)))
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);
    return academy;
  }
}
