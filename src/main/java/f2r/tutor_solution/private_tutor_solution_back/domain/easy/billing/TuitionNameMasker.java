package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

/** 공개 화면에 학생 실명을 내보내지 않기 위한 이름 가리기. */
public final class TuitionNameMasker {

  private TuitionNameMasker() {}

  /** 김민수 → 김*수, 김수 → 김*, 남궁민수 → 남**수. 비어 있으면 "학생". */
  public static String mask(String name) {
    if (name == null || name.isBlank()) {
      return "학생";
    }
    String trimmed = name.trim();
    int[] cps = trimmed.codePoints().toArray();
    if (cps.length == 1) {
      return "*";
    }
    StringBuilder out = new StringBuilder().appendCodePoint(cps[0]);
    if (cps.length == 2) {
      return out.append('*').toString();
    }
    out.append("*".repeat(cps.length - 2));
    return out.appendCodePoint(cps[cps.length - 1]).toString();
  }
}
