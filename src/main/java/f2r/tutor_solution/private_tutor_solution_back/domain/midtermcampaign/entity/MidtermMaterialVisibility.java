package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

/**
 * 자료 공개 범위(작업지시서 §14). 프런트 숨김이 아니라 서버가 판정한다
 * ({@code MidtermMaterialAccessService}). PRIVATE 자료의 원본 URL 은 응답에 실리지 않는다.
 */
public enum MidtermMaterialVisibility {
  /** 외부 공개 안 함 — 목록에도 노출하지 않는다. */
  PRIVATE,
  /** 썸네일/일부만 공개(전체 파일 미제공). */
  PREVIEW,
  /** 전체 공개 — 비로그인 학부모도 파일 열람 가능. */
  PUBLIC,
  /** 로그인 회원만 전체 파일 접근. */
  MEMBER
}
