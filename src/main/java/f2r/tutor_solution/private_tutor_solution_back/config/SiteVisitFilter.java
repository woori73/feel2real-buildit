package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsVisitorIdentity;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteVisitRecordingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * F2R 전체(+ Wiki 하위) 실제 페이지 방문을 기록한다. 정적 리소스(css/js/img)와 {@code /api/**}는
 * 경로 prefix로 우선 제외하고, 남은 요청도 {@code chain.doFilter} 이후 실제 응답
 * Content-Type이 {@code text/html}이고 상태가 200인 경우에만 기록한다 — "진짜 페이지 진입"만 카운트.
 *
 * <p>{@code JwtAuthenticationFilter} 뒤에 등록되어(SecurityConfig) {@link SecurityContextHolder}에
 * principal이 이미 채워진 상태로 동작한다. UV(순 방문자) 식별은 로그인 "U:{userId}", 비로그인은
 * first-party 장기 cookie의 UUID("V:{uuid}")를 쓴다. 기존 "S:{sessionId}" 행은 그대로 집계한다.
 * IP와 fingerprint는 사용하지 않는다.
 *
 * <p>{@code @Component}가 아니라 {@link SecurityConfig}에서 직접 생성해 필터체인에 넣는다
 * (Filter 빈은 서블릿 컨테이너에 자동 등록돼 이중 실행되므로 — {@link SubdomainAcademyFilter} 참고).
 * 통계 기록 실패가 페이지 응답을 깨서는 안 되므로 {@link SiteVisitRecordingService}가 예외를 삼킨다.
 */
public class SiteVisitFilter extends OncePerRequestFilter {

  private static final List<String> EXCLUDED_PREFIXES = List.of(
      "/api/", "/css/", "/js/", "/img/", "/video/", "/webjars/",
      "/actuator/", "/swagger-ui", "/v3/api-docs", "/error");

  private static final Set<String> EXCLUDED_EXACT = Set.of(
      "/favicon.ico", "/robots.txt", "/sitemap.xml");

  static final String VISITOR_COOKIE_NAME = AnalyticsVisitorIdentity.VISITOR_COOKIE_NAME;
  private static final Duration VISITOR_COOKIE_MAX_AGE = Duration.ofDays(90);
  // ObjectProvider 지연 주입: @WebMvcTest 슬라이스(JPA 레포/서비스 없음)가 SecurityConfig를
  // import해도 컨텍스트가 뜨도록(SubdomainAcademyFilter와 동일 패턴).
  private final ObjectProvider<SiteVisitRecordingService> recordingService;

  public SiteVisitFilter(ObjectProvider<SiteVisitRecordingService> recordingService) {
    this.recordingService = recordingService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();
    boolean candidate = !Boolean.TRUE.equals(request.getAttribute("f2r.mcp.readOnly"))
        && "GET".equalsIgnoreCase(request.getMethod())
        && !EXCLUDED_EXACT.contains(uri)
        && EXCLUDED_PREFIXES.stream().noneMatch(uri::startsWith);

    // Resolve identity and attach a new visitor cookie before the downstream handler can commit.
    Long userId = null;
    String role = null;
    String visitorKey = null;
    AnalyticsTrafficKind trafficKind = AnalyticsTrafficKind.HUMAN;
    if (candidate) {
      trafficKind = isBot(request.getHeader("User-Agent"))
          ? AnalyticsTrafficKind.BOT : AnalyticsTrafficKind.HUMAN;
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof JwtTokenProvider.AuthenticatedPrincipal principal) {
        userId = principal.userId();
        role = principal.role();
      }
      if (userId != null) {
        visitorKey = "U:" + userId;
      } else if (trafficKind == AnalyticsTrafficKind.BOT) {
        visitorKey = "B:BOT";
      } else {
        String visitorId = validVisitorId(request);
        if (visitorId == null) {
          visitorId = UUID.randomUUID().toString();
          response.addHeader(HttpHeaders.SET_COOKIE, visitorCookie(visitorId).toString());
        }
        visitorKey = "V:" + visitorId;
      }
    }

    chain.doFilter(request, response);

    if (!candidate) {
      return;
    }
    String contentType = response.getContentType();
    if (response.getStatus() != HttpServletResponse.SC_OK
        || contentType == null
        || !contentType.startsWith(MediaType.TEXT_HTML_VALUE)) {
      return;
    }

    SiteVisitRecordingService service = recordingService.getIfAvailable();
    if (service == null) {
      return;
    }

    service.record(uri, visitorKey, userId, role, trafficKind);
  }

  private boolean isBot(String userAgent) {
    return AnalyticsVisitorIdentity.isBot(userAgent);
  }

  private String validVisitorId(HttpServletRequest request) {
    return AnalyticsVisitorIdentity.validVisitorId(request);
  }

  private ResponseCookie visitorCookie(String visitorId) {
    return ResponseCookie.from(VISITOR_COOKIE_NAME, visitorId)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(VISITOR_COOKIE_MAX_AGE)
        .build();
  }
}
