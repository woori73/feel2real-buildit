package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller.ProblemGeneratorPageController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Problem Generator SSR 화면의 비로그인 진입 계약.
 *
 * <p>목적지는 랜딩("/")으로 유지하되(비로그인 목적지 통일), 왜 튕겼는지 알 수 있도록
 * {@code login=required} 마커를 싣는다. 랜딩 스크립트가 이 값을 읽어 로그인 모달을 연다.
 */
@WebMvcTest(controllers = ProblemGeneratorPageController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("Problem Generator 화면 비로그인 진입")
class ProblemGeneratorSecurityIntegrationTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean AcademyRepository academyRepository;

  @Test
  @DisplayName("비로그인 브라우저 요청은 첫 화면(/problem-generator)에서 랜딩으로 튕기지 않고 200 공개 소개를 본다")
  void anonymousHtmlRequestSeesPublicIntro() throws Exception {
    // given: 로그인하지 않은 사용자의 주소창 top-level GET
    // when & then: 소개 화면(200)에서 네이버 로그인으로 시작한다
    mockMvc.perform(get("/problem-generator").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("하위 메뉴는 login=required 마커와 원래 경로(returnUrl)를 실어 302된다")
  void anonymousHtmlRequestOnSubMenuRedirectsWithLoginMarker() throws Exception {
    // given: 7개 메뉴 중 하나로 직접 진입한 비로그인 사용자
    // when & then: 로그인 후 원래 화면으로 돌아오도록 returnUrl이 실려야 한다
    mockMvc.perform(
            get("/problem-generator/questions").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/?login=required&returnUrl=%2Fproblem-generator%2Fquestions"));
  }

  @Test
  @DisplayName("비로그인 JSON 요청은 기존 계약대로 401 — entry point 교체가 API를 오염시키지 않는다")
  void anonymousJsonRequestStays401() throws Exception {
    // given: fetch/XHR 요청 — 리다이렉트를 주면 호출부가 HTML을 파싱하게 된다
    // when & then
    mockMvc.perform(
            get("/problem-generator/questions").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());
  }
}
