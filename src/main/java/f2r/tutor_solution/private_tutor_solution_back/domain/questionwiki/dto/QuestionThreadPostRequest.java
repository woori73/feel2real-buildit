package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadDataKind;

/**
 * THREAD 글 작성/수정 요청.
 *
 * <p>{@code dataKind == GENERATED_PRACTICE} 이면 {@code variant} 필수,
 * {@code dataKind == EXAM_EVIDENCE} 이면 {@code examEvidence} 필수(서비스에서 검증).
 * 답글 작성 시 {@code parentPostId} 를 채운다(경로로도 받을 수 있음).
 */
public record QuestionThreadPostRequest(
    QuestionThreadCategory category,
    QuestionThreadDataKind dataKind,
    String title,
    String content,
    Long parentPostId,
    Variant variant,
    ExamEvidence examEvidence) {

  public record Variant(
      String questionText,
      String questionType,
      String answer,
      String explanation,
      String sourceSentence,
      String sourceQuestion,
      String difficulty) {}

  public record ExamEvidence(
      String schoolName,
      String gradeLabel,
      String examPeriod,
      String relatedSentence,
      String examQuestionType,
      String examPoint,
      String description) {}
}
