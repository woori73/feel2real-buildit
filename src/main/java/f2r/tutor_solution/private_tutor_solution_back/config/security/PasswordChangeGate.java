package f2r.tutor_solution.private_tutor_solution_back.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 임시 비밀번호로 로그인한 토큰(pwc)이 쓸 수 있는 경로. 그 밖의 화면은 새 비밀번호 설정 화면으로 보내고, API는 403으로 막는다.
 */
public final class PasswordChangeGate {

  public static final String PAGE = "/account/password-change";
  public static final String CHANGE_API = "/api/auth/password-change/temporary";
  public static final String HEADER = "X-Password-Change-Required";

  private static final Set<String> ALLOWED_EXACT = Set.of(
      "GET " + PAGE,
      "POST " + CHANGE_API,
      "POST /api/auth/logout",
      "GET /api/auth/me");
  private static final List<String> STATIC_PREFIXES = List.of(
      "/css/", "/js/", "/images/", "/img/", "/vendor/", "/favicon", "/error");

  private PasswordChangeGate() {}

  public static boolean isAllowed(String method, String path) {
    if (path == null) return false;
    if (ALLOWED_EXACT.contains(method + " " + path)) return true;
    if (!"GET".equals(method) && !"HEAD".equals(method)) return false;
    return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
  }

  /** 화면(GET)은 설정 화면으로 보내고, API·쓰기 요청은 403 + 안내 헤더. */
  static void block(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String path = request.getRequestURI();
    if ("GET".equals(request.getMethod()) && !path.startsWith("/api/")) {
      response.sendRedirect(PAGE);
      return;
    }
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setHeader(HEADER, "true");
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"임시 비밀번호로 로그인했습니다. 새 비밀번호를 먼저 정해 주세요.\",\"redirect\":\"" + PAGE + "\"}");
  }
}
