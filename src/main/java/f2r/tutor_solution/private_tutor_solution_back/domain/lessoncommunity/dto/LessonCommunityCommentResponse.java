package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityComment;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;

public record LessonCommunityCommentResponse(
    Long commentId,
    Long authorUserId,
    String authorName,
    String content,
    boolean authorAdmin,
    boolean mine,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<LessonCommunityAttachmentResponse> attachments
) {
  public static LessonCommunityCommentResponse from(
      LessonCommunityComment c,
      Long currentUserId,
      List<LessonCommunityAttachmentResponse> attachments) {
    Long authorUserId = c.getAuthor().getUserId();
    return new LessonCommunityCommentResponse(
        c.getCommentId(),
        authorUserId,
        c.getAuthor().getDisplayName(),
        c.getContent(),
        c.getAuthor().getRole() == Role.ADMIN,
        authorUserId.equals(currentUserId),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        attachments);
  }
}
