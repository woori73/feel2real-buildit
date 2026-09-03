package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadVariantDetail;

public record QuestionThreadVariantResponse(
    Long variantId,
    Long postId,
    String questionText,
    String questionType,
    String answer,
    String explanation,
    String sourceSentence,
    String sourceQuestion,
    String difficulty) {

  public static QuestionThreadVariantResponse from(QuestionThreadVariantDetail v) {
    return new QuestionThreadVariantResponse(
        v.getVariantId(),
        v.getPost().getPostId(),
        v.getQuestionText(),
        v.getQuestionType(),
        v.getAnswer(),
        v.getExplanation(),
        v.getSourceSentence(),
        v.getSourceQuestion(),
        v.getDifficulty());
  }
}
