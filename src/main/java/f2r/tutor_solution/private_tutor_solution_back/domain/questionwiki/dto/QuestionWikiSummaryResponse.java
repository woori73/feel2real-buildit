package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWiki;

/** 문항별 네비게이션 카드. */
public record QuestionWikiSummaryResponse(
    Long questionWikiId,
    String subject,
    int grade,
    int examYear,
    String examLabel,
    int questionNumber,
    String passageGroupKey,
    String title,
    String displayContext,
    long threadCount) {

  public static QuestionWikiSummaryResponse of(QuestionWiki q, long threadCount) {
    return new QuestionWikiSummaryResponse(
        q.getQuestionWikiId(),
        q.getSubject(),
        q.getGrade(),
        q.getExamYear(),
        q.getExamLabel(),
        q.getQuestionNumber(),
        q.getPassageGroupKey(),
        q.getTitle(),
        q.displayContext(),
        threadCount);
  }
}
