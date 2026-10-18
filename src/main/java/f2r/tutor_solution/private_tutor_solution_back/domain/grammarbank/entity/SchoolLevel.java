package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

/** 학교급·학년. 미확정은 UNKNOWN, 학년 무관 공통 문제는 COMMON. */
public enum SchoolLevel {
  M1, M2, M3, H1, H2, H3, COMMON, UNKNOWN;

  public String labelKo() {
    return switch (this) {
      case M1 -> "중1";
      case M2 -> "중2";
      case M3 -> "중3";
      case H1 -> "고1";
      case H2 -> "고2";
      case H3 -> "고3";
      case COMMON -> "공통";
      case UNKNOWN -> "미확정";
    };
  }
}
