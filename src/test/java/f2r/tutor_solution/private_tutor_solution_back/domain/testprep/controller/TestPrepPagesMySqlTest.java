package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * TESTPREP 3단계 — `/testprep` 화면 진입·렌더·접근 통제 MySQL 통합 테스트.
 *
 * <p>SecurityConfig의 신규 규칙을 검증한다:
 * <ul>
 *   <li>익명 브라우저 요청은 raw 401이 아닌 랜딩 로그인 모달로 리다이렉트(302 → {@code /?login=required}).</li>
 *   <li>로그인 회원(STUDENT)에게는 Thymeleaf 화면이 정상 렌더(200)된다.</li>
 *   <li>feel2real {@code /home}(BuildIt 랜딩)은 그대로 익명 접근 가능 — TESTPREP 화면 통제가 홈페이지에 영향 없음.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("TESTPREP 3단계 — /testprep 화면 진입·렌더·접근 통제")
class TestPrepPagesMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private Cookie auth() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(900303L, "tp_page_student", "STUDENT"));
  }

  @Test
  @DisplayName("익명 /testprep 요청은 302로 랜딩 로그인 모달로 보낸다 (raw 401 아님)")
  void anonymousTestprepRedirectsToLogin() throws Exception {
    MvcResult r = mockMvc.perform(get("/testprep").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(302);
    assertThat(r.getResponse().getRedirectedUrl()).startsWith("/?login=required");
  }

  @Test
  @DisplayName("로그인 회원은 /testprep 및 서브 화면이 200으로 렌더된다")
  void authenticatedPagesRender() throws Exception {
    for (String path : new String[] {
        "/testprep", "/testprep/practice", "/testprep/vocab",
        "/testprep/run", "/testprep/scores", "/testprep/review", "/testprep/learning"}) {
      MvcResult r = mockMvc.perform(get(path).cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
      assertThat(r.getResponse().getStatus()).as("render " + path).isEqualTo(200);
      String html = r.getResponse().getContentAsString();
      assertThat(html).as("nav present on " + path).contains("tp-nav");
      // 한영 병기 셸이 함께 렌더되는지 확인.
      assertThat(html).as("bilingual ko span on " + path).contains("class=\"ko\"");
    }
  }

  @Test
  @DisplayName("TESTPREP 화면 통제는 BuildIt /home 익명 접근을 막지 않는다")
  void homeRemainsPublic() throws Exception {
    MvcResult r = mockMvc.perform(get("/home").accept(MediaType.TEXT_HTML)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("/testprep landing은 QA dev-login 배너 없이 실제 SSO 로그인만 안내한다")
  void landingHasNoDevLoginBanner() throws Exception {
    MvcResult r = mockMvc.perform(get("/testprep").cookie(auth()).accept(MediaType.TEXT_HTML)).andReturn();
    String html = r.getResponse().getContentAsString();
    assertThat(html).doesNotContain("qa-banner");
    assertThat(html).doesNotContain("Dev login");
    assertThat(html).doesNotContain("dev-login");
  }
}
