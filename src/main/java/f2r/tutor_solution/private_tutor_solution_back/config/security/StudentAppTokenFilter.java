package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppPrincipals;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 재원생 앱(/naesintest/app) 로그인 쿠키(F2R_STUDENT_APP) 확인.
 * <ul>
 *   <li>학생 앱 API(/api/naesintest/app/**)에서는 이 쿠키를 먼저 쓴다(같은 휴대폰에 네이버 로그인이 있어도 앱은 코드 학생으로 본다).</li>
 *   <li>문항 그림(/naesin/**, TOEIC·SAT 그림 /testprep/img/**)은 다른 로그인이 없을 때만 이 쿠키로 연다.</li>
 *   <li>요청마다 코드가 아직 사용 중인지(폐기·재발급·퇴원) DB로 확인한다. 실패하면 아무 인증도 넣지 않는다(fail-closed).</li>
 *   <li>그 밖의 경로(관리자·대시보드·일반 API)에는 이 쿠키가 아무 권한도 주지 않는다.</li>
 * </ul>
 */
public class StudentAppTokenFilter extends OncePerRequestFilter {

  public static final String COOKIE = "F2R_STUDENT_APP";
  static final String API_PREFIX = "/api/naesintest/app/";
  static final String IMAGE_PREFIX = "/naesin/";
  /** TOEIC Part 1 사진·SAT 그림(2026-11-24 TOEIC·SAT 회차 배정). */
  static final String TESTPREP_IMAGE_PREFIX = "/testprep/img/";

  private final JwtTokenProvider tokenProvider;
  private final ObjectProvider<StudentAppCodeService> codesProvider;

  public StudentAppTokenFilter(JwtTokenProvider tokenProvider, ObjectProvider<StudentAppCodeService> codesProvider) {
    this.tokenProvider = tokenProvider;
    this.codesProvider = codesProvider;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String uri = request.getRequestURI();
    boolean api = uri.startsWith(API_PREFIX);
    boolean image = uri.startsWith(IMAGE_PREFIX) || uri.startsWith(TESTPREP_IMAGE_PREFIX);
    Authentication before = SecurityContextHolder.getContext().getAuthentication();
    boolean set = false;
    if (api || (image && before == null)) {
      AuthenticatedPrincipal p = verify(cookie(request));
      if (p != null) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            p, null, List.of(new SimpleGrantedAuthority("ROLE_" + StudentAppPrincipals.ROLE))));
        set = true;
      } else if (api && before != null) {
        // 학생 앱 API는 코드 로그인만 받는다. 다른 로그인으로 들어오면 인증을 비워 401이 나게 한다.
        SecurityContextHolder.getContext().setAuthentication(null);
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      if (set) SecurityContextHolder.clearContext();
    }
  }

  AuthenticatedPrincipal verify(String token) {
    if (token == null || token.isBlank()) return null;
    StudentAppCodeService codes = codesProvider.getIfAvailable();
    if (codes == null) return null;
    try {
      AuthenticatedPrincipal p = tokenProvider.parse(token);
      if (!StudentAppPrincipals.is(p)) return null;
      return codes.activeSession(StudentAppPrincipals.codeId(p), StudentAppPrincipals.studentId(p)).isPresent() ? p : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String cookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) if (COOKIE.equals(c.getName())) return c.getValue();
    return null;
  }
}
