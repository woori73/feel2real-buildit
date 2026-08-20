package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaComment;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentHighlight;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentStatus;
import java.time.LocalDateTime;

/** 어드민 모더레이션 목록의 항목 1건. */
public record BetaCommentAdminResponse(
    Long id,
    String nickname,
    String participantType,
    String participantLabel,
    String comment,
    BetaCommentStatus status,
    BetaCommentHighlight highlightLabel,
    String highlightText,
    LocalDateTime createdAt) {

  public static BetaCommentAdminResponse from(BetaComment comment) {
    BetaCommentHighlight highlight = comment.getHighlightLabel();
    return new BetaCommentAdminResponse(
        comment.getId(),
        comment.getNickname(),
        comment.getParticipantType().name(),
        comment.getParticipantType().getLabel(),
        comment.getComment(),
        comment.getStatus(),
        highlight,
        highlight == null ? null : highlight.getLabel(),
        comment.getCreatedAt());
  }
}
