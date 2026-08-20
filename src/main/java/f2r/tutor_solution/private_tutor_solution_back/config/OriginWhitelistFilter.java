package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * 상태변경 {@code /api/**} 요청(POST/PUT/DELETE/PATCH)의 Origin(없으면 Referer) 호스트를
 * 화이트리스트 suffix와 대조해 불일치 시 403. 서브도메인 same-site화에 따른 CSRF 방어(토큰 방식 대신).
 *
 * <p>CORS가 아니라 서버측 Origin 강제다. {@code app.security.allowed-origins}가 비어있으면
 * (local/dev/stg 기본) 전면 비활성 — 브라우저 아닌 테스트/서버 호출을 막지 않는다.
 * 활성 상태에서 Origin·Referer가 둘 다 없는 쓰기 요청은 기본 거부한다.
 *
 * <p>{@code @Component}가 아니라 {@link SecurityConfig}에서 직접 생성해 필터체인에 넣는다
 * (Filter 빈은 서블릿 컨테이너에 자동 등록돼 이중 실행되므로).
 */
public class OriginWhitelistFilter extends OncePerRequestFilter {

  private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
  private static final PathPattern API_PATH = PathPatternParser.defaultInstance.parse("/api/**");

  private final WebSecurityProperties props;

  public OriginWhitelistFilter(WebSecurityProperties props) {
    this.props = props;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (!props.enforcementEnabled() || !isGuardedWrite(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String originHost = hostOf(request.getHeader("Origin"));
    if (originHost == null) {
      originHost = hostOf(request.getHeader("Referer"));
    }
    if (originHost == null || !isAllowed(originHost)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "허용되지 않은 출처의 요청입니다.");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isGuardedWrite(HttpServletRequest request) {
    return MUTATING_METHODS.contains(request.getMethod())
        && API_PATH.matches(ServletRequestPathUtils.parse(request).pathWithinApplication());
  }

  private boolean isAllowed(String host) {
    String lower = host.toLowerCase();
    for (String allowed : props.allowedOrigins()) {
      if (allowed == null || allowed.isBlank()) continue;
      String suffix = allowed.toLowerCase();
      if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
        return true;
      }
    }
    return false;
  }

  private String hostOf(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) return null;
    try {
      String host = new URI(headerValue.trim()).getHost();
      return (host == null || host.isBlank()) ? null : host;
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
