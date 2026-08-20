package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ArchivedRouteSecurityIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@Import(ArchivedRouteSemicolonSecurityIntegrationTest.SemicolonFirewallConfig.class)
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
@DisplayName("Archived route semicolon-enabled defense-in-depth integration")
class ArchivedRouteSemicolonSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @Test
  @DisplayName("Given semicolons allowed by firewall, Then Origin guards original and alias rejects archived")
  void applicationFiltersRemainSafeWhenFirewallAllowsSemicolons() throws Exception {
    // given
    Cookie admin = new Cookie(
        "ACCESS_TOKEN", tokenProvider.issue(1L, "archive-admin", "ADMIN"));

    // when & then: canonical PathPattern treats the original matrix segment as /api/**.
    mockMvc.perform(post("/api;v=1/admin/archive-probe")
            .cookie(admin)
            .contentType("text/plain")
            .content("body"))
        .andExpect(status().isForbidden());

    // when & then: archived matrix ambiguity is rejected by the alias before target execution.
    mockMvc.perform(post("/archived/api;v=1/admin/archive-probe")
            .cookie(admin)
            .header("Origin", "https://feel2real.co.kr")
            .contentType("text/plain")
            .content("body"))
        .andExpect(status().isBadRequest());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SemicolonFirewallConfig {

    @Bean
    WebSecurityCustomizer semicolonFirewallCustomizer() {
      StrictHttpFirewall firewall = new StrictHttpFirewall();
      firewall.setAllowSemicolon(true);
      return web -> web.httpFirewall(firewall);
    }
  }
}
