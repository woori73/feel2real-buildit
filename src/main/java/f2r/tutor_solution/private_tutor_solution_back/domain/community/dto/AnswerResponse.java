package f2r.tutor_solution.private_tutor_solution_back.domain.community.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.Answer;

import java.time.LocalDateTime;

/** 답변 1건. */
public record AnswerResponse(
    Long answerId,
    String contentHtml,
    String authorName,
    Long authorUserId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AnswerResponse of(Answer a) {
    return new AnswerResponse(
        a.getAnswerId(),
        a.getContentHtml(),
        a.getAuthor().getDisplayName(),
        a.getAuthor().getUserId(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }
}
