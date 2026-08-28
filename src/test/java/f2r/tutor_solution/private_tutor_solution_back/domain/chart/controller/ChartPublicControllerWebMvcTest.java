package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCriteria;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorRanking;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRankingList;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartRegionDirectory;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartEducatorSearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartPublicService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartRankingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * PR-C 신규 DI wiring + 매핑을 web slice 로 검증(무DB, CI 실행).
 *   · ChartPublicController ← ChartRankingService, ChartPageController ← ChartEducatorSearchService 주입.
 *   · /api/chart/rankings·rankings/{id}·criteria·regions 매핑 충돌 없음, public(무인증) 접근.
 *   · /chart/educators/{id} 존재하지 않는 교육자는 /chart 로 redirect.
 */
@WebMvcTest(controllers = {ChartPublicController.class, ChartPageController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class ChartPublicControllerWebMvcTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean ChartPublicService chartPublicService;
  @MockitoBean ChartEducatorSearchService chartEducatorSearchService;
  @MockitoBean ChartRankingService chartRankingService;
  @MockitoBean AcademyRepository academyRepository;

  @BeforeEach
  void stubs() {
    when(chartRankingService.nationwide(null))
        .thenReturn(new ChartRankingList("NATIONWIDE", null, null, "buildit-chart-v1", 0, List.of(), "d"));
    when(chartRankingService.region(eq("서울특별시"), eq("강남구")))
        .thenReturn(new ChartRankingList("REGION", "서울특별시", "강남구", "buildit-chart-v1", 0, List.of(), "d"));
    when(chartRankingService.forEducator(anyLong()))
        .thenReturn(ChartEducatorRanking.unranked(1L, "d"));
    when(chartRankingService.criteria())
        .thenReturn(new ChartCriteria("buildit-chart-v1", 100, List.of(), List.of(), "실제 수업 능력 순위가 아닙니다", "s"));
    when(chartRankingService.regions()).thenReturn(new ChartRegionDirectory(List.of()));
  }

  @Test
  void rankings_endpoints_arePublicAndWired() throws Exception {
    mockMvc.perform(get("/api/chart/rankings")).andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("NATIONWIDE"))
        .andExpect(jsonPath("$.disclaimer").exists());
    mockMvc.perform(get("/api/chart/rankings").param("province", "서울특별시").param("cityDistrict", "강남구"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("REGION"));
    mockMvc.perform(get("/api/chart/rankings/12345")).andExpect(status().isOk())
        .andExpect(jsonPath("$.ranked").value(false));
    mockMvc.perform(get("/api/chart/criteria")).andExpect(status().isOk())
        .andExpect(jsonPath("$.maxScore").value(100));
    mockMvc.perform(get("/api/chart/regions")).andExpect(status().isOk())
        .andExpect(jsonPath("$.provinces").isArray());
  }

  @Test
  void educatorDetailPage_redirectsToChart_whenEducatorNotPublic() throws Exception {
    when(chartEducatorSearchService.getById(999_999L))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no"));
    mockMvc.perform(get("/chart/educators/999999"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/chart"));
  }

  @Test
  void rankingsById_nonNumericPath_isNotMatched() throws Exception {
    // /rankings/{id:\d+} 와 다른 매핑이 충돌하지 않음 — 비숫자는 매칭되지 않아 4xx
    mockMvc.perform(get("/api/chart/rankings/abc")).andExpect(status().is4xxClientError());
  }
}
