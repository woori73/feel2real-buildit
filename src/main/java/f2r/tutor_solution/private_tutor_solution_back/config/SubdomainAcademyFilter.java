package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * {@code {handle}.{base-domain}} 서브도메인의 root({@code /}) 요청을 해당 Academy 공개 페이지로 forward한다.
 *
 * <p>{@code getServerName()}이 {@code X-Forwarded-Host}를 반영하도록 {@code ForwardedHeaderFilter} 뒤에
 * (= Spring Security 필터체인 안에서) 실행된다. base-domain 미설정(local/test 기본)이면 완전 no-op.
 *
 * <ul>
 *   <li>Host가 {@code {handle}.{base-domain}} 형태가 아니면(apex/기타) 통과.
 *   <li>서브도메인이고 path=={@code /} 이면 handle→academy 해석. 있으면 slug를 request 속성으로 주입 후
 *       {@code /subdomain-home}으로 forward(공개 렌더), 없으면 404.
 *   <li>서브도메인이고 path가 LMS({@code /lms}, {@code /lms/...})면 <b>경로를 로컬 형태로 정규화</b>해
 *       체인을 계속 진행한다. {@link #normalizeLmsPath} 참조 — 301도 forward도 아니다.
 *   <li>서브도메인이지만 자산/API 경로({@link #PASS_THROUGH_PREFIXES})면 통과 — 학원 페이지가
 *       자기 CSS/JS/이미지/API를 같은 호스트에서 로드해야 하므로 리다이렉트 대상이 아니다.
 *   <li>그 외 서브도메인의 공통 페이지 경로({@code /home}, {@code /academies}, {@code /community} 등)는
 *       apex의 동일 path로 301. 공통 경로 앞에 학원 식별자가 붙어서는 안 되기 때문.
 * </ul>
 *
 * <p>{@code @Component}가 아니라 {@link SecurityConfig}에서 직접 생성해 필터체인에 넣는다
 * (Filter 빈은 서블릿 컨테이너에 자동 등록돼 이중 실행되므로).
 */
public class SubdomainAcademyFilter extends OncePerRequestFilter {

  public static final String SLUG_ATTRIBUTE = "subdomainAcademySlug";

  private static final String RENDER_PATH = "/subdomain-home";

  /** 학원 LMS 경로 prefix. 서브도메인에서는 handle이 생략된 짧은 형태로 들어온다. */
  private static final String LMS_PREFIX = "/lms";

  /**
   * 서브도메인에서 apex로 301하지 않고 그대로 서빙할 경로. 학원 공개 페이지가 같은 호스트에서 불러오는
   * 정적 자산·API·내부 forward 지점이라, 리다이렉트하면 페이지 자체가 깨진다.
   */
  private static final List<String> PASS_THROUGH_PREFIXES = List.of(
      "/api/", "/css/", "/js/", "/img/", "/webjars/", RENDER_PATH);

  /** 확장자 없는 단일 파일 자산(디렉터리가 아니라 정확히 일치해야 한다). */
  private static final List<String> PASS_THROUGH_EXACT = List.of(
      "/favicon.ico", "/favicon.svg", "/robots.txt");

  // ObjectProvider 지연 주입: @WebMvcTest 슬라이스(JPA 레포 없음)가 SecurityConfig를 import해도
  // 컨텍스트가 뜨도록. base-domain 미설정이면 레포를 아예 조회하지 않는다.
  private final ObjectProvider<AcademyRepository> academyRepository;
  private final PublicSiteProperties props;

  public SubdomainAcademyFilter(
      ObjectProvider<AcademyRepository> academyRepository, PublicSiteProperties props) {
    this.academyRepository = academyRepository;
    this.props = props;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (!props.hasBaseDomain()) {
      filterChain.doFilter(request, response);
      return;
    }

    Optional<String> handle = extractHandle(request.getServerName());
    if (handle.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    // 서브도메인의 비-root 경로: LMS는 경로 정규화 후 통과, 자산/API는 그대로 통과,
    // 그 외 공통 페이지 경로는 apex로 301.
    if (!"/".equals(request.getRequestURI())) {
      if (isLmsPath(request.getRequestURI())) {
        filterChain.doFilter(normalizeLmsPath(request, handle.get()), response);
      } else if (isPassThrough(request.getRequestURI())) {
        filterChain.doFilter(request, response);
      } else {
        redirectToApex(request, response);
      }
      return;
    }

    Optional<Academy> academy = academyRepository.getObject().findByHandle(handle.get());
    if (academy.isEmpty()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "존재하지 않는 학원 주소입니다.");
      return;
    }
    request.setAttribute(SLUG_ATTRIBUTE, academy.get().getSlug());
    request.getRequestDispatcher(RENDER_PATH).forward(request, response);
  }

  private boolean isPassThrough(String uri) {
    return PASS_THROUGH_EXACT.contains(uri)
        || PASS_THROUGH_PREFIXES.stream().anyMatch(uri::startsWith);
  }

  /** {@code /lms} 정확 일치 또는 {@code /lms/} 하위. {@code /lmsfoo}는 아니다. */
  private boolean isLmsPath(String uri) {
    return LMS_PREFIX.equals(uri) || uri.startsWith(LMS_PREFIX + "/");
  }

  /**
   * 서브도메인 LMS 경로를 로컬 형태로 정규화한다.
   * {@code edu-holic.{base}/lms/my} → 내부적으로 {@code /lms/edu-holic/my}.
   * 브라우저 주소창은 원래 URL 그대로다(리다이렉트가 아니므로).
   *
   * <p><b>forward가 아니라 request wrapping인 이유:</b> 이 필터는 시큐리티 필터체인 안,
   * {@code JwtAuthenticationFilter}와 {@code AuthorizationFilter}보다 <b>앞</b>에서 돈다
   * ({@link SecurityConfig} 필터 등록 순서 참조). 여기서 {@code forward()}를 부르면 체인의
   * 나머지 필터가 실행되지 않고, FORWARD dispatch는 시큐리티 체인에 재진입하지도 않는다
   * → prd에서 LMS 인증·인가가 통째로 우회된다. wrapping은 체인을 그대로 계속 태운다.
   *
   * <p>이 정규화 덕분에 인가 매처와 컨트롤러 매핑은 로컬 형태 <b>한 벌</b>만 두면 된다.
   */
  private HttpServletRequest normalizeLmsPath(HttpServletRequest request, String handle) {
    String rest = request.getRequestURI().substring(LMS_PREFIX.length());
    String normalized = LMS_PREFIX + "/" + handle + rest;

    HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
      @Override
      public String getRequestURI() {
        return normalized;
      }

      @Override
      public String getServletPath() {
        return normalized;
      }

      @Override
      public String getPathInfo() {
        return null;
      }

      @Override
      public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer(request.getScheme()).append("://")
            .append(request.getServerName());
        int port = request.getServerPort();
        if (port > 0 && port != 80 && port != 443) {
          url.append(':').append(port);
        }
        return url.append(normalized);
      }
    };

    // ⚠ Spring 6+/Security 7은 파싱한 경로를 request attribute로 캐시한다
    //   (PathPatternRequestMatcher가 hasParsedRequestPath → getParsedRequestPath 순으로 읽는다).
    //   여기서 갱신하지 않으면 인가가 **원본 경로 기준으로 조용히** 평가된다 — 에러 없이 열린다.
    //   SubdomainLmsPathSecurityIntegrationTest가 이 한 줄을 못 박는다.
    ServletRequestPathUtils.parseAndCache(wrapped);
    return wrapped;
  }

  /**
   * 공통 경로를 apex의 동일 path로 301한다. query string은 보존하고, {@code getRequestURI()}는
   * 인코딩된 상태 그대로 쓴다(Location 헤더는 인코딩된 형태여야 하므로 디코드하지 않는다).
   */
  private void redirectToApex(HttpServletRequest request, HttpServletResponse response) {
    String query = request.getQueryString();
    String target = "https://" + props.baseDomain() + request.getRequestURI()
        + (query == null || query.isBlank() ? "" : "?" + query);
    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    response.setHeader("Location", target);
  }

  /**
   * Host에서 {@code {handle}.{base-domain}}의 handle을 뽑는다. apex(host==base-domain),
   * base-domain suffix 불일치, 다단계 서브도메인({@code a.b.base})은 모두 empty(= 통과).
   */
  private Optional<String> extractHandle(String serverName) {
    if (serverName == null) return Optional.empty();
    String host = serverName.toLowerCase();
    String suffix = "." + props.baseDomain().toLowerCase();
    if (!host.endsWith(suffix)) return Optional.empty();
    String prefix = host.substring(0, host.length() - suffix.length());
    if (prefix.isEmpty() || prefix.contains(".")) return Optional.empty();
    return Optional.of(prefix);
  }
}
