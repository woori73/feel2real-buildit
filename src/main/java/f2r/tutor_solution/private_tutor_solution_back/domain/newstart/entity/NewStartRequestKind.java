package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

/** 질문요청 게시판의 요청 종류(설계 §5의 2개 인박스 = 1개 테이블의 2개 값). */
public enum NewStartRequestKind {
  /** [질문·조사 요청] — 회원이 답을 모르는 상황·기관 대응에 대한 질문. */
  QUESTION,
  /** [사이트 개선 요청] — 내용 오류, 잘못된 링크, 오래된 정보, UI/버튼 기능 개선. */
  SITE_IMPROVEMENT
}
