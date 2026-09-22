package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService.DiscoveryResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 수동 discovery 트리거 — /api/admin/** ROLE_ADMIN 계약 회귀.
 * 익명 401 / 비관리자 403 / ADMIN 200. 트리거 결과도 REVIEW까지만 생성된다.
 */
@DisplayName("NewStart discovery 수동 트리거 — ADMIN 전용")
@WebMvcTest(controllers = NewStartOfficialUpdateAdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartOfficialUpdateDiscoveryTriggerSecurityTest {

  private static final String RUN_URL = "/api/admin/newstart/updates/discovery/run";

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean NewStartOfficialUpdateService updateService;
  @MockitoBean NewStartOfficialUpdateDiscoveryService discoveryService;

  @Test
  @DisplayName("익명 → 401")
  void anonymousBlocked() throws Exception {
    mockMvc.perform(post(RUN_URL)).andExpect(status().isUnauthorized());
    verify(discoveryService, never()).runDiscoveryAllSources(any());
  }

  @Test
  @DisplayName("비관리자(STUDENT) → 403")
  void nonAdminBlocked() throws Exception {
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student01", "STUDENT"));
    mockMvc.perform(post(RUN_URL).cookie(student)).andExpect(status().isForbidden());
    verify(discoveryService, never()).runDiscoveryAllSources(any());
  }

  @Test
  @DisplayName("ADMIN → 200, discovery 위임, 자동 발행 금지")
  void adminAllowedAndDelegatesToDiscovery() throws Exception {
    when(discoveryService.runDiscoveryAllSources(any()))
        .thenReturn(DiscoveryResult.success(SourceType.NEWSTART_FUND, 3, 2, 1, 0));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));

    mockMvc.perform(post(RUN_URL).cookie(admin)).andExpect(status().isOk());

    verify(discoveryService).runDiscoveryAllSources(any());
    verify(updateService, never()).publish(any(), any());
    verify(updateService, never()).createReview(any());
  }
}
