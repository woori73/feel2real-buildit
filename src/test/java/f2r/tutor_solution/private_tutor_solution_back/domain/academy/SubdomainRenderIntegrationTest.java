package f2r.tutor_solution.private_tutor_solution_back.domain.academy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서브도메인 진입 → 공용 CSR 페이지 렌더 검증.
 *
 * <p>⚠ {@code app.public.base-domain}을 반드시 주입해야 한다. 미설정이면
 * {@code SubdomainAcademyFilter}가 no-op이라 필터를 타지도 않고 테스트가 무의미하게 통과한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.public.base-domain=feel2real.co.kr")
@DisplayName("서브도메인 렌더 통합 테스트")
class SubdomainRenderIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void subdomainRoot_forwardsToRenderPath() throws Exception {
    // given: 시드된 학원 서브도메인
    // when & then: 필터가 handle을 해석해 렌더 경로로 forward해야 한다.
    //              (MockMvc는 forward를 렌더하지 않으므로 여기선 forward 대상만 확인한다)
    mockMvc.perform(get("/").header("Host", "edu-holic.feel2real.co.kr"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/subdomain-home"));
  }

  @Test
  void renderPath_usesSharedCsrShell() throws Exception {
    // given: 필터가 주입하는 request 속성
    // when: 실제 Thymeleaf 렌더를 거친다 — 템플릿 파싱 오류가 있으면 여기서 터진다
    MvcResult result = mockMvc.perform(get("/subdomain-home")
            .requestAttr("subdomainAcademySlug", "sample-english-academy"))
        .andExpect(status().isOk())
        .andReturn();

    // then: 전용 정적 템플릿 예외가 사라졌으므로 모든 학원이 공용 CSR 페이지로 서빙된다
    String html = result.getResponse().getContentAsString();
    assertThat(html).contains("academy_public.js");
  }

  @Test
  void unknownSubdomain_returnsNotFound() throws Exception {
    // given: 존재하지 않는 handle
    // when & then: 404 fallback이 유지돼야 한다
    mockMvc.perform(get("/").header("Host", "no-such-academy.feel2real.co.kr"))
        .andExpect(status().isNotFound());
  }

  @Test
  void apexRoot_isNotForwardedToAcademyRender() throws Exception {
    // given: apex 도메인 (서브도메인 아님)
    // when & then: 필터가 통과시켜야 한다 — 학원 렌더 경로로 가면 안 된다
    MvcResult result = mockMvc.perform(get("/").header("Host", "feel2real.co.kr")).andReturn();

    assertThat(result.getResponse().getForwardedUrl()).isNotEqualTo("/subdomain-home");
  }
}
