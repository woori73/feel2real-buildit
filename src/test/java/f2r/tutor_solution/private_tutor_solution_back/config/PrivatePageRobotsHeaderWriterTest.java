package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PrivatePageRobotsHeaderWriterTest {
  @Test void givenPrivatePageOrDeniedApi_whenHeadersWritten_thenNoindexWithoutChangingStatus() {
    for (String path : new String[]{"/admin/hub", "/admin/login", "/api/admin/users", "/api/studio/catalog", "/ai-agent-studio", "/studio-projects", "/tutor/academy", "/stdt_dashboard", "/tutor_dashboard", "/dashboard", "/dashboard/assistant", "/dashboard/info", "/my/profile", "/old-home", "/wemade/login", "/api/analyzes/9/content", "/api/academies", "/api/studio-public", "/api", "/signin/reset/password", "/signin/reset/password/confirm", "/chart/educators/5", "/manual/index.html", "/manual/videos/intro.mp4"}) {
      for (int status : new int[]{200, 302, 401, 403}) {
        var response = new MockHttpServletResponse(); response.setStatus(status);
        new PrivatePageRobotsHeaderWriter().writeHeaders(new MockHttpServletRequest("GET", path), response);
        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
        assertThat(response.getStatus()).isEqualTo(status);
      }
    }
  }
  @Test void givenPublicPageOrSimilarPrefix_whenHeadersWritten_thenExistingPolicyUnchanged() {
    for (String path : new String[]{"/", "/home", "/wiki", "/academies", "/analyzes/9", "/sitemap.xml", "/robots.txt", "/administrator", "/apis", "/signing", "/chart", "/chart/rankings/1", "/manuals", "/exam", "/wiki"}) {
      var response = new MockHttpServletResponse();
      new PrivatePageRobotsHeaderWriter().writeHeaders(new MockHttpServletRequest("GET", path), response);
      assertThat(response.getHeader("X-Robots-Tag")).isNull();
    }
  }
}
