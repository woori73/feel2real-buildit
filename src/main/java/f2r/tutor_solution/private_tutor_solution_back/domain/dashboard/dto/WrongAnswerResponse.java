package f2r.tutor_solution.private_tutor_solution_back.domain.dashboard.dto;

import java.util.List;

/**
 * 한 대화에 첨부된 시험지(들)에서 학생이 틀린 문제 목록 — 오답노트.
 *
 * <p>radar(카테고리별 정답률)와 보완 관계: radar는 약점 카테고리 파악용, 본 응답은
 * "어느 문제를 어떻게 틀렸는가"를 시험 단위로 묶어 보여준다.
 *
 * <p>첨부 시험지가 없거나 오답이 없으면 {@code exams}가 빈 리스트로 반환된다.
 * 프론트는 카테고리 필터를 자체적으로 적용한다 (응답은 그룹핑하지 않음).
 */
public record WrongAnswerResponse(List<ExamGroup> exams) {

  public record ExamGroup(
      Long examId,
      String examDisplayName,
      int wrongCount,
      int totalCount,
      List<WrongQuestion> questions) {
  }

  /**
   * @param choices       객관식 보기 JSON 문자열 (DB에 저장된 원형). 단답형이면 null. 프론트가 JSON.parse.
   * @param categoryNameEn 카테고리가 매핑되지 않은 문제는 null — 프론트는 "기타"로 표시한다.
   */
  public record WrongQuestion(
      Long questionId,
      String questionLabel,
      Integer orderNum,
      String questionText,
      String questionType,
      String choices,
      String correctAnswer,
      String studentAnswer,
      String explanation,
      String categoryNameEn,
      String categoryNameKo,
      Integer point) {
  }
}
