package f2r.tutor_solution.private_tutor_solution_back.domain.community.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;

import java.time.LocalDateTime;
import java.util.List;

/** 질문 상세 + 질문 댓글 + 답변 목록(각 답변의 댓글 포함). */
public record CommunityPostDetailResponse(
    Long postId,
    String boardType,
    Integer subjectId,
    String boardLabel,
    String title,
    String contentHtml,
    String authorName,
    Long authorUserId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long likeCount,
    boolean likedByMe,
    boolean likeAllowed,
    LocalDateTime likeAllowedUntil,
    List<AnswerResponse> answers) {

  public static CommunityPostDetailResponse of(CommunityPost p, long likeCount, boolean likedByMe,
      boolean likeAllowed, LocalDateTime likeAllowedUntil, List<AnswerResponse> answers) {
    Subject s = p.getSubject();
    return new CommunityPostDetailResponse(
        p.getPostId(),
        p.getBoardType().name(),
        s != null ? s.getSubjectId() : null,
        CommunityPostListResponse.boardLabel(p),
        p.getTitle(),
        p.getContentHtml(),
        p.getAuthor().getDisplayName(),
        p.getAuthor().getUserId(),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        likeCount,
        likedByMe,
        likeAllowed,
        likeAllowedUntil,
        answers);
  }
}
