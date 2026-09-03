package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

/**
 * 데이터 성격. AI 가 나중에 신뢰도를 판단할 수 있도록 저장 시점에 고정한다.
 * 예상(GENERATED_PRACTICE)과 실제 기출(EXAM_EVIDENCE)을 절대 같은 데이터로 취급하지 않는다.
 */
public enum QuestionThreadDataKind {
  /** 교육자·학생의 질문·의견·해석·풀이·출제예상·수업경험·해설개선 의견. */
  DISCUSSION("커뮤니티 지식"),
  /** 사용자가 만든 예상문제·변형문제(정답·해설 포함). {@link QuestionThreadVariantDetail} 동반. */
  GENERATED_PRACTICE("사용자 제작 문제"),
  /** 실제 학교 시험에서 확인된 기출 증거. {@link QuestionThreadExamEvidence} 동반. */
  EXAM_EVIDENCE("실제 기출 증거");

  private final String label;

  QuestionThreadDataKind(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
