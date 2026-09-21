package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.ChartAgentTokenFilter;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtAuthenticationFilter;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteVisitRecordingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.CookieOAuth2AuthorizationRequestRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NaverOAuth2LoginFailureHandler;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NaverOAuth2LoginSuccessHandler;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NaverOAuth2UserService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NoOpOAuth2AuthorizedClientRepository;
import jakarta.servlet.DispatcherType;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
@EnableWebSecurity
// PublicSite/WebSecurity properties도 명시 등록: @WebMvcTest 슬라이스(@ConfigurationPropertiesScan 없음)에서
// 이 설정을 import해도 컨텍스트가 뜨도록 한다.
@EnableConfigurationProperties({
    JwtProperties.class,
    PublicSiteProperties.class,
    WebSecurityProperties.class,
    ArchivedRoutesProperties.class
})
public class SecurityConfig {

  private static final Pattern LANDING_VIDEO_ASYNC_PATH =
      Pattern.compile(
          "^/api/admin/landing/videos/[^/]+/(?:stream|thumbnail)$"
              + "|^/api/landing/videos/[^/]+/stream$");

  /**
   * 보안 정책:
   * <ul>
   *   <li>세션 STATELESS — JWT 쿠키 기반이므로 서버 세션 사용 안 함.
   *   <li>CSRF disable — 동일 출처 + SameSite=Lax 쿠키로 충분.
   *   <li>SSR 뷰 경로는 대부분 public — AI Chat 화면만 ADMIN/TUTOR 전용.
   *   <li>API 중 회원가입/로그인/공개 lookup만 public, 나머지는 인증 필요.
   *   <li>{@code /api/admin/**}는 ROLE_ADMIN 필수.
   * </ul>
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtTokenProvider tokenProvider,
      CookieUtil cookieUtil,
      org.springframework.core.env.Environment environment,
      org.springframework.beans.factory.ObjectProvider<
          f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository> academyRepository,
      org.springframework.beans.factory.ObjectProvider<SiteVisitRecordingService> siteVisitRecordingService,
      PublicSiteProperties publicSiteProperties,
      WebSecurityProperties webSecurityProperties,
      ArchivedRoutesProperties archivedRoutesProperties,
      JwtProperties jwtProperties,
      // NewStart 네이버 로그인 STEP 1. spring.security.oauth2.client.registration.naver가 설정된
      // 프로필(prd만 — application-prd.yaml)에서만 이 빈이 존재한다. ObjectProvider로 지연 조회해
      // local/dev/stg/테스트 슬라이스에서는 oauth2Login을 아예 구성하지 않고 기존과 동일하게 둔다.
      org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository>
          clientRegistrationRepositoryProvider,
      // STEP 2. JPA 레포를 갖고 있어 @WebMvcTest 슬라이스엔 없을 수 있다 — 다른 JPA 의존 빈들과
      // 동일하게 ObjectProvider로 지연 조회한다.
      org.springframework.beans.factory.ObjectProvider<
              f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NaverAccountResolutionService>
          naverAccountResolutionServiceProvider) throws Exception {

    // Filter 빈은 서블릿 컨테이너에 자동 등록돼 이중 실행되므로, 필터체인 안에서만 돌도록 여기서 직접 생성한다.
    // AcademyRepository는 ObjectProvider 지연 주입: @WebMvcTest 슬라이스(JPA 레포 없음)에서도 이 설정이 뜨게.
    SubdomainAcademyFilter subdomainAcademyFilter =
        new SubdomainAcademyFilter(academyRepository, publicSiteProperties);
    ArchivedRouteAliasFilter archivedRouteAliasFilter =
        new ArchivedRouteAliasFilter(archivedRoutesProperties);
    JwtAuthenticationFilter jwtAuthenticationFilter =
        new JwtAuthenticationFilter(tokenProvider, cookieUtil);
    // Chart Research Agent 전용 Bearer 토큰 필터. CHART_AGENT_TOKEN 미설정 시 비활성화.
    // 서블릿 자동 등록 이중 실행 방지 + @WebMvcTest 슬라이스 호환을 위해 여기서 직접 생성한다.
    // 테스트에서는 TestPropertySource의 CHART_AGENT_TOKEN 으로 주입된다.
    ChartAgentTokenFilter chartAgentTokenFilter =
        new ChartAgentTokenFilter(environment.getProperty(ChartAgentTokenFilter.ENV_VAR));
    ArchivedRouteAccessFilter archivedRouteAccessFilter =
        new ArchivedRouteAccessFilter(archivedRoutesProperties);
    OriginWhitelistFilter originWhitelistFilter =
        new OriginWhitelistFilter(webSecurityProperties);
    // Form login is disabled because authentication is handled by /api/auth/login + JWT cookies.
    // Keep a disabled standard anchor in the chain so custom-filter order remains inspectable and
    // deterministic relative to Spring Security's canonical authentication/authorization stages.
    UsernamePasswordAuthenticationFilter usernamePasswordAuthenticationFilter =
        new UsernamePasswordAuthenticationFilter();
    usernamePasswordAuthenticationFilter.setRequiresAuthenticationRequestMatcher(request -> false);
    SiteVisitFilter siteVisitFilter = new SiteVisitFilter(siteVisitRecordingService);

    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(e -> e
            // LMS SSR 화면(/lms/{handle}/**)의 익명 브라우저 요청만 랜딩("/")으로 302한다.
            // 주소창 top-level GET에 빈 401을 주면 사용자가 흰 에러 페이지를 보기 때문이다.
            // 목적지가 "/"인 이유: LMS 보호 화면은 로그인 상태에서만 쓰는 화면이고,
            // 로그아웃도 "/"로 보낸다(static/js/account_menu.js). 비로그인 목적지를 한 곳으로 맞춘다.
            // (/login은 로그인 페이지가 아니다 — 로그인은 공통 모달이다, docs/API.md)
            // ⚠ 명시적 .authenticationEntryPoint(...)를 함께 두면 아래 default 매핑이 전부 무시된다.
            //   ExceptionHandlingConfigurer가 그쪽을 우선하므로 절대 되살리지 말 것.
            .defaultAuthenticationEntryPointFor(
                (request, response, ex) -> response.sendRedirect("/"),
                new AndRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher("/lms/**"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            // Wiki 랜딩(/wiki)은 공개하지만 실제 자료 화면(/wiki/**)은 회원 전용이다.
            // 원래 내부 경로를 안전하게 URL-encode해 랜딩 로그인 모달로 전달한다.
            .defaultAuthenticationEntryPointFor(
                (request, response, ex) -> {
                  String target = request.getRequestURI();
                  if (request.getQueryString() != null) target += "?" + request.getQueryString();
                  response.sendRedirect("/?login=required&returnUrl="
                      + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8));
                },
                new AndRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher("/wiki/**"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            // Problem Generator 화면은 목적지를 "/"로 유지하되(비로그인 목적지 통일) 이유를 실어 보낸다.
            // 마커가 없으면 사용자는 아무 안내 없이 랜딩으로 튕겨 원인을 알 수 없다.
            // 랜딩(static/js/landing_buildit.js)이 이 값을 읽어 안내 + 공통 로그인 모달을 연다.
            .defaultAuthenticationEntryPointFor(
                (request, response, ex) -> response.sendRedirect("/?login=required"),
                new AndRequestMatcher(
                    PathPatternRequestMatcher.withDefaults().matcher("/problem-generator/**"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            // 그 외 전부 기존 계약 유지 — 인증 실패 시 401. 프론트가 401 받으면 /home으로 이동.
            .defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), AnyRequestMatcher.INSTANCE)
            .accessDeniedHandler(conversationChatAccessDeniedHandler()))
        .authorizeHttpRequests(auth -> auth
            // StreamingResponseBody 완료 redispatch만 허용한다. 최초 REQUEST 인가 정책은 그대로 적용된다.
            .requestMatchers(request ->
                request.getDispatcherType() == DispatcherType.ASYNC
                    && LANDING_VIDEO_ASYNC_PATH.matcher(request.getServletPath()).matches())
            .permitAll()
            .requestMatchers("/conversation/chat", "/conversation/chat.html")
            .hasAnyRole("ADMIN", "TUTOR")
            .requestMatchers("/tutor/conversation_dashboard", "/tutor/conversation/dashboard", "/tutor/conversation/chat", "/tutor/conversation/chat.html", "/tutor/conversation/materials", "/tutor/conversation/materials.html", "/tutor/conversations/**")
            .hasRole("TUTOR")
            // 랜딩 편집 화면은 기존 public admin view 그룹보다 먼저 차단한다.
            .requestMatchers("/admin/landing", "/admin/chart").hasRole("ADMIN")
            // Easy 회계·학생원장 SSR 화면 — ADMIN 전용. 데이터는 /api/admin/easy/** (ROLE_ADMIN).
            .requestMatchers("/easy", "/easy/**").hasRole("ADMIN")
            // NewStart 운영자 화면 — ADMIN 전용. 메뉴 숨김이 아니라 서버 규칙으로 차단한다.
            // 반드시 아래 catch-all permitAll의 /admin/** 나열보다 위에 둔다(first-match-wins).
            .requestMatchers("/admin/newstart", "/admin/newstart/**").hasRole("ADMIN")

            // ── 학원 LMS SSR 화면 ──────────────────────────────────────────
            // ⚠ 이 블록은 반드시 아래 .anyRequest().permitAll()보다 위에 있어야 한다.
            //   catch-all이 permitAll이라, 매처가 없으면 운영자 화면까지 전부 공개된다.
            // ⚠ 패턴은 "정규화된 로컬 형태"(/lms/{handle}/...) 한 벌만 쓴다.
            //   prd의 {handle}.{base-domain}/lms/... 는 SubdomainAcademyFilter가 인가 이전에
            //   이 형태로 rewrite하므로, prd 형태를 중복 등록하면 죽은 줄이 된다.
            // ⚠ 매처 순서 고정 — first-match-wins라 좁은 것부터 넓은 것 순.
            //   slug/handle 단위 소유권은 SecurityConfig가 표현할 수 없다(role만 가능).
            //   실제 owner 검증은 AcademyLmsManagePageController가 담당한다.
            .requestMatchers(
                "/lms/*/manage",
                "/lms/*/contents",
                "/lms/*/midterm-live")
            .hasRole("TUTOR")
            .requestMatchers(
                "/lms/*/cart",
                "/lms/*/checkout",
                "/lms/*/my",
                "/lms/*/lecture/*")
            .authenticated()
            .requestMatchers(
                "/lms/*",
                "/lms/*/courses/*")
            .permitAll()

            // ── Problem Generator SSR 화면 ─────────────────────────────────
            // BuildIt Wiki MCP 서버: 외부 MCP client(Claude/GPT 등)는 브라우저 로그인
            // 세션이 없으므로 아래 /problem-generator/** 규칙(hasAnyRole)에 걸리면 절대
            // 접근할 수 없다. 실제 인증은 FastAPI 쪽 mcp_server.py의 Bearer 토큰
            // 미들웨어가 전담하므로, 이 경로만 Spring 레벨에서는 permitAll로 열어둔다.
            // ⚠ 반드시 아래 넓은 /problem-generator/** 규칙보다 먼저 와야 한다(좁은 것 우선).
            .requestMatchers("/problem-generator/wiki/api/mcp/**")
            .permitAll()
            // catch-all permitAll보다 위에서 닫는다. 화면은 로그인한 STUDENT/TUTOR/ADMIN 전용이다.
            .requestMatchers("/problem-generator/**")
            .hasAnyRole("STUDENT", "TUTOR", "ADMIN")
            // 위 패턴에 걸리지 않는 LMS 하위 신규 경로는 fail-closed(최소 인증).
            // 이 줄이 없으면 catch-all permitAll로 떨어진다.
            .requestMatchers("/lms/**").authenticated()

            // Wiki 랜딩만 공개한다. 자료·문항·Thread 화면은 로그인 회원 전용이다.
            .requestMatchers(request -> request.getRequestURI().startsWith("/wiki/"))
            .authenticated()

            // 정적 리소스 + SSR 뷰 모두 public
            .requestMatchers(
                "/", "/home",
                "/eduholic",
                "/login",
                "/login.html",
                "/register",
                "/signin/reset/password",
                "/signin/reset/password/confirm",
                "/tutor/academy",
                "/tutor/academy/setup",
                // 뷰 자체에는 SSR 데이터가 없다. 영상 목록/업로드/삭제는 전부 /api/tutor/** (TUTOR role)라
                // 비인증 접근은 빈 셸만 보고 API에서 막힌다. 기존 tutor 뷰 경로들과 동일한 취급.
                "/tutor/academy/videos",
                "/tutor/academy/posts",
                "/tutor/academy/posts/**",
                // JWT principal + TUTOR role redirect guard는 AcademyBuilderPageController가 담당한다.
                // permitAll을 유지해야 비인증 요청도 컨트롤러에서 랜딩("/")으로 리다이렉트할 수 있다.
                "/builder",
                "/tutor/lesson/**",
                "/tutor/lessons/**",
                "/academies",
                "/academies/**",
                "/academy-posts",
                "/community",
                "/community/posts/**",
                "/analyzes",
                "/analyzes/**",
                "/wiki",
                "/admin/wiki-originals",
                "/admin/wiki-analysis",
                "/chart",
                "/chart/educators/**",
                "/lessons",
                "/stdt_dashboard",
                "/tutor_dashboard",
                "/conversation/dashboard",
                "/conversation/materials",
                "/conversation/exam-materials",
                "/conversation/study-materials",
                "/conversation/tutor-materials",
                "/conversation/homework",
                "/conversation/report",
                "/conversation/review",
                "/exam_review",
                "/admin/login",
                "/admin/dashboard",
                "/admin/students",
                "/admin/members",
                "/admin/academies",
                "/admin/analyzes",
                "/admin/lesson-communities",
                "/css/**", "/js/**", "/img/**", "/favicon.ico",
                "/error",
                // Swagger / OpenAPI
                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                // Actuator health (배포 헬스체크용)
                "/actuator/health",
                "/robots.txt", "/sitemap.xml"
            ).permitAll()

            // 인증 관련 public API
            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()  // 빈 쿠키 덮는 건 인증 무관
            .requestMatchers(HttpMethod.POST, "/api/auth/login-id/find").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/password-reset/request").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/password-reset/confirm").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/admin/password-change").permitAll()  // 운영 ADMIN 전용 변경(서버가 대상·현재비번 검증)
            .requestMatchers(HttpMethod.POST, "/api/users/students").permitAll()  // 회원가입
            .requestMatchers(HttpMethod.POST, "/api/users/tutors").permitAll()  // 강사 회원가입
            .requestMatchers(HttpMethod.GET, "/api/users/check-login-id").permitAll()  // 아이디 중복 확인
            .requestMatchers(HttpMethod.GET, "/api/subjects").permitAll()  // 과목 마스터 lookup
            .requestMatchers(HttpMethod.GET, "/api/academies").permitAll()  // 공개 Academy 목록
            .requestMatchers(HttpMethod.GET, "/api/academies/**").permitAll()  // 공개 Academy 조회/이미지
            .requestMatchers(HttpMethod.HEAD, "/api/academies/**").permitAll()  // 공개 영상 메타데이터
            .requestMatchers(HttpMethod.POST, "/api/academies/*/visits").permitAll()  // 공개 방문 집계
            .requestMatchers(HttpMethod.GET, "/api/academy-posts").permitAll()  // 공개 Academy 블로그 포스트 모아보기
            // 일반 공개 커뮤니티 read는 유지하되 Wiki scope 목록은 회원 전용으로 먼저 닫는다.
            // Wiki 게시글 상세는 데이터의 boardType을 알아야 하므로 CommunityController가 2차 방어한다.
            .requestMatchers(request ->
                HttpMethod.GET.matches(request.getMethod())
                    && "/api/open-community/posts".equals(request.getRequestURI())
                    && "wiki".equalsIgnoreCase(request.getParameter("scope")))
            .authenticated()
            .requestMatchers(HttpMethod.GET, "/api/open-community/posts", "/api/open-community/posts/**").permitAll()
            // Wiki 게시글과 일반 커뮤니티가 파일 저장 endpoint를 공유하며 URL만으로 소유 scope를 판별할 수 없다.
            // 보호 자료 우회 노출을 막기 위해 이미지/첨부 원본 serve는 로그인 회원에게 통일한다.
            .requestMatchers(HttpMethod.GET,
                "/api/open-community/images/**",
                "/api/open-community/attachments/*/file").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/analyzes", "/api/analyzes/**").permitAll()  // 공개 시험 분석 조회
            // /api/wiki/** 실제 자료·문항·Thread·미리보기·다운로드는 아래
            // /api/** catch-all에서 모두 인증한다. 종류별 GUEST 예외를 두지 않는다.
            .requestMatchers(HttpMethod.POST, "/api/wiki/visits").permitAll()  // /wiki 방문 비컨(익명 포함)
            // 공개 /wiki 소개에 필요한 메타 aggregate만 허용한다.
            // home-feed는 controller에서 익명 응답의 Thread/LIVE/회원 활동을 제거한다.
            .requestMatchers(HttpMethod.GET, "/api/wiki/home-feed").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/wiki/popular-downloads").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/chart/**").permitAll()  // 공개 BUILD IT CHART 조회
            .requestMatchers(HttpMethod.GET, "/api/lessons/search").permitAll()  // 공개 수업 검색
            // 공개 랜딩 managed stream은 GET만 익명 허용한다. 같은 prefix의 write는 열지 않는다.
            .requestMatchers(HttpMethod.GET, "/api/landing/videos/*/stream").permitAll()

            // Chart Research Agent (ROLE_CHART_AGENT) — 조사 파이프라인 최소 범위만 허용.
            // Bearer 토큰은 CHART_AGENT_TOKEN 환경변수로 주입되며 미설정 시 이 규칙은
            // 사실상 ADMIN 전용과 동일해진다 (필터가 인증을 설정하지 않으므로).
            // decision/finalize/revert/rebuild/web-draft-requests 는 아래 /api/admin/** 로
            // 떨어져 ROLE_ADMIN 전용으로 남는다. 반드시 /api/admin/** 보다 먼저(좁은 것 우선).
            .requestMatchers(HttpMethod.GET,
                "/api/admin/chart/tools/context",
                "/api/admin/chart/tools/regions/next",
                "/api/admin/chart/tools/scores/preview",
                "/api/admin/chart/tools/review-batches",
                "/api/admin/chart/tools/review-batches/{reviewBatchId}").hasAnyRole("ADMIN", "CHART_AGENT")
            .requestMatchers(HttpMethod.POST,
                "/api/admin/chart/tools/regions/{regionId}/claim",
                "/api/admin/chart/tools/regions/{regionId}/complete",
                "/api/admin/chart/tools/educators/search-existing",
                "/api/admin/chart/tools/research-drafts",
                "/api/admin/chart/tools/review-batches").hasAnyRole("ADMIN", "CHART_AGENT")

            // Admin 전용
            .requestMatchers("/api/admin/**").hasRole("ADMIN")

            // Eduholic 등 학원 공개 홈페이지가 "이 방문자가 이 학원 소유자인가"를 판별하는 조회
            // 전용 엔드포인트(academy_public.js isOwnAcademy). ADMIN 실계정도 서브도메인 공개
            // 페이지에서 이 호출이 401/403 없이 통과해야 한다 — getMine()은 principal이 소유한
            // 학원이 없으면 안전하게 204를 반환할 뿐 다른 tutor의 데이터를 주거나 쓰기 권한을
            // 주지 않는다. 반드시 아래 넓은 /api/tutor/** 규칙보다 먼저(좁은 것 우선).
            .requestMatchers(HttpMethod.GET, "/api/tutor/academy/me").hasAnyRole("TUTOR", "ADMIN")

            // Tutor 전용
            .requestMatchers("/api/tutor/**").hasRole("TUTOR")

            // 그 외 모든 /api/**는 인증 필요
            .requestMatchers("/api/**").authenticated()

            .anyRequest().permitAll()
        )
        // Each custom filter is ordered relative to the previous one so registration order cannot
        // silently change the security contract.
        .addFilterBefore(subdomainAcademyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(archivedRouteAliasFilter, SubdomainAcademyFilter.class)
        .addFilterAfter(jwtAuthenticationFilter, ArchivedRouteAliasFilter.class)
        .addFilterAfter(chartAgentTokenFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(archivedRouteAccessFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(originWhitelistFilter, ArchivedRouteAccessFilter.class)
        .addFilterAfter(usernamePasswordAuthenticationFilter, OriginWhitelistFilter.class)
        .addFilterAfter(siteVisitFilter, UsernamePasswordAuthenticationFilter.class);

    // NewStart 네이버 로그인 STEP 1 (docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md).
    // ClientRegistrationRepository 빈이 없는 환경(local/dev/stg/대부분의 테스트)에서는 oauth2Login을
    // 아예 구성하지 않는다 — /oauth2/authorization/naver, /login/oauth2/code/naver는 이미 위
    // authorizeHttpRequests의 anyRequest().permitAll() catch-all에 걸리므로 별도 인가 규칙 추가가
    // 필요 없다(광범위 permitAll 확장 없이 기존 catch-all만으로 충분함을 확인했다).
    ClientRegistrationRepository clientRegistrationRepository =
        clientRegistrationRepositoryProvider.getIfAvailable();
    if (clientRegistrationRepository != null) {
      http.oauth2Login(oauth2 -> oauth2
          .clientRegistrationRepository(clientRegistrationRepository)
          .authorizationEndpoint(endpoint -> endpoint.authorizationRequestRepository(
              // HttpSession 대신 단명 쿠키에 저장 — 앱 전역 STATELESS 정책을 유지한다.
              new CookieOAuth2AuthorizationRequestRepository(jwtProperties.secureCookie())))
          // 기본값(HttpSessionOAuth2AuthorizedClientRepository)은 access token을 세션에 저장한다.
          // response.id만 쓰고 버리면 되므로 access token을 어디에도 남기지 않는다 — STATELESS 유지.
          .authorizedClientRepository(new NoOpOAuth2AuthorizedClientRepository())
          .userInfoEndpoint(userInfo -> userInfo.userService(new NaverOAuth2UserService()))
          .successHandler(new NaverOAuth2LoginSuccessHandler(
              naverAccountResolutionServiceProvider.getIfAvailable(), tokenProvider, cookieUtil))
          .failureHandler(new NaverOAuth2LoginFailureHandler()));
    }

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private AccessDeniedHandler conversationChatAccessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      String path = request.getRequestURI();
      if ("/conversation/chat".equals(path) || "/conversation/chat.html".equals(path)) {
        response.sendRedirect("/stdt_dashboard");
        return;
      }
      response.sendError(HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase());
    };
  }
}
