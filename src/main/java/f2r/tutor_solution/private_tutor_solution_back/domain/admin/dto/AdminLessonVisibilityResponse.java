package f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonStatus;

public record AdminLessonVisibilityResponse(
    Long lessonId,
    String name,
    LessonStatus status,
    boolean isVisible,
    Integer currentCount,
    Integer maxCapacity,
    String priceSummary,
    String scheduleSummary
) {
  public static AdminLessonVisibilityResponse from(LessonProduct lesson) {
    return new AdminLessonVisibilityResponse(
        lesson.getLessonId(),
        lesson.getName(),
        lesson.getStatus(),
        lesson.isVisible(),
        lesson.getCurrentCount(),
        lesson.getMaxCapacity(),
        lesson.getPriceSummary(),
        lesson.getScheduleSummary());
  }
}
