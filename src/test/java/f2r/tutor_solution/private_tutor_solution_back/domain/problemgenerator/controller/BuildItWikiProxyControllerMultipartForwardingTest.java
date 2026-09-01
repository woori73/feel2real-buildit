package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = BuildItWikiProxyControllerMultipartForwardingTest.MinimalProxyApp.class)
class BuildItWikiProxyControllerMultipartForwardingTest {

  @Configuration
  @EnableAutoConfiguration(exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class,
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class,
      ServletWebSecurityAutoConfiguration.class,
      ManagementWebSecurityAutoConfiguration.class,
  })
  static class MinimalProxyApp {
    @Bean
    BuildItWikiProxyController buildItWikiProxyController() {
      AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
      when(resolver.currentPrincipal()).thenReturn(Optional.of(
          new JwtTokenProvider.AuthenticatedPrincipal(1L, "test-user", "STUDENT")));
      return new BuildItWikiProxyController(
          resolver,
          "wiki-proxy-test-identity-secret-32-bytes-minimum");
    }

    @Bean
    SecurityProperties securityProperties() {
      return new SecurityProperties();
    }
  }

  @LocalServerPort
  private int port;

  private HttpServer fakeFastApi;
  private volatile byte[] capturedBody;
  private volatile String capturedContentType;
  private volatile String capturedAuthorization;
  private volatile String capturedApiKeyHeader;

  @BeforeEach
  void startFakeFastApi() throws IOException {
    fakeFastApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);
    fakeFastApi.createContext("/api/exam-materials", exchange -> {
      capturedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
      capturedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
      capturedApiKeyHeader = exchange.getRequestHeaders().getFirst("X-Api-Key");
      capturedBody = exchange.getRequestBody().readAllBytes();
      byte[] response = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(201, response.length);
      try (var os = exchange.getResponseBody()) {
        os.write(response);
      }
    });
    fakeFastApi.createContext("/api/mcp", exchange -> {
      capturedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
      capturedApiKeyHeader = exchange.getRequestHeaders().getFirst("X-Api-Key");
      capturedBody = exchange.getRequestBody().readAllBytes();
      byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      try (var os = exchange.getResponseBody()) {
        os.write(response);
      }
    });
    fakeFastApi.start();
  }

  @AfterEach
  void stopFakeFastApi() {
    if (fakeFastApi != null) fakeFastApi.stop(0);
  }

  @Test
  void multipartExamMaterialBodyReachesFastApiIntact() throws Exception {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("school", "신구중");
    builder.part("grade", "2학년");
    builder.part("year", "2026");
    builder.part("semester", "2학기");
    builder.part("exam", "중간고사");
    builder.part("core_type", "school_print");
    builder.part("title", "5과");
    builder.part("html_text", "<html><body>학교프린트 본문</body></html>");
    builder.part("json_text", "{}");

    HttpEntity<MultiValueMap<String, HttpEntity<?>>> multipartEntity = new HttpEntity<>(builder.build());
    RestTemplate restTemplate = new RestTemplate();

    ResponseEntity<String> response = restTemplate.postForEntity(
        "http://127.0.0.1:" + port + "/problem-generator/wiki/api/exam-materials",
        multipartEntity,
        String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(capturedContentType).contains("multipart/form-data");
    assertThat(capturedContentType).contains("boundary=");
    assertThat(capturedBody).isNotEmpty();
    String bodyText = new String(capturedBody, StandardCharsets.UTF_8);
    assertThat(bodyText).contains("신구중");
    assertThat(bodyText).contains("core_type");
    assertThat(bodyText).contains("school_print");
    assertThat(bodyText).contains("학교프린트 본문");
  }

  @Test
  void authorizationHeaderIsForwardedForMcpPathWithoutTrailingSlash() {
    postWithAuthorization("/api/mcp");
    assertThat(capturedAuthorization).isEqualTo("Bearer test-placeholder");
  }

  @Test
  void authorizationHeaderIsForwardedForMcpPathWithTrailingSlash() {
    postWithAuthorization("/api/mcp/");
    assertThat(capturedAuthorization).isEqualTo("Bearer test-placeholder");
  }

  @Test
  void authorizationHeaderIsForwardedForMcpSubpath() {
    postWithAuthorization("/api/mcp/session");
    assertThat(capturedAuthorization).isEqualTo("Bearer test-placeholder");
  }

  @Test
  void authorizationHeaderIsNotForwardedForOtherWikiApi() {
    postWithAuthorization("/api/exam-materials");
    assertThat(capturedAuthorization).isNull();
  }

  private void postWithAuthorization(String targetPath) {
    capturedAuthorization = null;
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("test-placeholder");
    headers.setContentType(MediaType.APPLICATION_JSON);
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.postForEntity(
        "http://127.0.0.1:" + port + "/problem-generator/wiki" + targetPath,
        new HttpEntity<>("{}", headers),
        String.class);
  }

  @Test
  void xApiKeyHeaderIsForwardedForMcpPathWithoutTrailingSlash() {
    postWithApiKeyHeader("/api/mcp");
    assertThat(capturedApiKeyHeader).isEqualTo("test-api-key-placeholder");
  }

  @Test
  void xApiKeyHeaderIsForwardedForMcpPathWithTrailingSlash() {
    postWithApiKeyHeader("/api/mcp/");
    assertThat(capturedApiKeyHeader).isEqualTo("test-api-key-placeholder");
  }

  @Test
  void xApiKeyHeaderIsNotForwardedForOtherWikiApi() {
    postWithApiKeyHeader("/api/exam-materials");
    assertThat(capturedApiKeyHeader).isNull();
  }

  private void postWithApiKeyHeader(String targetPath) {
    capturedApiKeyHeader = null;
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Api-Key", "test-api-key-placeholder");
    headers.setContentType(MediaType.APPLICATION_JSON);
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.postForEntity(
        "http://127.0.0.1:" + port + "/problem-generator/wiki" + targetPath,
        new HttpEntity<>("{}", headers),
        String.class);
  }
}
