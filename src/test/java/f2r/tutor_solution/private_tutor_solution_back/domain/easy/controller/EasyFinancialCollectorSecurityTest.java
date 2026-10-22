package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFinancialCollectorDtos.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialCollectorService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EasyFinancialCollectorController.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.easy.financial-collector.enabled=true",
    "app.jwt.secret=12345678901234567890123456789012", "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN", "app.jwt.secure-cookie=false"
})
class EasyFinancialCollectorSecurityTest {
  private static final String ROOT = "/api/admin/easy/financial-collector";
  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider jwt;
  @MockitoBean EasyFinancialCollectorService service;
  @MockitoBean AcademyRepository academyRepository;

  @Test
  void givenAnonymousOrTutor_whenReadingOrStaging_thenDenyBeforeService() throws Exception {
    // given / when / then
    mvc.perform(get(ROOT + "/capabilities")).andExpect(status().isUnauthorized());
    mvc.perform(get(ROOT + "/capabilities").cookie(cookie("TUTOR"))).andExpect(status().isForbidden());
    mvc.perform(post(ROOT + "/previews/demo/stage?businessId=1").cookie(cookie("STUDENT"))
        .contentType("application/json").content("{\"reviewed\":true}")).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void givenAdmin_whenCapabilitiesRequested_thenNoLiveConnectorIsAdvertised() throws Exception {
    mvc.perform(get(ROOT + "/capabilities").cookie(cookie("ADMIN")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].browserDownloadEnabled").value(false));
  }

  @Test
  void givenAdmin_whenStatusRequested_thenBusinessAndPrincipalReachAccessBoundary() throws Exception {
    when(service.get(eq(1L), eq("demo"), any())).thenReturn(mock(Status.class));
    mvc.perform(get(ROOT + "/previews/demo?businessId=1").cookie(cookie("ADMIN"))).andExpect(status().isOk());
    verify(service).get(eq(1L), eq("demo"), argThat(p -> p.userId().equals(1L) && p.role().equals("ADMIN")));
  }

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", jwt.issue(1L, "sample-user", role));
  }
}
