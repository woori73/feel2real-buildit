package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * NewStart 지식 검색 Agent 전용 Bearer 토큰 인증 필터.
 *
 * <p>배경: {@code /api/muse/newstart/knowledge/search} 는 Muse 에이전트가 운영 DB의 ACTIVE
 * NewStart 지식을 read-only 로 검색하는 Phase 1 연결용 엔드포인트다. 브라우저가 없는
 * 에이전트는 ADMIN JWT 쿠키 세션을 가질 수 없으므로, 환경변수
 * {@code NEWSTART_AGENT_TOKEN} 과 일치하는 {@code Authorization: Bearer} 헤더를
 * 제시하면 검색 allowlist 에 한해 {@code ROLE_NEWSTART_AGENT} 로 인증한다.
 *
 * <p>{@link ChartAgentTokenFilter} 와 동일한 보안 패턴을 재사용한다.
 *
 * <p>안전 장치:
 * <ul>
 *   <li>토큰 미설정(빈 값)이면 필터는 완전히 비활성화된다 (fail-closed).
 *   <li>allowlist 밖 경로는 절대 인증하지 않는다 — GET 검색 1개 경로만 허용.
 *   <li>기존 인증이 이미 있으면 덮어쓰지 않는다 (관리자 쿠키 우선).
 *   <li>비교는 상수 시간 비교, 토큰을 로그에 남기지 않는다.
 *   <li>인증 성공 시 {@link AuditorContextHolder} 에 {@code newstart-agent} 를 set 하여
 *       감사 추적을 유지한다. 요청 종료 시점에 반드시 clear.
 * </ul>
 */
public class NewstartAgentTokenFilter extends OncePerRequestFilter {

  public static final String ROLE = "NEWSTART_AGENT";
  public static final String PRINCIPAL = "newstart-agent";
  public static final String ENV_VAR = "NEWSTART_AGENT_TOKEN";

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  /** HTTP 메서드 + Ant 경로. read-only 지식 검색 1개 경로만 허용 (쓰기 없음). */
  private static final List<AgentRoute> ALLOWLIST = List.of(
      new AgentRoute("GET", "/api/muse/newstart/knowledge/search"));

  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final Supplier<String> tokenSupplier;

  /** 운영용: {@code NEWSTART_AGENT_TOKEN} 환경변수에서 읽는다. */
  public NewstartAgentTokenFilter() {
    this(System.getenv(ENV_VAR));
  }

  /** 테스트·배선용: 토큰 값을 직접 주입한다 (빈 값이면 비활성화). */
  public NewstartAgentTokenFilter(String configuredToken) {
    this(() -> configuredToken);
  }

  NewstartAgentTokenFilter(Supplier<String> tokenSupplier) {
    this.tokenSupplier = tokenSupplier;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    boolean auditSet = false;
    boolean authenticatedHere = false;
    try {
      if (SecurityContextHolder.getContext().getAuthentication() == null
          && isAllowlisted(request.getMethod(), request.getRequestURI())
          && tokenMatches(tokenSupplier.get(), bearerToken(request))) {
        var auth = new UsernamePasswordAuthenticationToken(
            PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuditorContextHolder.set(PRINCIPAL);
        authenticatedHere = true;
        auditSet = true;
      }
      chain.doFilter(request, response);
    } finally {
      // 이 필터가 설정한 인증만 정리한다 — 앞단 JWT 필터의 ADMIN 인증은 건드리지 않는다.
      if (authenticatedHere) {
        SecurityContextHolder.clearContext();
      }
      if (auditSet) AuditorContextHolder.clear();
    }
  }

  private boolean isAllowlisted(String method, String uri) {
    for (AgentRoute route : ALLOWLIST) {
      if (route.method().equalsIgnoreCase(method) && pathMatcher.match(route.pattern(), uri)) {
        return true;
      }
    }
    return false;
  }

  private String bearerToken(HttpServletRequest request) {
    String header = request.getHeader(AUTH_HEADER);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length()).trim();
    }
    return null;
  }

  private boolean tokenMatches(String configured, String presented) {
    if (configured == null || configured.isBlank() || presented == null || presented.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        configured.getBytes(StandardCharsets.UTF_8),
        presented.getBytes(StandardCharsets.UTF_8));
  }

  private record AgentRoute(String method, String pattern) {}
}
