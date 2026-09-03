package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadExamEvidence;

public record QuestionThreadExamEvidenceResponse(
    Long evidenceId,
    Long postId,
    String schoolName,
    String gradeLabel,
    String examPeriod,
    String relatedSentence,
    String examQuestionType,
    String examPoint,
    String description,
    Long examId) {

  public static QuestionThreadExamEvidenceResponse from(QuestionThreadExamEvidence e) {
    return new QuestionThreadExamEvidenceResponse(
        e.getEvidenceId(),
        e.getPost().getPostId(),
        e.getSchoolName(),
        e.getGradeLabel(),
        e.getExamPeriod(),
        e.getRelatedSentence(),
        e.getExamQuestionType(),
        e.getExamPoint(),
        e.getDescription(),
        e.getExamId());
  }
}
