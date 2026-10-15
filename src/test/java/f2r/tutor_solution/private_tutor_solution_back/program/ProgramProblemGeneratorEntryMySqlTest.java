package f2r.tutor_solution.private_tutor_solution_back.program;

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

/** 내신 영어 문제지 제작기(/problem-generator) 공개 첫 화면 + 로그인 복귀. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("프로그램 공개 첫 화면 — 문제지 제작기 /problem-generator")
class ProgramProblemGeneratorEntryMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private MvcResult html(String path, String role) throws Exception {
    var req = get(path).accept(MediaType.TEXT_HTML);
    if (role != null) {
      req = req.cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(900601L, "pe_pg_" + role, role)));
    }
    return mockMvc.perform(req).andReturn();
  }

  @Test
  @DisplayName("given 비회원 when /problem-generator를 열면 then 200 공개 소개와 네이버 시작 링크")
  void anonymous_sees_public_intro() throws Exception {
    MvcResult r = html("/problem-generator", null);

    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body).contains("내신 영어 문제지 제작기", "/auth/naver/start?returnUrl=/problem-generator", "program-id-login");
  }

  @Test
  @DisplayName("given STUDENT·TUTOR·ADMIN 로그인 when 첫 화면을 열면 then 재로그인 없이 기존 제작기로 이동한다")
  void allowed_roles_go_to_existing_tool() throws Exception {
    for (String role : new String[] {"STUDENT", "TUTOR", "ADMIN"}) {
      MvcResult r = html("/problem-generator", role);
      assertThat(r.getResponse().getStatus()).as(role).isEqualTo(302);
      assertThat(r.getResponse().getRedirectedUrl()).as(role).startsWith("/problem-generator/wiki/index.html");
    }
  }

  @Test
  @DisplayName("given 비회원 when 하위 기능(/problem-generator/exam)을 열면 then 원래 경로를 returnUrl로 싣고 로그인 안내로 302")
  void anonymous_sub_page_redirects_with_return_url() throws Exception {
    MvcResult r = html("/problem-generator/exam", null);

    assertThat(r.getResponse().getStatus()).isEqualTo(302);
    assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/?login=required&returnUrl=%2Fproblem-generator%2Fexam");
  }

  @Test
  @DisplayName("given 비회원 when 제작기 Wiki 화면·API를 직접 호출하면 then 인증은 완화되지 않는다")
  void protected_tool_stays_protected() throws Exception {
    MvcResult page = html("/problem-generator/wiki/index.html", null);
    assertThat(page.getResponse().getStatus()).isEqualTo(302);
    MvcResult api = mockMvc.perform(get("/problem-generator/wiki/api/anything").accept(MediaType.APPLICATION_JSON)).andReturn();
    assertThat(api.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("given /problem-generator 복귀 URL when 네이버 시작점에 접근하면 then 복귀 쿠키를 심는다")
  void naver_start_accepts_problem_generator_return_url() throws Exception {
    MvcResult r = mockMvc.perform(get("/auth/naver/start").param("returnUrl", "/problem-generator/exam")).andReturn();

    assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/oauth2/authorization/naver");
    assertThat(r.getResponse().getHeaders("Set-Cookie").stream()
        .anyMatch(c -> c.startsWith("oauth_return_url=%2Fproblem-generator%2Fexam;"))).isTrue();
  }
}
