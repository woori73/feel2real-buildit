package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity;

/**
 * 수강권을 누가/왜 부여했는지. Phase 1은 {@code ACADEMY}(재원생 무료 배정)·{@code ADMIN}만 실제로
 * 쓰인다. {@code PURCHASE}는 향후 유료 영문법 강좌 PG 연동을 위해 값만 미리 열어 둔다(작업지시서 §6).
 */
public enum LmsGrantSource {
  ACADEMY,
  ADMIN,
  PURCHASE
}
