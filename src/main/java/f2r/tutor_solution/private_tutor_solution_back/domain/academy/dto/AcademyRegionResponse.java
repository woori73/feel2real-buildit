package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyRegion;

public record AcademyRegionResponse(
    String city,
    String district,
    String displayName
) {
  public static AcademyRegionResponse from(AcademyRegion region) {
    return new AcademyRegionResponse(region.getCity(), region.getDistrict(), region.getDisplayName());
  }
}
