package f2r.tutor_solution.private_tutor_solution_back.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 인증 쿠키 생성/삭제 헬퍼.
 *
 * <p>모든 쿠키는 HttpOnly + SameSite=Lax + Path=/ 로 발급된다.
 * 운영(prd)은 HTTPS이므로 {@code Secure} 플래그를 부여하고, 로컬은 false로 둔다
 * (Chrome은 Secure=true 쿠키를 http://localhost에서도 받아주긴 하지만 명시적으로 분기).
 */
@Component
public class CookieUtil {

  private final JwtProperties props;

  public CookieUtil(JwtProperties props) {
    this.props = props;
  }

  /** 발급된 access token을 HTTP-only Cookie로 응답에 부착. */
  public void writeAccessToken(HttpServletResponse response, String token) {
    ResponseCookie cookie = applyDomain(baseCookie(token)
        .maxAge(props.expirationSeconds()))
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  /**
   * Max-Age=0 빈 쿠키로 덮어서 브라우저에서 즉시 삭제하게 한다.
   * 삭제 쿠키도 발급과 동일한 {@code Domain}을 써야 domain-scoped 쿠키가 실제로 지워진다(좀비 세션 방지).
   */
  public void clearAccessToken(HttpServletResponse response) {
    ResponseCookie cookie = applyDomain(baseCookie("")
        .maxAge(0))
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    return ResponseCookie.from(props.cookieName(), value)
        .httpOnly(true)
        .secure(props.secureCookie())
        .sameSite("Lax")
        .path("/");
  }

  /**
   * cookie-domain이 설정된 경우(운영)에만 {@code Domain}을 붙인다. 빈 문자열로 {@code .domain("")}을
   * 호출하면 malformed Set-Cookie가 되므로 non-blank일 때만 적용한다.
   */
  private ResponseCookie.ResponseCookieBuilder applyDomain(ResponseCookie.ResponseCookieBuilder builder) {
    String domain = props.cookieDomain();
    if (domain != null && !domain.isBlank()) {
      builder.domain(domain);
    }
    return builder;
  }

  /** 요청 쿠키에서 access token 추출. 없으면 empty. */
  public Optional<String> readAccessToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return Optional.empty();
    for (Cookie c : cookies) {
      if (props.cookieName().equals(c.getName())) {
        return Optional.ofNullable(c.getValue()).filter(v -> !v.isEmpty());
      }
    }
    return Optional.empty();
  }
}
