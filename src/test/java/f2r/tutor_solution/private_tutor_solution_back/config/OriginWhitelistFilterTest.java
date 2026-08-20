package f2r.tutor_solution.private_tutor_solution_back.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OriginWhitelistFilterTest {

  private MockMvc mockMvcWithOrigins(List<String> origins) {
    OriginWhitelistFilter filter = new OriginWhitelistFilter(new WebSecurityProperties(origins));
    return MockMvcBuilders.standaloneSetup(new ApiController()).addFilter(filter).build();
  }

  @Test
  void write_fromDisallowedOrigin_isForbidden() throws Exception {
    // given: 화이트리스트 밖 Origin
    MockMvc mockMvc = mockMvcWithOrigins(List.of("feel2real.co.kr"));

    // when & then
    mockMvc.perform(post("/api/tutor/academy").header("Origin", "https://evil.example.com"))
        .andExpect(status().isForbidden());
  }

  @Test
  void write_fromApexOrigin_passes() throws Exception {
    // given: apex Origin
    MockMvc mockMvc = mockMvcWithOrigins(List.of("feel2real.co.kr"));

    // when & then
    mockMvc.perform(post("/api/tutor/academy").header("Origin", "https://feel2real.co.kr"))
        .andExpect(status().isOk());
  }

  @Test
  void write_fromSubdomainOrigin_passes() throws Exception {
    // given: 정식 서브도메인 Origin
    MockMvc mockMvc = mockMvcWithOrigins(List.of("feel2real.co.kr"));

    // when & then
    mockMvc.perform(post("/api/tutor/academy").header("Origin", "https://minji.feel2real.co.kr"))
        .andExpect(status().isOk());
  }

  @Test
  void write_withoutOriginOrReferer_isForbiddenWhenEnforced() throws Exception {
    // given: 브라우저 아닌 쓰기(Origin·Referer 모두 없음) → 기본 거부
    MockMvc mockMvc = mockMvcWithOrigins(List.of("feel2real.co.kr"));

    // when & then
    mockMvc.perform(post("/api/tutor/academy")).andExpect(status().isForbidden());
  }

  @Test
  void write_fallsBackToRefererWhenOriginMissing() throws Exception {
    // given: Origin 없고 Referer만 있음
    MockMvc mockMvc = mockMvcWithOrigins(List.of("feel2real.co.kr"));

    // when & then
    mockMvc.perform(post("/api/tutor/academy").header("Referer", "https://minji.feel2real.co.kr/page"))
        .andExpect(status().isOk());
  }

  @Test
  void getRequest_isNeverBlocked() throws Exception {
    // given: 읽기(GET)는 Origin 무관
    MockMvc mockMvc = mockMvcWithOrigins(List.of("feel2real.co.kr"));

    // when & then
    mockMvc.perform(get("/api/academies").header("Origin", "https://evil.example.com"))
        .andExpect(status().isOk());
  }

  @Test
  void enforcementDisabled_whenNoOriginsConfigured() throws Exception {
    // given: allowed-origins 미설정(local/dev/stg) → 전면 비활성
    MockMvc mockMvc = mockMvcWithOrigins(List.of());

    // when & then: Origin 없어도 통과
    mockMvc.perform(post("/api/tutor/academy")).andExpect(status().isOk());
  }

  @Test
  void write_underContextPath_isGuardedUsingCanonicalApplicationPath() throws Exception {
    // given: non-empty context path and missing Origin
    OriginWhitelistFilter filter =
        new OriginWhitelistFilter(new WebSecurityProperties(List.of("feel2real.co.kr")));
    org.springframework.mock.web.MockHttpServletRequest request =
        new org.springframework.mock.web.MockHttpServletRequest("POST", "/app/api/items");
    request.setContextPath("/app");
    request.setServletPath("/api/items");
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();

    // when
    filter.doFilter(request, response, (wrapped, ignored) -> { });

    // then
    org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void matrixApiPath_isGuardedWithPathPatternSemantics() throws Exception {
    // given: firewall-independent direct filter request with API matrix parameters
    OriginWhitelistFilter filter =
        new OriginWhitelistFilter(new WebSecurityProperties(List.of("feel2real.co.kr")));
    org.springframework.mock.web.MockHttpServletRequest request =
        new org.springframework.mock.web.MockHttpServletRequest("POST", "/api;v=1/items");
    request.setServletPath("/api;v=1/items");
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();

    // when
    filter.doFilter(request, response, (wrapped, ignored) -> { });

    // then
    org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(403);
  }

  @RestController
  static class ApiController {
    @PostMapping("/api/tutor/academy")
    @ResponseBody
    String write() {
      return "ok";
    }

    @GetMapping("/api/academies")
    @ResponseBody
    String read() {
      return "ok";
    }
  }
}
