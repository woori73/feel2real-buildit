package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioAccessGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제작소 AI 작업 세션 경로는 쿠키 로그인 없이 컨트롤러까지 도달해야 한다(토큰 검사는 컨트롤러 몫). 2026-11-11 운영에서
 * {@code /api/**} 인증 규칙에 막혀 토큰 검사 전에 401이 난 회귀를 막는다.
 */
@WebMvcTest(controllers = StudioAgentSecurityIntegrationTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class, StudioAccessGuard.class,
    StudioAgentSecurityIntegrationTest.ProbeController.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("제작소 AI 작업 세션 경로 — 보안 규칙이 컨트롤러 토큰 검사 앞에서 막지 않는다")
class StudioAgentSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean AcademyRepository academyRepository;
  @MockitoBean UserRepository userRepository;

  @RestController
  static class ProbeController {
    @RequestMapping({"/api/studio-agent/**", "/api/studio/**", "/api/other-probe"})
    String probe() {
      return "probe";
    }
  }

  @Test
  void givenNoLogin_whenAgentDownload_thenReachesController() throws Exception {
    // given: 쿠키 없음, Bearer만
    // when / then
    mockMvc
        .perform(
            get("/api/studio-agent/files/f1/content").header("Authorization", "Bearer anything"))
        .andExpect(status().isOk())
        .andExpect(content().string("probe"));
  }

  @Test
  void givenNoLogin_whenAgentUpload_thenReachesController() throws Exception {
    // given: 쿠키 없음
    // when / then
    mockMvc
        .perform(post("/api/studio-agent/files"))
        .andExpect(status().isOk())
        .andExpect(content().string("probe"));
  }

  @Test
  void givenNoLogin_whenStudioCookieApi_thenStillBlocked() throws Exception {
    // given: 쿠키 없음
    // when
    var studio = mockMvc.perform(get("/api/studio/files/f1/content")).andReturn().getResponse();
    var other = mockMvc.perform(get("/api/other-probe")).andReturn().getResponse();
    // then: 기존 제작소 경로와 나머지 /api/**는 그대로 닫혀 있다
    org.junit.jupiter.api.Assertions.assertNotEquals(200, studio.getStatus());
    org.junit.jupiter.api.Assertions.assertEquals(401, other.getStatus());
  }
}
