package f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto;

public record LandingVideoVisibilityRequest(Boolean visible) {

  public LandingVideoVisibilityRequest {
    if (visible == null) {
      throw new IllegalArgumentException("visible은 필수입니다.");
    }
  }
}
