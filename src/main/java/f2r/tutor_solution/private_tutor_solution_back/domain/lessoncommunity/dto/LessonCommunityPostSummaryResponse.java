package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;

import java.time.LocalDateTime;

public record LessonCommunityPostSummaryResponse(
    Long postId,
    Long lessonId,
    String lessonName,
    Long categoryId,
    String categoryName,
    String postType,
    String title,
    String authorName,
    boolean authorAdmin,
    boolean qnaPublic,
    boolean mine,
    long commentCount,
    long attachmentCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
  public static LessonCommunityPostSummaryResponse from(
      LessonCommunityPost p, Long currentUserId, long commentCount, long attachmentCount) {
    return new LessonCommunityPostSummaryResponse(
        p.getPostId(),
        p.getLesson().getLessonId(),
        p.getLesson().getName(),
        p.getCategory() == null ? null : p.getCategory().getCategoryId(),
        p.getCategory() == null ? null : p.getCategory().getName(),
        p.getPostType().name(),
        p.getTitle(),
        p.getAuthor().getDisplayName(),
        p.getAuthor().getRole() == Role.ADMIN,
        p.isQnaPublic(),
        p.getAuthor().getUserId().equals(currentUserId),
        commentCount,
        attachmentCount,
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
