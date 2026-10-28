package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound this endpoint before the shared 200MB media multipart parser spools the body. */
@Component
@Order(-90)
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamUploadLimitFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if ("POST".equals(request.getMethod())
        && request.getRequestURI().matches("/api/exam-intake/requests/[^/]+/documents")) {
      long length = request.getContentLengthLong();
      if (length < 0 || length > 11L * 1024 * 1024) {
        response.setStatus(length < 0 ? 411 : 413);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"message\":\"업로드 요청 크기를 확인해 주세요. 파일당 10MiB 제한입니다.\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
