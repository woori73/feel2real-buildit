package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.security.web.header.HeaderWriter;

/** Indexing policy only. Authentication and authorization remain in their existing filters. */
public class PrivatePageRobotsHeaderWriter implements HeaderWriter {
  private static final Set<String> PREFIXES = Set.of("/admin", "/api/admin", "/ai-agent-studio", "/api/studio");
  private static final Set<String> PAGES = Set.of("/dashboard", "/my/profile", "/studio-projects");

  @Override public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (PAGES.contains(path) || PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"))) {
      response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
  }
}
