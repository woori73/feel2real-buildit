package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsResponse.PeriodMetric;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AdminAnalyticsBreakdownService;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AdminAnalyticsService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("AdminAnalyticsController — /api/admin/** 는 ADMIN 전용 (SecurityConfig)")
@WebMvcTest(controllers = AdminAnalyticsController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class AdminAnalyticsControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean AdminAnalyticsService service;
  @MockitoBean AdminAnalyticsBreakdownService breakdownService;
  @MockitoBean AcademyRepository academyRepository;

  @BeforeEach
  void stubs() {
    PeriodMetric zero = new PeriodMetric(0, 0, 0, 0);
    when(service.summary()).thenReturn(new AdminAnalyticsResponse(
        zero, zero, zero, zero, zero, zero, zero, null,
        List.of(), List.of(), List.of(), List.of(), List.of()));
  }

  @Test
  @DisplayName("given 익명 when 조회 then 401")
  void summary_anonymous_unauthorized() throws Exception {
    mockMvc.perform(get("/api/admin/analytics/summary")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given STUDENT/TUTOR when 조회 then 403 — 다른 사용자 방문/다운로드 내역 열람 불가")
  void summary_nonAdmin_forbidden() throws Exception {
    mockMvc.perform(get("/api/admin/analytics/summary").cookie(token("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/analytics/summary").cookie(token("TUTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given ADMIN when 조회 then 200")
  void summary_admin_ok() throws Exception {
    mockMvc.perform(get("/api/admin/analytics/summary").cookie(token("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given 익명·STUDENT·TUTOR when 세분화 통계를 조회하면 then 401/403, ADMIN만 200")
  void breakdown_adminOnly() throws Exception {
    mockMvc.perform(get("/api/admin/analytics/breakdown")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/admin/analytics/breakdown").cookie(token("STUDENT"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/analytics/breakdown").cookie(token("TUTOR"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/analytics/breakdown").cookie(token("ADMIN"))).andExpect(status().isOk());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }
}
