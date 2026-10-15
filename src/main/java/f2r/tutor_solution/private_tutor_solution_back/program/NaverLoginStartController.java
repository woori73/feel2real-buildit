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
 * 등록된 프로그램 영역이 아닌 URL은 쿠키를 만들지 않는다(기존 기본 이동 경로 유지).
 */
@Controller
public class NaverLoginStartController {

  static final String RETURN_URL_COOKIE = "oauth_return_url";
  static final String NAVER_AUTHORIZATION_PATH = "/oauth2/authorization/naver";

  @GetMapping("/auth/naver/start")
  public void start(
      @RequestParam(name = "returnUrl", required = false) String returnUrl,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    if (ReturnUrlPolicy.isSafeInternal(returnUrl) && Program.isRegisteredReturnUrl(returnUrl)) {
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
