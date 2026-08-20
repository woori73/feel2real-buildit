package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LessonProductLegacyRegressionContractTest {

  @Test
  void updateDetails_preservesEnrollmentCount() {
    // given
    LessonProduct lesson = existingLesson();

    // when
    updateAuthoredDetails(lesson);

    // then
    assertThat(lesson.getCurrentCount()).isEqualTo(4);
  }

  @Test
  void updateDetails_preservesAdminVisibility() {
    // given
    LessonProduct lesson = existingLesson();
    lesson.updateVisibility(false);

    // when
    updateAuthoredDetails(lesson);

    // then
    assertThat(lesson.isVisible()).isFalse();
  }

  @Test
  void hideFromTutorManagement_preservesEnrollmentCount() {
    // given
    LessonProduct lesson = existingLesson();

    // when
    lesson.hideFromTutorManagement();

    // then
    assertThat(lesson.getCurrentCount()).isEqualTo(4);
  }

  @Test
  void hideFromTutorManagement_setsHiddenAndInvisible() {
    // given
    LessonProduct lesson = existingLesson();

    // when
    lesson.hideFromTutorManagement();

    // then
    assertThat(lesson.getStatus()).isEqualTo(LessonStatus.HIDDEN);
    assertThat(lesson.isVisible()).isFalse();
  }

  @Test
  void approveOneSeat_preservesAuthoringRevision() {
    // given
    LessonProduct lesson = existingLesson();

    // when
    lesson.approveOneSeat();

    // then
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(7L);
  }

  @Test
  void cancelOneSeat_preservesAuthoringRevision() {
    // given
    LessonProduct lesson = existingLesson();

    // when
    lesson.cancelOneSeat();

    // then
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(7L);
  }

  private LessonProduct existingLesson() {
    return LessonProduct.builder()
        .name("기존 수업")
        .description("기존 설명")
        .lessonType(LessonType.SUBSCRIPTION)
        .targetGroup(TargetGroup.HIGH)
        .priceAmount(500_000)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .currentCount(4)
        .scheduleType(ScheduleType.WEEKLY)
        .weeklyCount(2)
        .scheduleSummary("주2회")
        .status(LessonStatus.PUBLISHED)
        .visible(true)
        .authoringRevision(7L)
        .weekdays(new HashSet<>(Set.of(LessonWeekday.MONDAY)))
        .build();
  }

  private void updateAuthoredDetails(LessonProduct lesson) {
    lesson.updateDetails(
        "수정 수업",
        "수정 설명",
        LessonType.SHORT_TERM,
        TargetGroup.MIDDLE,
        120_000,
        PriceUnit.COURSE,
        "12만원/코스",
        8,
        ScheduleType.PERIOD,
        null,
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        null,
        null,
        "2026.08.01 ~ 2026.08.31",
        LessonStatus.DRAFT,
        Set.of());
  }
}
