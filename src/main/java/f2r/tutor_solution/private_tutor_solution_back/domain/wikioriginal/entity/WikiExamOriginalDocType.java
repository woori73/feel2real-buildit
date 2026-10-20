package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity;

/** 모의고사 자료 구분: 문제지 / 정답지 / 문제풀이 해설 / 구문독해. */
public enum WikiExamOriginalDocType {
  QUESTION("문제지"),
  ANSWER("정답지"),
  EXPLANATION("문제풀이 해설"),
  SYNTAX("구문독해");

  private final String label;

  WikiExamOriginalDocType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
