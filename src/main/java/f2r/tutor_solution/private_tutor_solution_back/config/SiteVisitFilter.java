package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteVisitRecordingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
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
 * {@link jakarta.servlet.http.HttpSession} id("S:{sessionId}") — IP는 전혀 사용하지 않는다
 * ({@code WikiVisitService}와 동일한 개인정보 최소화 원칙).
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

  private static final String WIKI_PREFIX = "/wiki";

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
    boolean candidate = "GET".equalsIgnoreCase(request.getMethod())
        && !EXCLUDED_EXACT.contains(uri)
        && EXCLUDED_PREFIXES.stream().noneMatch(uri::startsWith);

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

    Long userId = null;
    String role = null;
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof JwtTokenProvider.AuthenticatedPrincipal principal) {
      userId = principal.userId();
      role = principal.role();
    }
    String visitorKey = userId != null ? "U:" + userId : "S:" + request.getSession(true).getId();

    service.record(uri, uri.startsWith(WIKI_PREFIX), visitorKey, userId, role);
  }
}
