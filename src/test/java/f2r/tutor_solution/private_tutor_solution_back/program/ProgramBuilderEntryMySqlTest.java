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

/** BuildIt 홈페이지 제작기(/builder) 공개 첫 화면 + 네이버 로그인 복귀 + 권한 안내. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("프로그램 공개 첫 화면 — BuildIt /builder")
class ProgramBuilderEntryMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private MvcResult html(String path, String role) throws Exception {
    var req = get(path).accept(MediaType.TEXT_HTML);
    if (role != null) {
      req = req.cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(900501L, "pe_builder_" + role, role)));
    }
    return mockMvc.perform(req).andReturn();
  }

  @Test
  @DisplayName("given 비회원 when /builder를 열면 then 랜딩으로 튕기지 않고 200 소개 화면과 네이버 시작 링크")
  void anonymous_builder_shows_public_intro() throws Exception {
    MvcResult r = html("/builder", null);

    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body).contains("BuildIt 홈페이지 제작", "program-start", "/auth/naver/start?returnUrl=/builder");
    assertThat(body).doesNotContain("program-forbidden");
  }

  @Test
  @DisplayName("given STUDENT 로그인 when /builder를 열면 then 서비스 안내 화면(403 본문)이고 제작기는 렌더되지 않는다")
  void student_gets_notice() throws Exception {
    MvcResult r = html("/builder", "STUDENT");

    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(body).contains("program-forbidden", "강사(TUTOR) 또는 관리자(ADMIN) 계정 전용");
    assertThat(body).doesNotContain("program-start");
  }

  @Test
  @DisplayName("given TUTOR·ADMIN 로그인 when /builder를 열면 then 재로그인 없이 실제 제작기를 렌더한다")
  void tutor_and_admin_get_real_builder() throws Exception {
    for (String role : new String[] {"TUTOR", "ADMIN"}) {
      MvcResult r = html("/builder", role);
      assertThat(r.getResponse().getStatus()).as(role).isEqualTo(200);
      assertThat(r.getResponse().getContentAsString()).as(role).doesNotContain("program-start", "program-forbidden");
    }
  }

  @Test
  @DisplayName("given 샘플 갤러리 /builder/entry when 비회원이 열면 then 기존대로 공개 200")
  void sample_gallery_stays_public() throws Exception {
    assertThat(html("/builder/entry", null).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("given /builder 복귀 URL when 네이버 시작점에 접근하면 then 복귀 쿠키를 심고 네이버 로그인으로 302")
  void naver_start_accepts_builder_return_url() throws Exception {
    MvcResult r = mockMvc.perform(get("/auth/naver/start").param("returnUrl", "/builder")).andReturn();

    assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/oauth2/authorization/naver");
    assertThat(r.getResponse().getHeaders("Set-Cookie").stream()
        .anyMatch(c -> c.startsWith("oauth_return_url=%2Fbuilder;"))).isTrue();
  }

  @Test
  @DisplayName("given 비회원·STUDENT when 학원 편집 API를 호출하면 then 인증·인가는 완화되지 않는다")
  void academy_apis_stay_protected() throws Exception {
    MvcResult anon = mockMvc.perform(get("/api/tutor/academy/me").accept(MediaType.APPLICATION_JSON)).andReturn();
    assertThat(anon.getResponse().getStatus()).isEqualTo(401);
    MvcResult student = mockMvc.perform(get("/api/tutor/academy/me").accept(MediaType.APPLICATION_JSON)
        .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(900502L, "pe_builder_student", "STUDENT")))).andReturn();
    assertThat(student.getResponse().getStatus()).isEqualTo(403);
  }
}
