package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import java.util.Locale;
import java.util.Set;

/**
 * 신규 가입으로 쓸 수 없는 로그인 ID. 폐기된 최초 관리자 ID를 다른 사람이 가입으로 선점해 감사 기록(created_by 등)이
 * 섞이거나 관리자로 오인되는 것을 막는다. 비교는 공백 제거 + 대소문자 무시.
 */
public final class ReservedLoginIds {

  private static final Set<String> RESERVED = Set.of("admin-f2r");

  private ReservedLoginIds() {}

  public static boolean isReserved(String loginId) {
    return loginId != null && RESERVED.contains(loginId.trim().toLowerCase(Locale.ROOT));
  }
}
