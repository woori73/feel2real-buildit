package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse.Members;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse.Visits;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service.WikiOpsOverviewService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service.WikiVisitService;
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

@DisplayName("Wiki 통계 API — 방문 비컨은 공개, 운영 현황은 ADMIN")
@WebMvcTest(controllers = {WikiVisitController.class, WikiOpsOverviewController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiStatsControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean WikiVisitService wikiVisitService;
  @MockitoBean WikiOpsOverviewService wikiOpsOverviewService;
  @MockitoBean AcademyRepository academyRepository;

  @BeforeEach
  void stubs() {
    when(wikiOpsOverviewService.overview()).thenReturn(new WikiOpsOverviewResponse(
        new Visits(1, 2, List.of()), new Members(3, 0, List.of())));
  }

  @Test
  @DisplayName("POST /api/wiki/visits — 익명 허용(204)")
  void visitBeacon_anonymous_ok() throws Exception {
    mockMvc.perform(post("/api/wiki/visits")).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /api/admin/wiki/ops-overview — 익명 401 / 회원 403 / ADMIN 200")
  void opsOverview_adminOnly() throws Exception {
    mockMvc.perform(get("/api/admin/wiki/ops-overview")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/admin/wiki/ops-overview").cookie(token("STUDENT"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/wiki/ops-overview").cookie(token("ADMIN"))).andExpect(status().isOk());
  }

  private Cookie token(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "test-" + role.toLowerCase(), role));
  }
}
