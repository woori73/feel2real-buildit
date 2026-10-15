package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity;

/** 모의고사 원본 구분: 문제지 / 정답지. */
public enum WikiExamOriginalDocType {
  QUESTION("문제지"),
  ANSWER("정답지");

  private final String label;

  WikiExamOriginalDocType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
