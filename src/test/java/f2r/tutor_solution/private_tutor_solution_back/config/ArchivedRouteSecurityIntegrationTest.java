package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtAuthenticationFilter;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ArchivedRouteSecurityIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.profiles.active=test",
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.archived.enabled=true",
    "app.archived.require-admin=true",
    "app.security.allowed-origins[0]=feel2real.co.kr"
})
@DisplayName("Archived route single-request security integration")
class ArchivedRouteSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @Autowired FilterChainProxy filterChainProxy;

  @Test
  @DisplayName("Given ADMIN, When archived ADMIN endpoint is requested, Then target handler responds")
  void adminCanReachArchivedAdminTarget() throws Exception {
    // given, when & then
    mockMvc.perform(get("/archived/api/admin/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string("admin-get"));
  }

  @Test
  @DisplayName("Given missing or non-admin auth, When archived endpoint is requested, Then gate denies")
  void archivedAccessGateEnforcesAuthenticationAndAdmin() throws Exception {
    // given, when & then
    mockMvc.perform(get("/archived/api/admin/archive-probe"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/archived/api/admin/archive-probe").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Given ADMIN, When archived TUTOR endpoint is requested, Then target role still denies")
  void targetAuthorizationComposesWithArchiveGate() throws Exception {
    // given, when & then
    mockMvc.perform(get("/archived/api/tutor/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Given allowed Origin, When archived POST runs, Then method body and query reach target")
  void allowedArchivedWritePreservesRequest() throws Exception {
    // given, when & then
    mockMvc.perform(post("/archived/api/admin/archive-probe?mode=kept")
            .cookie(token("ADMIN"))
            .header("Origin", "https://admin.feel2real.co.kr")
            .contentType(MediaType.TEXT_PLAIN)
            .content("payload"))
        .andExpect(status().isOk())
        .andExpect(content().string("payload:kept"));
  }

  @Test
  @DisplayName("Given missing Origin, When archived POST runs, Then auth gate precedes Origin gate")
  void statusPrecedenceIsAuthenticationThenOrigin() throws Exception {
    // given, when & then
    mockMvc.perform(post("/archived/api/admin/archive-probe")
            .contentType(MediaType.TEXT_PLAIN)
            .content("payload"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/archived/api/admin/archive-probe")
            .cookie(token("ADMIN"))
            .contentType(MediaType.TEXT_PLAIN)
            .content("payload"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Given context path, When archived write runs, Then canonical target remains protected")
  void contextPathArchivedWriteUsesCanonicalApiPath() throws Exception {
    // given, when & then
    mockMvc.perform(post("/app/archived/api/admin/archive-probe?mode=context")
            .contextPath("/app")
            .cookie(token("ADMIN"))
            .header("Origin", "https://feel2real.co.kr")
            .contentType(MediaType.TEXT_PLAIN)
            .content("body"))
        .andExpect(status().isOk())
        .andExpect(content().string("body:context"));
  }

  @Test
  @DisplayName("Given encoded suffix under literal API, When requested, Then target receives decoded value")
  void encodedSuffixRetainsTargetRouting() throws Exception {
    // given, when & then
    mockMvc.perform(get(URI.create("/archived/api/admin/archive-probe/value%20with%20space"))
            .cookie(token("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string("value with space"));
  }

  @Test
  @DisplayName("Given ambiguous or nested route, When requested, Then target is never aliased")
  void ambiguousAndNestedRoutesAreRejected() throws Exception {
    // given, when & then
    mockMvc.perform(get("/archived/%2561pi/admin/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/archived/archived/api/admin/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/archived-preview/api/admin/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Given default firewall, When matrix paths are requested, Then they are rejected")
  void defaultFirewallRejectsMatrixPaths() throws Exception {
    // given, when & then
    mockMvc.perform(post("/api;v=1/admin/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/archived/api;v=1/admin/archive-probe").cookie(token("ADMIN")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Given real security chain, When inspected, Then complete custom order is deterministic")
  void securityFilterOrderIsDeterministic() {
    // given
    assertThat(filterChainProxy.getFilterChains()).hasSize(1);
    List<Class<?>> filterTypes = filterChainProxy.getFilterChains().getFirst().getFilters().stream()
        .map(Object::getClass)
        .toList();

    // when
    List<Class<?>> expectedOrder = List.of(
        SubdomainAcademyFilter.class,
        ArchivedRouteAliasFilter.class,
        JwtAuthenticationFilter.class,
        ArchivedRouteAccessFilter.class,
        OriginWhitelistFilter.class,
        UsernamePasswordAuthenticationFilter.class,
        AuthorizationFilter.class);

    // then
    int previous = -1;
    for (Class<?> filterType : expectedOrder) {
      int current = filterTypes.indexOf(filterType);
      assertThat(current).as(filterType.getSimpleName()).isGreaterThan(previous);
      previous = current;
    }
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "archive-" + role.toLowerCase(), role));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(exclude = {
      DataSourceAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class
  })
  @Import({
      SecurityConfig.class,
      JwtTokenProvider.class,
      CookieUtil.class,
      ProbeController.class
  })
  static class TestApplication {
  }

  @RestController
  static class ProbeController {

    @GetMapping("/api/admin/archive-probe")
    String adminGet() {
      return "admin-get";
    }

    @PostMapping("/api/admin/archive-probe")
    String adminPost(@RequestBody String body, @RequestParam(defaultValue = "none") String mode) {
      return body + ":" + mode;
    }

    @GetMapping("/api/admin/archive-probe/{value}")
    String encoded(@PathVariable String value) {
      return value;
    }

    @GetMapping("/api/tutor/archive-probe")
    String tutorGet() {
      return "tutor-get";
    }
  }
}
