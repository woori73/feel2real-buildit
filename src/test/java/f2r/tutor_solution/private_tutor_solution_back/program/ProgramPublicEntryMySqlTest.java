package f2r.tutor_solution.private_tutor_solution_back.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

/**
 * 공개 첫 화면 + 네이버 로그인 복귀 흐름(DesignCraft) 통합 테스트.
 * 비회원 200, 권한 부족 로그인은 안내 화면(403 본문), TUTOR만 편집기, API 인가는 완화되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("프로그램 공개 첫 화면 — DesignCraft 진입·네이버 로그인 복귀·권한 안내")
class ProgramPublicEntryMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private Cookie as(long id, String loginId, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(id, loginId, role));
  }

  /** 방문자 집계 쿠키(F2R_VISITOR) 등 다른 Set-Cookie와 구분해 복귀 쿠키만 꺼낸다. */
  private static String returnCookie(MvcResult r) {
    return r.getResponse().getHeaders("Set-Cookie").stream()
        .filter(c -> c.startsWith("oauth_return_url=")).findFirst().orElse(null);
  }

  private MvcResult html(String path, Cookie... cookies) throws Exception {
    var req = get(path).accept(MediaType.TEXT_HTML);
    if (cookies.length > 0) req = req.cookie(cookies);
    return mockMvc.perform(req).andReturn();
  }

  @Test
  @DisplayName("given 비회원 when /designcraft/editor를 열면 then 200 공개 소개와 네이버 시작 링크, 편집기 스크립트는 없다")
  void anonymous_editor_url_shows_public_intro() throws Exception {
    // when
    MvcResult r = html("/designcraft/editor");

    // then
    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body).contains("DesignCraft", "program-start", "/auth/naver/start?returnUrl=/designcraft/editor");
    assertThat(body).doesNotContain("program-forbidden");
    assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
  }

  @Test
  @DisplayName("given 비회원 when /designcraft를 열면 then 같은 공개 첫 화면 200")
  void anonymous_designcraft_root_is_public() throws Exception {
    MvcResult r = html("/designcraft");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentAsString()).contains("program-start");
  }

  @Test
  @DisplayName("given STUDENT 로그인 when /designcraft/editor를 열면 then 기본 403 대신 서비스 안내 화면(403 본문)이다")
  void student_gets_service_notice_not_editor() throws Exception {
    // when
    MvcResult r = html("/designcraft/editor", as(900401L, "pe_student", "STUDENT"));

    // then
    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(body).contains("program-forbidden", "강사(TUTOR) 또는 관리자(ADMIN) 계정 전용");
    assertThat(body).doesNotContain("program-start");
  }

  @Test
  @DisplayName("given ADMIN 로그인 when /designcraft/editor를 열면 then 관리자도 재로그인 없이 실제 편집기를 본다")
  void admin_gets_real_editor() throws Exception {
    MvcResult r = html("/designcraft/editor", as(900402L, "pe_admin", "ADMIN"));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentAsString()).doesNotContain("program-start", "program-forbidden");
  }

  @Test
  @DisplayName("given TUTOR 로그인 when /designcraft/editor를 열면 then 재로그인 요구 없이 실제 편집기를 렌더한다")
  void tutor_gets_real_editor() throws Exception {
    MvcResult r = html("/designcraft/editor", as(900403L, "pe_tutor", "TUTOR"));
    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body).doesNotContain("program-start", "program-forbidden");
  }

  @Test
  @DisplayName("given 등록 프로그램의 복귀 URL when 네이버 시작점에 접근하면 then 복귀 쿠키를 심고 기존 네이버 로그인으로 302")
  void naver_start_sets_return_cookie_and_redirects() throws Exception {
    MvcResult r = mockMvc.perform(get("/auth/naver/start").param("returnUrl", "/designcraft/editor")).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(302);
    assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/oauth2/authorization/naver");
    assertThat(returnCookie(r)).contains("oauth_return_url=%2Fdesigncraft%2Feditor", "Max-Age=300", "SameSite=Lax", "HttpOnly");
  }

  @Test
  @DisplayName("given 외부 URL·미등록 경로·경로탐색 when 네이버 시작점에 접근하면 then 복귀 쿠키를 만들지 않는다")
  void naver_start_ignores_unsafe_or_unregistered_return_urls() throws Exception {
    for (String bad : new String[] {
        "https://evil.com", "//evil.com", "/designcraft/../admin/dashboard", "/admin/dashboard", "/\\evil.com"}) {
      MvcResult r = mockMvc.perform(get("/auth/naver/start").param("returnUrl", bad)).andReturn();
      assertThat(r.getResponse().getStatus()).as(bad).isEqualTo(302);
      assertThat(r.getResponse().getRedirectedUrl()).as(bad).isEqualTo("/oauth2/authorization/naver");
      assertThat(returnCookie(r)).as(bad).isNull();
    }
  }

  @Test
  @DisplayName("given 비회원 when 출판 API를 호출하면 then 인증·인가는 완화되지 않고 401이다")
  void publishing_api_stays_protected() throws Exception {
    MvcResult r = mockMvc.perform(get("/api/publishing/jobs/1").accept(MediaType.APPLICATION_JSON)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    MvcResult w = mockMvc.perform(post("/api/publishing/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    assertThat(w.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("given STUDENT 로그인 when 출판 API를 호출하면 then TUTOR 전용 인가가 유지돼 거부된다")
  void publishing_api_rejects_student() throws Exception {
    MvcResult r = mockMvc.perform(get("/api/publishing/jobs/1").cookie(as(900401L, "pe_student", "STUDENT"))
        .accept(MediaType.APPLICATION_JSON)).andReturn();
    assertThat(r.getResponse().getStatus()).isIn(401, 403, 404);
    assertThat(r.getResponse().getContentAsString()).doesNotContain("\"status\":\"DONE\"");
  }
}
