package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.PriceUnit;

/** LMS 강좌 관리 표가 사용하는 최소 allowlist 응답. */
public record LmsManageLessonResponse(
    Long lessonId,
    String schoolName,
    String gradeLabel,
    String name,
    Integer priceAmount,
    PriceUnit priceUnit,
    String description
) {
  public static LmsManageLessonResponse from(LessonProduct lesson) {
    return new LmsManageLessonResponse(
        lesson.getLessonId(),
        lesson.getSchoolName(),
        lesson.getGradeLabel(),
        lesson.getName(),
        lesson.getPriceAmount(),
        lesson.getPriceUnit(),
        lesson.getDescription());
  }
}
