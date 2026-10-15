package f2r.tutor_solution.private_tutor_solution_back.program;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;

/**
 * 프로그램 진입 화면 공통 처리. 비회원은 공개 첫 화면(200), 로그인했지만 권한이 없으면 서비스 안내 화면(403 본문),
 * 권한이 있으면 실제 기능 화면으로 보낸다. API 인가는 이 클래스와 무관하게 SecurityConfig가 그대로 막는다.
 */
public final class ProgramEntry {

  public enum State { ANON, FORBIDDEN, ALLOWED }

  public static final String INTRO_VIEW = "program/intro";

  private ProgramEntry() {}

  public static State stateOf(Program program, Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return State.ANON;
    }
    for (GrantedAuthority a : auth.getAuthorities()) {
      String name = a.getAuthority();
      if (name != null && name.startsWith("ROLE_") && program.getRoles().contains(name.substring(5))) {
        return State.ALLOWED;
      }
    }
    return State.FORBIDDEN;
  }

  /** 컨트롤러가 principal의 role만 갖고 있을 때 쓰는 판정. role이 null이면 비회원이다. */
  public static State stateOfRole(Program program, String roleOrNull) {
    if (roleOrNull == null) return State.ANON;
    return program.getRoles().contains(roleOrNull) ? State.ALLOWED : State.FORBIDDEN;
  }

  /**
   * 공개 첫 화면 렌더. 사용자별로 내용이 달라지므로 캐시하지 않는다.
   * 권한 없는 로그인 사용자는 브라우저 기본 403 대신 안내 화면을 같은 403 상태로 받는다.
   */
  public static String intro(
      Program program, State state, Model model, HttpServletResponse response) {
    model.addAttribute("program", program);
    model.addAttribute("state", state.name());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    if (state == State.FORBIDDEN) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    return INTRO_VIEW;
  }
}
