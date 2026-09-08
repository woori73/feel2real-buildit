package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

public enum VideoActiveSource {
  RAW,
  NORMALIZED,
  /**
   * 외부 링크 영상({@link AcademyVideoSourceKind#EXTERNAL_LINK}). storage key·트랜스코딩 산출물이
   * 없고 재생은 외부 URL 로 이루어진다 — optimization/cleanup 파이프라인은 이 소스를 건드리지 않는다.
   */
  EXTERNAL
}
