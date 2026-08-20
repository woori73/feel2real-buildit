package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentStatus;

public record BetaCommentStatusUpdateRequest(BetaCommentStatus status) {

  public BetaCommentStatusUpdateRequest {
    if (status == null) {
      throw new IllegalArgumentException("status는 필수입니다.");
    }
  }
}
