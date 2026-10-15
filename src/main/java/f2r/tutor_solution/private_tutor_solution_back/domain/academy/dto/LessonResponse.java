package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;

import java.time.LocalDate;
import java.util.Set;

public record LessonResponse(
    Long lessonId,
    String name,
    String description,
    @JsonInclude(JsonInclude.Include.NON_NULL) String schoolName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String gradeLabel,
    LessonType lessonType,
    TargetGroup targetGroup,
    Integer priceAmount,
    PriceUnit priceUnit,
    String priceSummary,
    Integer maxCapacity,
    Integer currentCount,
    ScheduleType scheduleType,
    Integer weeklyCount,
    Set<LessonWeekday> weekdays,
    LocalDate startDate,
    LocalDate endDate,
    Integer durationWeeks,
    Integer durationMonths,
    String scheduleSummary,
    LessonStatus status,
    boolean isVisible,
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
    /** 대표 미디어 종류. 컬럼이 아니라 이미지 key/영상 id에서 파생한다. 없으면 null. */
    @JsonInclude(JsonInclude.Include.NON_NULL) LessonMediaType mediaType,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long mediaVideoId,
    /** 대표 미디어와 독립된 수업 OT 영상 id. */
    @JsonInclude(JsonInclude.Include.NON_NULL) Long otVideoId,
    /**
     * 대표 이미지 storage key. <b>강사 응답에만</b> 담긴다.
     *
     * <p>공개 응답에 넣으면 저장소 key가 그대로 노출된다. 공개 페이지는
     * {@code /api/academies/{slug}/lessons/{id}/hero-image}로 바이트만 받아 간다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL) String mediaImageKey,
    @JsonInclude(JsonInclude.Include.NON_NULL) String creationKey,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long authoringRevision
) {
  public static LessonResponse from(LessonProduct lesson) {
    return from(lesson, true);
  }

  public static LessonResponse fromPublic(LessonProduct lesson) {
    return from(lesson, false);
  }

  private static LessonResponse from(LessonProduct lesson, boolean includeAuthoringMetadata) {
    return new LessonResponse(
        lesson.getLessonId(),
        lesson.getName(),
        lesson.getDescription(),
        includeAuthoringMetadata ? lesson.getSchoolName() : null,
        includeAuthoringMetadata ? lesson.getGradeLabel() : null,
        lesson.getLessonType(),
        lesson.getTargetGroup(),
        lesson.getPriceAmount(),
        lesson.getPriceUnit(),
        lesson.getPriceSummary(),
        lesson.getMaxCapacity(),
        lesson.getCurrentCount(),
        lesson.getScheduleType(),
        lesson.getWeeklyCount(),
        lesson.getWeekdays(),
        lesson.getStartDate(),
        lesson.getEndDate(),
        lesson.getDurationWeeks(),
        lesson.getDurationMonths(),
        lesson.getScheduleSummary(),
        lesson.getStatus(),
        lesson.isVisible(),
        lesson.getTabGuidanceLabel(),
        lesson.getTabShortName(),
        lesson.getKicker(),
        lesson.getHighlight(),
        lesson.getLearnerNeed(),
        lesson.getCoreDetailTitle(),
        lesson.getExplanation1(),
        lesson.getExplanation2(),
        lesson.getExplanation3(),
        lesson.getEffectiveTheme(),
        mediaTypeOf(lesson),
        lesson.getMediaVideoId(),
        lesson.getOtVideoId(),
        // 공개 응답에는 storage key를 싣지 않는다. 전용 이미지 엔드포인트로만 노출한다.
        includeAuthoringMetadata ? lesson.getMediaImageKey() : null,
        includeAuthoringMetadata ? lesson.getCreationKey() : null,
        includeAuthoringMetadata ? lesson.getEffectiveAuthoringRevision() : null);
  }

  /**
   * 대표 미디어 종류를 파생한다.
   *
   * <p>media_type 컬럼을 두지 않는 이유가 여기 있다 — 두 필드에서 항상 계산되므로 컬럼으로 만들면
   * 동기화 지점만 늘어난다. FK가 {@code ON DELETE SET NULL}이라 영상이 사라지면 자연히 "미디어 없음"
   * 으로 떨어지는데, 그게 맞는 동작이다.
   */
  private static LessonMediaType mediaTypeOf(LessonProduct lesson) {
    if (lesson.getMediaVideoId() != null) {
      return LessonMediaType.VIDEO;
    }
    return lesson.getMediaImageKey() != null ? LessonMediaType.IMAGE : null;
  }
}
