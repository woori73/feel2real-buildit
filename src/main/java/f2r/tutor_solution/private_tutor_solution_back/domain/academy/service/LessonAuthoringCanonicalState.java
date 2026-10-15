package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonCollectionItemRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;

import java.time.LocalDate;
import java.util.Set;

record LessonAuthoringCanonicalState(
    String name,
    String description,
    String schoolName,
    String gradeLabel,
    LessonType lessonType,
    TargetGroup targetGroup,
    Integer priceAmount,
    PriceUnit priceUnit,
    String priceSummary,
    Integer maxCapacity,
    ScheduleType scheduleType,
    Integer weeklyCount,
    Set<LessonWeekday> weekdays,
    LocalDate startDate,
    LocalDate endDate,
    Integer durationWeeks,
    Integer durationMonths,
    String scheduleSummary,
    LessonStatus status,
    String tabGuidanceLabel,
    String tabShortName,
    String kicker,
    String highlight,
    String learnerNeed,
    String coreDetailTitle,
    String explanation1,
    String explanation2,
    String explanation3,
    LessonTheme theme,
    String mediaImageKey,
    Long mediaVideoId,
    Long otVideoId
) {

  static LessonAuthoringCanonicalState from(LessonCollectionItemRequest item) {
    return new LessonAuthoringCanonicalState(
        requiredTrimmed(item.name(), "수업명"),
        trimToNull(item.description()),
        trimToNull(item.schoolName()),
        trimToNull(item.gradeLabel()),
        // 편집기는 예시 HTML 기준 필드만 보낸다. 수업 유형·대상·가격·일정은 비어 올 수 있고,
        // 기본값으로 정규화하면 강사가 지정한 적 없는 값이 공개 카드·검색에 노출된다.
        item.lessonType(),
        item.targetGroup(),
        item.priceAmount(),
        item.priceUnit(),
        trimToNull(item.priceSummary()),
        item.maxCapacity(),
        item.scheduleType(),
        item.weeklyCount(),
        immutableWeekdays(item.weekdays()),
        item.startDate(),
        item.endDate(),
        item.durationWeeks(),
        item.durationMonths(),
        trimToNull(item.scheduleSummary()),
        // 편집기가 공개 상태를 받지 않으므로 null은 PUBLISHED로 본다(엔티티 정규화와 동일).
        normalizeEditableStatus(item.status()),
        trimToNull(item.tabGuidanceLabel()),
        trimToNull(item.tabShortName()),
        trimToNull(item.kicker()),
        trimToNull(item.highlight()),
        trimToNull(item.learnerNeed()),
        trimToNull(item.coreDetailTitle()),
        trimToNull(item.explanation1()),
        trimToNull(item.explanation2()),
        trimToNull(item.explanation3()),
        item.theme() == null ? LessonTheme.NAVY : item.theme(),
        // 대표 미디어는 이 canonical state를 반드시 통과해야 한다. 빠지면 미디어만 바꾼 저장이
        // equivalent==true로 판정돼 replayed로 분류되고 변경이 조용히 버려진다.
        trimToNull(item.mediaImageKey()),
        item.mediaVideoId(),
        item.otVideoId());
  }

  static LessonAuthoringCanonicalState from(LessonProduct lesson) {
    return new LessonAuthoringCanonicalState(
        requiredTrimmed(lesson.getName(), "수업명"),
        trimToNull(lesson.getDescription()),
        trimToNull(lesson.getSchoolName()),
        trimToNull(lesson.getGradeLabel()),
        lesson.getLessonType(),
        lesson.getTargetGroup(),
        lesson.getPriceAmount(),
        lesson.getPriceUnit(),
        trimToNull(lesson.getPriceSummary()),
        lesson.getMaxCapacity(),
        lesson.getScheduleType(),
        lesson.getWeeklyCount(),
        immutableWeekdays(lesson.getWeekdays()),
        lesson.getStartDate(),
        lesson.getEndDate(),
        lesson.getDurationWeeks(),
        lesson.getDurationMonths(),
        trimToNull(lesson.getScheduleSummary()),
        lesson.getStatus(),
        trimToNull(lesson.getTabGuidanceLabel()),
        trimToNull(lesson.getTabShortName()),
        trimToNull(lesson.getKicker()),
        trimToNull(lesson.getHighlight()),
        trimToNull(lesson.getLearnerNeed()),
        trimToNull(lesson.getCoreDetailTitle()),
        trimToNull(lesson.getExplanation1()),
        trimToNull(lesson.getExplanation2()),
        trimToNull(lesson.getExplanation3()),
        lesson.getEffectiveTheme(),
        trimToNull(lesson.getMediaImageKey()),
        lesson.getMediaVideoId(),
        lesson.getOtVideoId());
  }

  boolean applyTo(LessonProduct lesson) {
    return lesson.updateAuthoringDetails(
        name,
        description,
        schoolName,
        gradeLabel,
        lessonType,
        targetGroup,
        priceAmount,
        priceUnit,
        priceSummary,
        maxCapacity,
        scheduleType,
        weeklyCount,
        startDate,
        endDate,
        durationWeeks,
        durationMonths,
        scheduleSummary,
        status,
        weekdays,
        tabGuidanceLabel,
        tabShortName,
        kicker,
        highlight,
        learnerNeed,
        coreDetailTitle,
        explanation1,
        explanation2,
        explanation3,
        theme,
        mediaImageKey,
        mediaVideoId,
        otVideoId);
  }

  private static Set<LessonWeekday> immutableWeekdays(Set<LessonWeekday> weekdays) {
    return weekdays == null ? Set.of() : Set.copyOf(weekdays);
  }

  private static LessonStatus normalizeEditableStatus(LessonStatus status) {
    if (status == null) {
      return LessonStatus.PUBLISHED;
    }
    if (status != LessonStatus.DRAFT && status != LessonStatus.PUBLISHED) {
      throw new IllegalArgumentException("수업 상태는 DRAFT 또는 PUBLISHED만 가능합니다.");
    }
    return status;
  }

  private static <T> T require(T value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
    }
    return value;
  }

  private static String requiredTrimmed(String value, String fieldName) {
    String normalized = trimToNull(value);
    if (normalized == null) {
      throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
    }
    return normalized;
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
