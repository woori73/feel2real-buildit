package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;

import java.util.Set;

public record LessonSearchResponse(
    Long lessonId,
    String lessonName,
    String academyName,
    String academySlug,
    LessonType lessonType,
    TargetGroup targetGroup,
    Integer priceAmount,
    PriceUnit priceUnit,
    String priceSummary,
    Integer maxCapacity,
    Integer currentCount,
    Integer remainingCapacity,
    boolean full,
    ScheduleType scheduleType,
    Integer weeklyCount,
    Set<LessonWeekday> weekdays,
    String scheduleSummary
) {
  public static LessonSearchResponse from(LessonProduct lesson) {
    return new LessonSearchResponse(
        lesson.getLessonId(),
        lesson.getName(),
        lesson.getAcademy().getName(),
        lesson.getAcademy().getSlug(),
        lesson.getLessonType(),
        lesson.getTargetGroup(),
        lesson.getPriceAmount(),
        lesson.getPriceUnit(),
        lesson.getPriceSummary(),
        lesson.getMaxCapacity(),
        lesson.getCurrentCount(),
        lesson.remainingCapacity(),
        lesson.isFull(),
        lesson.getScheduleType(),
        lesson.getWeeklyCount(),
        lesson.getWeekdays(),
        lesson.getScheduleSummary());
  }
}
