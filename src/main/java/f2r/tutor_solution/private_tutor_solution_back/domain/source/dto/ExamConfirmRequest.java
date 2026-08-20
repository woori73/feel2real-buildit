package f2r.tutor_solution.private_tutor_solution_back.domain.source.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;

import java.util.List;

/**
 * Phase 4 검증 화면에서 학생이 [전체 확정] 시 보내는 데이터.
 * - meta: LLM 추출값 + 학생 보정 (학교/학년/시험종류/연도/학기)
 * - answers: 채점 + 배점 보정 (question_no 기준)
 */
public record ExamConfirmRequest(
    Meta meta,
    List<AnswerOverride> answers
) {
  public record Meta(
      String schoolName,
      Byte grade,
      ExamType examType,
      Short examYear,
      Byte examSemester
  ) {}

  /**
   * 한 문항에 대한 학생 보정 데이터.
   * - isCorrect는 null이면 LLM 추출값 유지
   * - point는 null이면 questions.point 그대로 유지, 0 이상의 정수면 update
   */
  public record AnswerOverride(
      Integer questionNo,
      Boolean isCorrect,
      Integer point
  ) {}
}
