package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;

import java.time.LocalDate;
import java.util.Set;

public record LessonUpsertRequest(
    String name,
    String description,
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
    LessonStatus status
) {
  public LessonUpsertRequest(
      String name,
      String description,
      LessonType lessonType,
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
      LessonStatus status) {
    this(name, description, lessonType, TargetGroup.HIGH, priceAmount, priceUnit, priceSummary,
        maxCapacity, scheduleType, weeklyCount, weekdays, startDate, endDate,
        durationWeeks, durationMonths, scheduleSummary, status);
  }
}
