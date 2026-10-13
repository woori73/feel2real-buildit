package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

import java.util.List;

/** preflight 검수 결과 (Qoder 명세 4.4 계약). */
public record PreflightResponse(
    boolean passed,
    List<PreflightIssue> issues,
    List<PreflightIssue> warnings
) {

  public record PreflightIssue(
      String severity,
      String kind,
      String message,
      Integer page
  ) {
  }
}
