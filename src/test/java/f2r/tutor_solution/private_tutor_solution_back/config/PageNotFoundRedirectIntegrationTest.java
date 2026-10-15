package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = PageNotFoundRedirectIntegrationTest.TestApplication.class
)
@TestPropertySource(properties = {
    "spring.profiles.active=test",
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("404 missing-route handling test")
class PageNotFoundRedirectIntegrationTest {

  private final HttpClient httpClient = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

  @LocalServerPort
  int port;

  @Autowired
  JwtTokenProvider tokenProvider;

  @Test
  @DisplayName("GET missing page route returns 404 (not redirect to /home)")
  void get_missing_page_route_returns_404() throws Exception {
    HttpResponse<Void> response = request("GET", "/some-missing-page");

    assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  @DisplayName("HEAD missing page route returns 404 (not redirect to /home)")
  void head_missing_page_route_returns_404() throws Exception {
    HttpResponse<Void> response = request("HEAD", "/some-missing-page");

    assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  @DisplayName("GET missing API route remains 404")
  void get_missing_api_route_remains_404() throws Exception {
    HttpResponse<Void> response = request(
        "GET",
        "/api/missing",
        "Cookie",
        "ACCESS_TOKEN=" + tokenProvider.issue(1L, "test-student", "STUDENT"));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  @DisplayName("POST missing page route remains 404")
  void post_missing_page_route_remains_404() throws Exception {
    HttpResponse<Void> response = request("POST", "/some-missing-page");

    assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  private HttpResponse<Void> request(String method, String path, String... headers) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .method(method, HttpRequest.BodyPublishers.noBody());
    if (headers.length > 0) {
      builder.headers(headers);
    }
    return httpClient.send(
        builder.build(),
        HttpResponse.BodyHandlers.discarding());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(exclude = {
      DataSourceAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class
  })
  @Import({
      PageNotFoundRedirectController.class,
      SecurityConfig.class,
      WebMvcConfig.class,
      AcademyPathRedirectInterceptor.class,
      RoleModelInterceptor.class,
      JwtTokenProvider.class,
      CookieUtil.class
  })
  static class TestApplication {
  }
}
