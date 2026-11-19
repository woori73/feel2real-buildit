package f2r.tutor_solution.private_tutor_solution_back.program;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 프로그램 공개 첫 화면의 "시작하기" 버튼이 쓰는 공통 네이버 로그인 시작점.
 *
 * <p>복귀 URL을 {@code oauth_return_url} 쿠키(5분, 내부 경로만)에 저장하고 기존 네이버 로그인
 * ({@code /oauth2/authorization/naver})으로 보낸다. 로그인 성공 시 {@code NaverOAuth2LoginSuccessHandler}가
 * 이 쿠키를 다시 검증해 원래 기능 화면으로 복귀시킨다. 새 로그인 체계를 만들지 않는다.
 * 등록된 프로그램 영역이나 메인 사이트 일반 화면이 아닌 URL(관리자·API·로그인 화면 등)은 쿠키를 만들지 않는다
 * (기존 기본 이동 경로 유지).
 */
@Controller
public class NaverLoginStartController {

  static final String RETURN_URL_COOKIE = "oauth_return_url";
  static final String NAVER_AUTHORIZATION_PATH = "/oauth2/authorization/naver";
  /** 강사 간편 가입 표시 쿠키. 성공 처리(NaverOAuth2LoginSuccessHandler)가 새 회원을 TUTOR로 만들 때만 읽는다. */
  static final String SIGNUP_ROLE_COOKIE = "oauth_signup_role";

  /** 로그인 뒤 돌아가면 어색하거나 막힌 화면(관리자·API·로그인·가입·계정 화면). 메인 사이트 복귀에서 뺀다. */
  static final java.util.List<String> BLOCKED_PREFIXES = java.util.List.of(
      "/admin", "/api", "/auth", "/login", "/oauth2", "/account", "/signin", "/logout", "/register", "/error");

  /**
   * 복귀 쿠키를 만들 URL인지. 등록 프로그램 영역 또는 메인 사이트(feel2real.co.kr) 일반 화면
   * (2026-11-17: 공용 로그인 창의 네이버 로그인 버튼). 둘 다 내부 경로만 허용한다(open redirect 방지).
   */
  static boolean isAllowedReturnUrl(String url) {
    if (!ReturnUrlPolicy.isSafeInternal(url)) return false;
    if (Program.isRegisteredReturnUrl(url)) return true;
    int q = url.indexOf('?');
    String path = q < 0 ? url : url.substring(0, q);
    return BLOCKED_PREFIXES.stream().noneMatch(p -> path.equals(p) || path.startsWith(p + "/"));
  }

  @GetMapping("/auth/naver/start")
  public void start(
      @RequestParam(name = "returnUrl", required = false) String returnUrl,
      @RequestParam(name = "role", required = false) String role,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    boolean secureRequest = request.isSecure()
        || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    // 회원가입 화면 「강사」 탭이면 강사로 가입시키는 표시를 남기고, 아니면 이전에 남은 표시를 지운다.
    boolean tutor = "tutor".equalsIgnoreCase(role);
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SIGNUP_ROLE_COOKIE, tutor ? "TUTOR" : "")
        .path("/").maxAge(tutor ? 300 : 0).sameSite("Lax").httpOnly(true).secure(secureRequest).build().toString());
    if (isAllowedReturnUrl(returnUrl)) {
      boolean secure = request.isSecure()
          || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
      ResponseCookie cookie = ResponseCookie.from(
              RETURN_URL_COOKIE, URLEncoder.encode(returnUrl, StandardCharsets.UTF_8))
          .path("/")
          .maxAge(300)
          .sameSite("Lax")
          .httpOnly(true)
          .secure(secure)
          .build();
      response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    response.sendRedirect(NAVER_AUTHORIZATION_PATH);
  }
}
