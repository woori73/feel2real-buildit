package f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVisitStatisticsResponse;

import java.util.List;

public record AdminAcademyVisibilityResponse(
    Long academyId,
    String slug,
    String name,
    AcademyStatus status,
    boolean isVisible,
    Long tutorUserId,
    String tutorLoginId,
    String tutorName,
    List<AdminLessonVisibilityResponse> lessons,
    AcademyVisitStatisticsResponse visitStatistics
) {
  public static AdminAcademyVisibilityResponse of(
      Academy academy,
      List<AdminLessonVisibilityResponse> lessons,
      AcademyVisitStatisticsResponse visitStatistics) {
    return new AdminAcademyVisibilityResponse(
        academy.getAcademyId(),
        academy.getSlug(),
        academy.getName(),
        academy.getStatus(),
        academy.isVisible(),
        academy.getTutor().getUserId(),
        academy.getTutor().getLoginId(),
        academy.getTutor().getName(),
        lessons,
        visitStatistics);
  }
}
