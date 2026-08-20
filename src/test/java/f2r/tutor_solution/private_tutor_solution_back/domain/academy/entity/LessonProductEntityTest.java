package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonProductEntityTest {

  @Test
  void lessonProductBuilder_defaultsCountAndStatus_whenValuesAreNull() {
    // given
    Integer currentCount = null;
    LessonStatus status = null;

    // when
    LessonProduct lesson = LessonProduct.builder()
        .name("중3 영어 내신반")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceAmount(500000)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .currentCount(currentCount)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회 월/수/금")
        .status(status)
        .build();

    // then
    assertThat(lesson.getCurrentCount()).isZero();
    assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PUBLISHED);
    // 수강 대상은 더 이상 HIGH로 정규화하지 않는다. 편집기가 받지 않는 값이라
    // 기본값을 넣으면 강사가 지정한 적 없는 대상이 공개 카드·검색 필터에 노출된다.
    assertThat(lesson.getTargetGroup()).isNull();
  }

  @Test
  void lessonProductBuilder_preservesNullableAuthoringFieldsAndUsesEffectiveLegacyDefaults() {
    // given & when
    LessonProduct lesson = LessonProduct.builder()
        .name("레거시 수업")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회 월/수/금")
        .tabGuidanceLabel(null)
        .tabShortName(null)
        .kicker(null)
        .highlight(null)
        .learnerNeed(null)
        .coreDetailTitle(null)
        .explanation1(null)
        .explanation2(null)
        .explanation3(null)
        .theme(null)
        .creationKey(null)
        .authoringRevision(null)
        .build();

    // then
    assertThat(lesson.getTabGuidanceLabel()).isNull();
    assertThat(lesson.getTabShortName()).isNull();
    assertThat(lesson.getKicker()).isNull();
    assertThat(lesson.getHighlight()).isNull();
    assertThat(lesson.getLearnerNeed()).isNull();
    assertThat(lesson.getCoreDetailTitle()).isNull();
    assertThat(lesson.getExplanation1()).isNull();
    assertThat(lesson.getExplanation2()).isNull();
    assertThat(lesson.getExplanation3()).isNull();
    assertThat(lesson.getTheme()).isNull();
    assertThat(lesson.getCreationKey()).isNull();
    assertThat(lesson.getAuthoringRevision()).isNull();
    assertThat(lesson.getEffectiveTheme()).isEqualTo(LessonTheme.NAVY);
    assertThat(lesson.getEffectiveAuthoringRevision()).isZero();
  }

  @Test
  void lessonTheme_supportsEveryStableThemeId() {
    assertThat(LessonTheme.values()).containsExactly(
        LessonTheme.NAVY,
        LessonTheme.TEAL,
        LessonTheme.PURPLE,
        LessonTheme.ORANGE,
        LessonTheme.SKY,
        LessonTheme.PINK,
        LessonTheme.GREEN);
  }

  @Test
  void effectiveTheme_roundTripsEveryPersistedTheme() {
    for (LessonTheme theme : LessonTheme.values()) {
      LessonProduct lesson = LessonProduct.builder()
          .name("테마 수업")
          .lessonType(LessonType.SUBSCRIPTION)
          .priceUnit(PriceUnit.MONTH)
          .priceSummary("50만원/1개월")
          .maxCapacity(15)
          .scheduleType(ScheduleType.WEEKLY)
          .scheduleSummary("주3회 월/수/금")
          .theme(theme)
          .build();

      assertThat(lesson.getTheme()).isEqualTo(theme);
      assertThat(lesson.getEffectiveTheme()).isEqualTo(theme);
    }
  }

  @Test
  void incrementAuthoringRevision_startsAtOneForLegacyNullAndIncrementsOncePerCall() {
    // given
    LessonProduct lesson = lessonWithCapacity(2, 1);

    // when
    lesson.incrementAuthoringRevision();

    // then
    assertThat(lesson.getAuthoringRevision()).isEqualTo(1L);
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(1L);

    // when
    lesson.incrementAuthoringRevision();

    // then
    assertThat(lesson.getAuthoringRevision()).isEqualTo(2L);
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(2L);
  }

  @Test
  void enrollmentAndAdminVisibilityMutations_doNotIncrementAuthoringRevision() {
    // given
    LessonProduct lesson = lessonWithCapacity(2, 1);

    // when
    lesson.approveOneSeat();
    lesson.cancelOneSeat();
    lesson.updateVisibility(false);

    // then
    assertThat(lesson.getAuthoringRevision()).isNull();
    assertThat(lesson.getEffectiveAuthoringRevision()).isZero();
  }

  @Test
  void updateAuthoringDetails_incrementsOnceOnlyWhenCanonicalStateChanges() {
    // given
    LessonProduct lesson = authoredLesson();

    // when
    boolean unchanged = updateAuthoringDetails(lesson, "성적 향상 집중");

    // then
    assertThat(unchanged).isFalse();
    assertThat(lesson.getEffectiveAuthoringRevision()).isZero();

    // when
    boolean changed = updateAuthoringDetails(lesson, "내신과 수능 동시 대비");

    // then
    assertThat(changed).isTrue();
    assertThat(lesson.getHighlight()).isEqualTo("내신과 수능 동시 대비");
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(1L);
  }

  @Test
  void hideFromTutorManagement_incrementsRevisionOnlyForFirstAuthoredStatusChange() {
    // given
    LessonProduct lesson = authoredLesson();

    // when
    lesson.hideFromTutorManagement();
    lesson.hideFromTutorManagement();

    // then
    assertThat(lesson.getStatus()).isEqualTo(LessonStatus.HIDDEN);
    assertThat(lesson.isVisible()).isFalse();
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(1L);
  }


  @Test
  void approveOneSeat_incrementsCurrentCount_whenCapacityRemains() {
    // given
    LessonProduct lesson = lessonWithCapacity(2, 1);

    // when
    lesson.approveOneSeat();

    // then
    assertThat(lesson.getCurrentCount()).isEqualTo(2);
    assertThat(lesson.isFull()).isTrue();
    assertThat(lesson.remainingCapacity()).isZero();
  }

  @Test
  void approveOneSeat_throws_whenCapacityIsFull() {
    // given
    LessonProduct lesson = lessonWithCapacity(2, 2);

    // when & then
    assertThatThrownBy(lesson::approveOneSeat)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("정원");
    assertThat(lesson.getCurrentCount()).isEqualTo(2);
  }

  @Test
  void cancelOneSeat_decrementsCurrentCount_whenCurrentCountIsPositive() {
    // given
    LessonProduct lesson = lessonWithCapacity(2, 1);

    // when
    lesson.cancelOneSeat();

    // then
    assertThat(lesson.getCurrentCount()).isZero();
  }

  @Test
  void cancelOneSeat_throws_whenCurrentCountIsZero() {
    // given
    LessonProduct lesson = lessonWithCapacity(2, 0);

    // when & then
    assertThatThrownBy(lesson::cancelOneSeat)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("취소");
    assertThat(lesson.getCurrentCount()).isZero();
  }

  @Test
  void isFull_returnsFalse_whenMaxCapacityIsNull() {
    // given
    LessonProduct lesson = lessonWithNullCapacity(3);

    // when
    boolean full = lesson.isFull();

    // then
    assertThat(full).isFalse();
  }

  @Test
  void approveOneSeat_incrementsWithoutLimit_whenMaxCapacityIsNull() {
    // given
    LessonProduct lesson = lessonWithNullCapacity(3);

    // when
    lesson.approveOneSeat();

    // then
    assertThat(lesson.getCurrentCount()).isEqualTo(4);
    assertThat(lesson.isFull()).isFalse();
  }

  @Test
  void remainingCapacity_returnsNull_whenMaxCapacityIsNull() {
    // given
    LessonProduct lesson = lessonWithNullCapacity(3);

    // when
    Integer remaining = lesson.remainingCapacity();

    // then
    assertThat(remaining).isNull();
  }

  @Test
  void lessonProductBuilder_keepsNullAuthoringFields_whenEditorSendsNothing() {
    // given
    // 예시 HTML이 요구하지 않는 값들은 편집기가 아예 보내지 않는다.

    // when
    LessonProduct lesson = LessonProduct.builder()
        .name("중3 영어 내신반")
        .build();

    // then
    assertThat(lesson.getLessonType()).isNull();
    assertThat(lesson.getTargetGroup()).isNull();
    assertThat(lesson.getPriceUnit()).isNull();
    assertThat(lesson.getPriceSummary()).isNull();
    assertThat(lesson.getMaxCapacity()).isNull();
    assertThat(lesson.getScheduleType()).isNull();
    assertThat(lesson.getScheduleSummary()).isNull();
    assertThat(lesson.isFull()).isFalse();
    assertThat(lesson.remainingCapacity()).isNull();
  }

  private LessonProduct lessonWithNullCapacity(int currentCount) {
    return LessonProduct.builder()
        .name("중3 영어 내신반")
        .maxCapacity(null)
        .currentCount(currentCount)
        .build();
  }

  private LessonProduct lessonWithCapacity(int maxCapacity, int currentCount) {
    return LessonProduct.builder()
        .name("중3 영어 내신반")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(maxCapacity)
        .currentCount(currentCount)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회 월/수/금")
        .build();
  }

  private LessonProduct authoredLesson() {
    return LessonProduct.builder()
        .name("중3 영어 내신반")
        .description("학교별 기출 중심 수업")
        .lessonType(LessonType.SUBSCRIPTION)
        .targetGroup(TargetGroup.MIDDLE)
        .priceAmount(500000)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .currentCount(4)
        .scheduleType(ScheduleType.WEEKLY)
        .weeklyCount(3)
        .scheduleSummary("주3회 월/수/금")
        .status(LessonStatus.PUBLISHED)
        .visible(true)
        .weekdays(new HashSet<>(
            Set.of(LessonWeekday.MONDAY, LessonWeekday.WEDNESDAY, LessonWeekday.FRIDAY)))
        .tabGuidanceLabel("이런 학생에게 추천")
        .tabShortName("중3 내신")
        .kicker("중3 영어")
        .highlight("성적 향상 집중")
        .learnerNeed("내신 등급을 올리고 싶은 학생")
        .coreDetailTitle("학교별 시험 대비")
        .explanation1("기출 분석")
        .explanation2("서술형 첨삭")
        .explanation3("오답 반복")
        .theme(LessonTheme.TEAL)
        .creationKey("5fd2e8f8-37f0-4f8d-a56c-fd42ed54c553")
        .build();
  }

  private boolean updateAuthoringDetails(LessonProduct lesson, String highlight) {
    return lesson.updateAuthoringDetails(
        lesson.getName(),
        lesson.getDescription(),
        lesson.getSchoolName(),
        lesson.getGradeLabel(),
        lesson.getLessonType(),
        lesson.getTargetGroup(),
        lesson.getPriceAmount(),
        lesson.getPriceUnit(),
        lesson.getPriceSummary(),
        lesson.getMaxCapacity(),
        lesson.getScheduleType(),
        lesson.getWeeklyCount(),
        lesson.getStartDate(),
        lesson.getEndDate(),
        lesson.getDurationWeeks(),
        lesson.getDurationMonths(),
        lesson.getScheduleSummary(),
        lesson.getStatus(),
        lesson.getWeekdays(),
        lesson.getTabGuidanceLabel(),
        lesson.getTabShortName(),
        lesson.getKicker(),
        highlight,
        lesson.getLearnerNeed(),
        lesson.getCoreDetailTitle(),
        lesson.getExplanation1(),
        lesson.getExplanation2(),
        lesson.getExplanation3(),
        lesson.getTheme(),
        lesson.getMediaImageKey(),
        lesson.getMediaVideoId(),
        lesson.getOtVideoId());
  }

}
