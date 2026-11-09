package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.security.web.header.HeaderWriter;

/** Indexing policy only. Authentication and authorization remain in their existing filters. */
public class PrivatePageRobotsHeaderWriter implements HeaderWriter {
  // 2026-11-09: /api 전체(JSON·내부 미리보기 — 공개 페이지와 내용 중복)와 /signin(비밀번호 재설정)도 검색 제외.
  private static final Set<String> PREFIXES = Set.of("/api", "/signin", "/admin", "/api/admin", "/ai-agent-studio", "/api/studio", "/tutor", "/my",
      "/naesin-production-studio", "/api/naesin-production", "/naesin-wiki", "/api/naesin-wiki");
  private static final Set<String> PAGES = Set.of("/old-home", "/wemade/login", "/dashboard", "/my/profile", "/studio-projects", "/stdt_dashboard", "/tutor_dashboard");

  @Override public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (PAGES.contains(path) || PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"))) {
      response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
  }
}
