package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;

import java.util.List;
import java.util.Set;

public record AcademyListResponse(
    Long academyId,
    String slug,
    String name,
    boolean isVisible,
    String logoUrl,
    List<AcademyResponse.SubjectSummary> subjects,
    Set<TargetGroup> targetGroups,
    List<AcademyRegionResponse> regions
) {
  public static AcademyListResponse of(
      Academy academy,
      List<AcademyResponse.SubjectSummary> subjects,
      List<AcademyRegionResponse> regions) {
    return new AcademyListResponse(
        academy.getAcademyId(),
        academy.getSlug(),
        academy.getName(),
        academy.isVisible(),
        "/api/academies/" + academy.getSlug() + "/logo",
        subjects,
        TargetGroup.canonicalOrder(academy.getTargetGroups()),
        regions);
  }
}
