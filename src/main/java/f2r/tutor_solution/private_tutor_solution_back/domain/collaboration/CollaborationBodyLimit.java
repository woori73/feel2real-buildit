package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound chunked bodies before JSON deserialization, independently of Content-Length. */
@Component
@Order(-90)
public class CollaborationBodyLimit extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/api/collaboration/")
        || request.getMethod().equals("GET")
        || request.getMethod().equals("HEAD")) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getRequestURI().equals("/api/collaboration/files")
        && request.getContentType() != null
        && request.getContentType().startsWith("multipart/form-data")) {
      chain.doFilter(request, response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(65537);
    if (body.length > 65536) {
      response.setStatus(413);
      response.setCharacterEncoding("UTF-8");
      response.setContentType("application/json");
      response.setHeader("Cache-Control", "no-store");
      response.getWriter().write("{\"message\":\"요청은 64 KiB 이하입니다.\"}");
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("synchronous JSON request");
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
          }
        },
        response);
  }
}
