package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriUtils;

/**
 * NewStart 회원 전용 화면 게이트. 홈({@code /newstart})과 로그인({@code /newstart/login})만 공개이고,
 * 그 밖의 {@code /newstart/**} 화면은 비회원이 URL을 직접 입력해도 자체 로그인 화면으로 보낸다.
 * 로그인 후 원래 화면으로 돌아오도록 {@code next} 쿼리에 요청 경로를 싣는다.
 * 등록 경로·제외 경로는 {@link WebMvcConfig}가 정한다. 의존성이 없어 직접 생성한다.
 */
public class NewStartMembersOnlyInterceptor implements HandlerInterceptor {

  static final String LOGIN_PATH = "/newstart/login";

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (isAuthenticated()) {
      return true;
    }
    String target = request.getRequestURI();
    if (request.getQueryString() != null) {
      target += "?" + request.getQueryString();
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    response.sendRedirect(LOGIN_PATH + "?next=" + UriUtils.encode(target, StandardCharsets.UTF_8));
    return false;
  }

  private static boolean isAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getPrincipal() instanceof JwtTokenProvider.AuthenticatedPrincipal;
  }
}
