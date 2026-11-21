package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;

/**
 * 재원생 앱 로그인 상태를 기존 {@link AuthenticatedPrincipal}로 나타낸다. 그래야 응시·채점·해설(TestPrepService)을 그대로 쓴다.
 * <ul>
 *   <li>userId = -studentId: 회원(users) 번호와 겹치지 않는 음수. 응시(tp_attempts.user_id)의 주인 확인이 이 값으로 된다.</li>
 *   <li>loginId = "app:" + 코드 번호: 요청마다 코드가 아직 쓰이는지(폐기·재발급·퇴원) 확인할 때 쓴다.</li>
 *   <li>role = STUDENT_APP: 학생 앱 경로와 문항 그림에서만 통한다(SecurityConfig).</li>
 * </ul>
 */
public final class StudentAppPrincipals {

  public static final String ROLE = "STUDENT_APP";
  static final String LOGIN_PREFIX = "app:";

  private StudentAppPrincipals() {}

  public static AuthenticatedPrincipal of(StudentAppCode code) {
    return new AuthenticatedPrincipal(-code.getStudentId(), LOGIN_PREFIX + code.getCodeId(), ROLE, null);
  }

  public static boolean is(AuthenticatedPrincipal p) {
    return p != null && ROLE.equals(p.role()) && p.userId() != null && p.userId() < 0
        && p.loginId() != null && p.loginId().startsWith(LOGIN_PREFIX);
  }

  public static Long studentId(AuthenticatedPrincipal p) {
    return is(p) ? -p.userId() : null;
  }

  public static Long codeId(AuthenticatedPrincipal p) {
    if (!is(p)) return null;
    try {
      return Long.parseLong(p.loginId().substring(LOGIN_PREFIX.length()));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
