package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartSiteToolService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Chart Research Agent 인증 계약 검증 (무DB, CI 실행).
 *
 * <p>given/when/then:
 * <ul>
 *   <li>유효 Bearer + 파이프라인 allowlist → 200
 *   <li>무인증 → 401
 *   <li>오토큰 → 401
 *   <li>유효 Bearer + allowlist 밖(revert/rebuild/decision/finalize) → 401 (토큰 무시, ROLE_ADMIN 전용 유지)
 * </ul>
 */
@WebMvcTest(controllers = ChartSiteToolController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "CHART_AGENT_TOKEN=test-agent-token-123"
})
class ChartSiteToolAgentAuthWebMvcTest {

  private static final String BEARER = "Bearer test-agent-token-123";
  private static final String DRAFT_JSON = "{"
      + "\"regionId\":\"gyeonggi-yongin\",\"name\":\"홍길동\",\"role\":\"원장\","
      + "\"academyName\":\"테스트영어학원\",\"province\":\"경기도\",\"cityDistrict\":\"용인시\","
      + "\"offlineActivityConfirmed\":true,\"currentYearActivityConfirmed\":true,"
      + "\"educatorIdentityConfirmed\":true,\"exclusionFree\":true,"
      + "\"evidence\":[{\"sourceUrl\":\"https://example.com/profile\","
      + "\"evidenceKind\":\"EDUCATIONAL_CONTENT\",\"educationallyInformative\":true}]}";
  private static final String BATCH_JSON =
      "{\"regionId\":\"gyeonggi-yongin\",\"candidateIds\":[],\"summary\":\"t\"}";
  private static final String COMPLETE_JSON =
      "{\"selectedCandidateIds\":[],\"selectedCount\":0,\"rejectedCount\":0,\"note\":\"t\"}";

  @Autowired MockMvc mockMvc;

  @MockitoBean ChartSiteToolService chartSiteToolService;
  @MockitoBean AcademyRepository academyRepository;

  @BeforeEach
  void stubs() {
    when(chartSiteToolService.context()).thenReturn(Map.of("criteriaVersion", "buildit-chart-v1"));
    when(chartSiteToolService.nextRegions(anyInt())).thenReturn(List.of());
    when(chartSiteToolService.claimRegion(any())).thenReturn(Map.of("status", "IN_PROGRESS"));
    when(chartSiteToolService.saveDraft(any())).thenReturn(Map.of("published", false));
    when(chartSiteToolService.previewScore(anyLong())).thenReturn(Map.of("published", false));
    when(chartSiteToolService.submitReview(any(), any(), any())).thenReturn(Map.of());
    when(chartSiteToolService.completeRegion(anyString(), anyList(), any(), anyString())).thenReturn(Map.of());
  }

  @Test
  void agentToken_allowsResearchPipeline() throws Exception {
    mockMvc.perform(get("/api/admin/chart/tools/context")
            .header("Authorization", BEARER))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/admin/chart/tools/regions/next")
            .header("Authorization", BEARER))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/chart/tools/regions/gyeonggi-yongin/claim")
            .header("Authorization", BEARER))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/chart/tools/research-drafts")
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON).content(DRAFT_JSON))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/admin/chart/tools/scores/preview").param("candidateId", "1")
            .header("Authorization", BEARER))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/chart/tools/review-batches")
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON).content(BATCH_JSON))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/chart/tools/regions/gyeonggi-yongin/complete")
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON).content(COMPLETE_JSON))
        .andExpect(status().isOk());
  }

  @Test
  void noAuth_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/chart/tools/context"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/chart/tools/research-drafts")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void wrongToken_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/chart/tools/context")
            .header("Authorization", "Bearer wrong-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void agentToken_deniedOutsideAllowlist() throws Exception {
    // revert / rebuild / decision / finalize 는 allowlist 밖이라 토큰이 무시되고
    // 익명 요청과 동일하게 401 — ROLE_ADMIN 전용 유지, 토큰 오라클 없음
    mockMvc.perform(post("/api/admin/chart/tools/review-batches/1/revert")
            .header("Authorization", BEARER))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/chart/tools/publications/rebuild")
            .header("Authorization", BEARER))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/chart/tools/review-batches/1/decision")
            .header("Authorization", BEARER)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/chart/tools/review-batches/1/finalize")
            .header("Authorization", BEARER))
        .andExpect(status().isUnauthorized());
  }
}
