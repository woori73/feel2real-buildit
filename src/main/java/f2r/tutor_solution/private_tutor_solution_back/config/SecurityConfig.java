package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtAuthenticationFilter;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
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
      org.springframework.beans.factory.ObjectProvider<
          f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository> academyRepository,
      PublicSiteProperties publicSiteProperties,
      WebSecurityProperties webSecurityProperties,
      ArchivedRoutesProperties archivedRoutesProperties) throws Exception {

    // Filter 빈은 서블릿 컨테이너에 자동 등록돼 이중 실행되므로, 필터체인 안에서만 돌도록 여기서 직접 생성한다.
    // AcademyRepository는 ObjectProvider 지연 주입: @WebMvcTest 슬라이스(JPA 레포 없음)에서도 이 설정이 뜨게.
    SubdomainAcademyFilter subdomainAcademyFilter =
        new SubdomainAcademyFilter(academyRepository, publicSiteProperties);
    ArchivedRouteAliasFilter archivedRouteAliasFilter =
        new ArchivedRouteAliasFilter(archivedRoutesProperties);
    JwtAuthenticationFilter jwtAuthenticationFilter =
        new JwtAuthenticationFilter(tokenProvider, cookieUtil);
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
                "/lms/*/contents")
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
                "/wiki/**",
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
            .requestMatchers(HttpMethod.POST, "/api/users/students").permitAll()  // 회원가입
            .requestMatchers(HttpMethod.POST, "/api/users/tutors").permitAll()  // 강사 회원가입
            .requestMatchers(HttpMethod.GET, "/api/users/check-login-id").permitAll()  // 아이디 중복 확인
            .requestMatchers(HttpMethod.GET, "/api/subjects").permitAll()  // 과목 마스터 lookup
            .requestMatchers(HttpMethod.GET, "/api/academies").permitAll()  // 공개 Academy 목록
            .requestMatchers(HttpMethod.GET, "/api/academies/**").permitAll()  // 공개 Academy 조회/이미지
            .requestMatchers(HttpMethod.HEAD, "/api/academies/**").permitAll()  // 공개 영상 메타데이터
            .requestMatchers(HttpMethod.POST, "/api/academies/*/visits").permitAll()  // 공개 방문 집계
            .requestMatchers(HttpMethod.GET, "/api/academy-posts").permitAll()  // 공개 Academy 블로그 포스트 모아보기
            // 전체 공개 커뮤니티 read만 permitAll(목록/상세). write/comment/like는 아래 /api/** catch-all로 인증.
            // base가 /api/open-community인 이유는 /api/community/**를 LessonCommunityController가 점유 중이기 때문.
            // ⚠ posts 한정 — /api/open-community/** 로 넓히면 comment 경로까지 공개돼 안 됨.
            .requestMatchers(HttpMethod.GET, "/api/open-community/posts", "/api/open-community/posts/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/open-community/images/**").permitAll()  // 본문 이미지 공개 serve
            .requestMatchers(HttpMethod.GET, "/api/open-community/attachments/*/file").permitAll()  // 게시글 첨부 공개 serve (add/delete는 인증)
            .requestMatchers(HttpMethod.GET, "/api/analyzes", "/api/analyzes/**").permitAll()  // 공개 시험 분석 조회
            .requestMatchers(HttpMethod.GET, "/api/wiki/originals", "/api/wiki/originals/**").permitAll()  // 공개 모의고사 원본 조회/다운로드
            .requestMatchers(HttpMethod.GET, "/api/wiki/analysis-materials", "/api/wiki/analysis-materials/**").permitAll()  // 공개 분석 자료 조회/다운로드
            // 문항별 Question Wiki: 목록/상세/Context/첨부 다운로드는 비로그인 read. write(글/답글/수정/삭제/첨부)는
            // 아래 /api/** catch-all 로 인증, 자료 링크 관리는 /api/admin/wiki/questions/** (ROLE_ADMIN).
            .requestMatchers(HttpMethod.GET, "/api/wiki/questions", "/api/wiki/questions/**").permitAll()  // 공개 문항 Wiki 조회/Context/첨부 다운로드
            // 모의고사 학습 자료(회원 업로드): 목록/제목만 공개. 다운로드(/{id}/download)·업로드(POST)는
            // 아래 /api/** catch-all 로 인증(회원 전용). 삭제/관리는 /api/admin/wiki/learning-materials (ROLE_ADMIN).
            // ⚠ 정확 경로만 — "/**" 로 넓히면 다운로드까지 공개돼 안 된다.
            .requestMatchers(HttpMethod.GET, "/api/wiki/learning-materials").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/wiki/visits").permitAll()  // /wiki 방문 비컨(익명 포함)
            .requestMatchers(HttpMethod.GET, "/api/chart/**").permitAll()  // 공개 BUILD IT CHART 조회
            .requestMatchers(HttpMethod.GET, "/api/lessons/search").permitAll()  // 공개 수업 검색
            // 공개 랜딩 managed stream은 GET만 익명 허용한다. 같은 prefix의 write는 열지 않는다.
            .requestMatchers(HttpMethod.GET, "/api/landing/videos/*/stream").permitAll()

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
        .addFilterAfter(archivedRouteAccessFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(originWhitelistFilter, ArchivedRouteAccessFilter.class)
        .addFilterAfter(usernamePasswordAuthenticationFilter, OriginWhitelistFilter.class);

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
