package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;

public record LessonCommunityPostDetailResponse(
    Long postId,
    Long lessonId,
    String lessonName,
    String academyName,
    Long categoryId,
    String categoryName,
    String postType,
    String title,
    String content,
    Long authorUserId,
    String authorName,
    boolean authorAdmin,
    boolean qnaPublic,
    boolean mine,
    boolean canHide,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<LessonCommunityAttachmentResponse> attachments,
    List<LessonCommunityCommentResponse> comments
) {
  public static LessonCommunityPostDetailResponse from(
      LessonCommunityPost p,
      Long currentUserId,
      boolean canHide,
      List<LessonCommunityAttachmentResponse> attachments,
      List<LessonCommunityCommentResponse> comments) {
    Long authorUserId = p.getAuthor().getUserId();
    return new LessonCommunityPostDetailResponse(
        p.getPostId(),
        p.getLesson().getLessonId(),
        p.getLesson().getName(),
        p.getLesson().getAcademy().getName(),
        p.getCategory() == null ? null : p.getCategory().getCategoryId(),
        p.getCategory() == null ? null : p.getCategory().getName(),
        p.getPostType().name(),
        p.getTitle(),
        p.getContent(),
        authorUserId,
        p.getAuthor().getDisplayName(),
        p.getAuthor().getRole() == Role.ADMIN,
        p.isQnaPublic(),
        authorUserId.equals(currentUserId),
        canHide,
        p.getCreatedAt(),
        p.getUpdatedAt(),
        attachments,
        comments);
  }
}
