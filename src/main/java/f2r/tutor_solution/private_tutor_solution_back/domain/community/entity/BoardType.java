package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

/**
 * 공개 커뮤니티 게시판 종류. 탭 = 전체(필터) / 자유게시판(FREE) / Q&A(QNA) / 과목(SUBJECT, subject_id로 구분).
 * 과목은 subjects 테이블(동적, 영어→국어/수학…)을 참조 — SUBJECT일 때만 subject_id 필수.
 */
public enum BoardType {
  FREE("자유게시판"),
  QNA("Q&A"),
  SUBJECT("과목");

  private final String label;

  BoardType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
