package f2r.tutor_solution.private_tutor_solution_back.domain.community.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;

import java.time.LocalDateTime;

/** 목록 카드용 요약. board_type/과목 + 답변수/댓글수(질문+답변)/추천수. */
public record CommunityPostListResponse(
    Long postId,
    String boardType,
    Integer subjectId,
    String boardLabel,
    String title,
    String authorName,
    Long authorUserId,
    LocalDateTime createdAt,
    long answerCount,
    long likeCount,
    long recentLikeCount,
    boolean likedByMe,
    boolean likeAllowed,
    LocalDateTime likeAllowedUntil) {

  public static CommunityPostListResponse of(CommunityPost p, long answerCount,
      long likeCount, long recentLikeCount, boolean likedByMe, boolean likeAllowed, LocalDateTime likeAllowedUntil) {
    Subject s = p.getSubject();
    return new CommunityPostListResponse(
        p.getPostId(),
        p.getBoardType().name(),
        s != null ? s.getSubjectId() : null,
        boardLabel(p),
        p.getTitle(),
        p.getAuthor().getDisplayName(),
        p.getAuthor().getUserId(),
        p.getCreatedAt(),
        answerCount,
        likeCount,
        recentLikeCount,
        likedByMe,
        likeAllowed,
        likeAllowedUntil);
  }

  /** UI 라벨: 과목 게시판이면 과목 한글명, 아니면 board 라벨(자유게시판/Q&A). */
  static String boardLabel(CommunityPost p) {
    if (p.getBoardType() == BoardType.SUBJECT && p.getSubject() != null) return p.getSubject().getNameKo();
    return p.getBoardType().label();
  }
}
