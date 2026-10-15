package f2r.tutor_solution.private_tutor_solution_back.domain.community.entity;

/**
 * 공개 커뮤니티 게시판 종류.
 *
 * <p>F2R 커뮤니티(/community) 탭 = 전체(필터) / 자유게시판(FREE) / Q&A(QNA) / 과목(SUBJECT, subject_id로 구분).
 * 과목은 subjects 테이블(동적, 영어→국어/수학…)을 참조 — SUBJECT일 때만 subject_id 필수.
 *
 * <p>공개 BuildIt Wiki(/wiki) 전용 게시판 = 시험 후기(EXAM_REVIEW) / 변형문제(VARIANT) / 자료 공유·요청(MATERIAL).
 * 같은 community_posts 테이블을 쓰되 {@code scope}로 목록이 분리된다({@code CommunityService}).
 */
public enum BoardType {
  FREE("자유게시판"),
  QNA("Q&A"),
  SUBJECT("과목"),
  EXAM_REVIEW("시험 후기"),
  VARIANT("변형문제"),
  MATERIAL("자료 공유·요청");

  private final String label;

  BoardType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
