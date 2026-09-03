package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

/**
 * THREAD 글 분류. UI 필터이자 AI 필터 축이다("질문만 읽기", "출제포인트만 분석" 등).
 * {@link QuestionThreadDataKind} 와는 독립적인 축이다(예: category=VARIANT + dataKind=GENERATED_PRACTICE).
 */
public enum QuestionThreadCategory {
  QUESTION("질문"),
  INTERPRETATION("해석·구문"),
  GRAMMAR("어법"),
  VOCAB("어휘"),
  EXAM_POINT("출제포인트"),
  PREDICTED("예상문제"),
  VARIANT("변형문제"),
  SOLUTION("풀이·해설"),
  EXPLANATION_IMPROVEMENT("해설 개선"),
  CLASS_INSIGHT("수업 인사이트"),
  ETC("기타");

  private final String label;

  QuestionThreadCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
