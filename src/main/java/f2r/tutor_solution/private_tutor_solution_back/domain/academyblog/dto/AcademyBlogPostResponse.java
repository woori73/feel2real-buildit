package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.AcademyBlogPostStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AcademyBlogPostResponse(
    Long postId,
    String academySlug,
    String title,
    String slug,
    String summary,
    String contentJson,
    String contentHtml,
    String metaTitle,
    String metaDescription,
    AcademyBlogPostStatus status,
    boolean visible,
    LocalDateTime publishedAt,
    LocalDateTime updatedAt,
    String publicUrl,
    long likeCount,
    long recentLikeCount,
    boolean likedByMe,
    boolean likeAllowed,
    LocalDateTime likeAllowedUntil,
    List<AcademyBlogSourceResponse> sources
) {
  public static AcademyBlogPostResponse of(AcademyBlogPost post, List<AcademyBlogSourceResponse> sources) {
    return of(post, sources, 0, 0, false, false, null);
  }

  public static AcademyBlogPostResponse of(AcademyBlogPost post, List<AcademyBlogSourceResponse> sources,
      long likeCount, long recentLikeCount, boolean likedByMe, boolean likeAllowed, LocalDateTime likeAllowedUntil) {
    String academySlug = post.getAcademy().getSlug();
    return new AcademyBlogPostResponse(
        post.getPostId(),
        academySlug,
        post.getTitle(),
        post.getSlug(),
        post.getSummary(),
        post.getContentJson(),
        post.getContentHtml(),
        post.getMetaTitle(),
        post.getMetaDescription(),
        post.getStatus(),
        post.isVisible(),
        post.getPublishedAt(),
        post.getUpdatedAt(),
        "/academies/" + academySlug + "/posts/" + post.getSlug(),
        likeCount,
        recentLikeCount,
        likedByMe,
        likeAllowed,
        likeAllowedUntil,
        sources
    );
  }
}
