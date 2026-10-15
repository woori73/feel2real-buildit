package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityPost;

import java.time.LocalDateTime;

public record AdminLessonCommunitySummaryResponse(
    Long lessonId,
    String lessonName,
    Long academyId,
    String academyName,
    String academySlug,
    Long tutorUserId,
    String tutorName,
    boolean academyVisible,
    boolean lessonVisible,
    String lessonStatus,
    long postCount,
    long commentCount,
    Long latestPostId,
    String latestPostTitle,
    String latestPostType,
    String latestAuthorName,
    LocalDateTime latestPostCreatedAt
) {
  public static AdminLessonCommunitySummaryResponse of(
      LessonProduct lesson,
      long postCount,
      long commentCount,
      LessonCommunityPost latestPost) {
    var academy = lesson.getAcademy();
    var tutor = academy.getTutor();
    return new AdminLessonCommunitySummaryResponse(
        lesson.getLessonId(),
        lesson.getName(),
        academy.getAcademyId(),
        academy.getName(),
        academy.getSlug(),
        tutor.getUserId(),
        tutor.getDisplayName(),
        academy.isVisible(),
        lesson.isVisible(),
        lesson.getStatus().name(),
        postCount,
        commentCount,
        latestPost == null ? null : latestPost.getPostId(),
        latestPost == null ? null : latestPost.getTitle(),
        latestPost == null ? null : latestPost.getPostType().name(),
        latestPost == null ? null : latestPost.getAuthor().getDisplayName(),
        latestPost == null ? null : latestPost.getCreatedAt());
  }
}
