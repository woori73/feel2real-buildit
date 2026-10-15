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

/**
 * 이지장부(/ledger)·Easy 장부(/easy) 공개 소개 + 권한 안내. MockMvc는 내부 forward를 따라가지 않으므로
 * (1) 보안 계층이 forward 대상과 속성을 정확히 넘기는지, (2) 소개 컨트롤러가 그 속성으로 올바른 화면을 렌더하는지를 각각 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("프로그램 공개 첫 화면 — 이지장부 /ledger · Easy 장부 /easy")
class ProgramEasyLedgerEntryMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private Cookie as(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(900701L, "pe_easy_" + role, role));
  }

  private MvcResult page(String path, String role) throws Exception {
    var req = get(path).accept(MediaType.TEXT_HTML);
    if (role != null) req = req.cookie(as(role));
    return mockMvc.perform(req).andReturn();
  }

  /** 보안 계층이 넘긴 forward 속성으로 소개 컨트롤러를 호출한다(서블릿 컨테이너의 forward를 대신). */
  private MvcResult intro(Program program, String role) throws Exception {
    var req = get("/program/intro").accept(MediaType.TEXT_HTML).requestAttr(ProgramIntroController.PROGRAM_ATTRIBUTE, program);
    if (role != null) req = req.cookie(as(role));
    return mockMvc.perform(req).andReturn();
  }

  @Test
  @DisplayName("given 비회원 when /ledger·/easy를 열면 then 보안 계층이 소개 화면으로 forward하고 소개는 200으로 네이버 시작 링크를 보여준다")
  void anonymous_gets_forwarded_to_public_intro() throws Exception {
    MvcResult ledger = page("/ledger", null);
    assertThat(ledger.getResponse().getForwardedUrl()).isEqualTo("/program/intro");
    assertThat(ledger.getRequest().getAttribute(ProgramIntroController.PROGRAM_ATTRIBUTE)).isEqualTo(Program.LEDGER);
    MvcResult easy = page("/easy", null);
    assertThat(easy.getResponse().getForwardedUrl()).isEqualTo("/program/intro");
    assertThat(easy.getRequest().getAttribute(ProgramIntroController.PROGRAM_ATTRIBUTE)).isEqualTo(Program.EASY);

    String ledgerBody = intro(Program.LEDGER, null).getResponse().getContentAsString();
    assertThat(intro(Program.LEDGER, null).getResponse().getStatus()).isEqualTo(200);
    assertThat(ledgerBody).contains("이지장부", "/auth/naver/start?returnUrl=/ledger", "program-id-login");
    assertThat(intro(Program.EASY, null).getResponse().getContentAsString())
        .contains("Easy 회계·학생원장", "/auth/naver/start?returnUrl=/easy");
  }

  @Test
  @DisplayName("given STUDENT when /ledger·/easy를 열면 then 기본 403·로그인 이동 대신 서비스 안내 화면(403 본문)")
  void student_gets_service_notice() throws Exception {
    assertThat(page("/ledger", "STUDENT").getResponse().getForwardedUrl()).isEqualTo("/program/intro");
    MvcResult notice = intro(Program.LEDGER, "STUDENT");

    assertThat(notice.getResponse().getStatus()).isEqualTo(403);
    assertThat(notice.getResponse().getContentAsString()).contains("program-forbidden", "원장(강사, TUTOR) 또는 관리자(ADMIN) 계정 전용");
    assertThat(notice.getResponse().getContentAsString()).doesNotContain("program-start");
  }

  @Test
  @DisplayName("given TUTOR when /easy를 열면 then 관리자 전용 안내(이지장부로 안내)이고 /ledger는 접근된다")
  void tutor_sees_easy_notice_but_reaches_ledger() throws Exception {
    assertThat(page("/easy", "TUTOR").getResponse().getForwardedUrl()).isEqualTo("/program/intro");
    MvcResult notice = intro(Program.EASY, "TUTOR");
    assertThat(notice.getResponse().getStatus()).isEqualTo(403);
    assertThat(notice.getResponse().getContentAsString()).contains("관리자(ADMIN) 계정 전용", "이지장부(/ledger)");

    MvcResult ledger = page("/ledger", "TUTOR");
    assertThat(ledger.getResponse().getStatus()).isEqualTo(200);
    assertThat(ledger.getResponse().getForwardedUrl()).isNull();
  }

  @Test
  @DisplayName("given ADMIN when /ledger·/easy를 열면 then 재로그인 없이 실제 화면(소개·안내 아님)")
  void admin_reaches_both_screens() throws Exception {
    for (String path : new String[] {"/ledger", "/easy", "/easy/students"}) {
      MvcResult r = page(path, "ADMIN");
      assertThat(r.getResponse().getStatus()).as(path).isEqualTo(200);
      assertThat(r.getResponse().getForwardedUrl()).as(path).isNull();
    }
  }

  @Test
  @DisplayName("given 하위 화면·API when 비회원·권한 부족이 접근하면 then 로그인 안내/안내 화면 이동이고 API 인가는 완화되지 않는다")
  void sub_pages_and_apis_stay_protected() throws Exception {
    assertThat(page("/easy/students", null).getResponse().getRedirectedUrl())
        .isEqualTo("/?login=required&returnUrl=%2Feasy%2Fstudents");
    assertThat(page("/easy/students", "TUTOR").getResponse().getRedirectedUrl()).isEqualTo("/easy");
    assertThat(page("/ledger/accounts", "STUDENT").getResponse().getRedirectedUrl()).isEqualTo("/ledger");
    MvcResult api = mockMvc.perform(get("/api/admin/easy/businesses").accept(MediaType.APPLICATION_JSON)).andReturn();
    assertThat(api.getResponse().getStatus()).isEqualTo(401);
    MvcResult tutorApi = mockMvc.perform(get("/api/admin/easy/businesses").accept(MediaType.APPLICATION_JSON).cookie(as("TUTOR"))).andReturn();
    assertThat(tutorApi.getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("given forward 속성 없는 직접 요청 when /program/intro를 열면 then 홈으로 보낸다")
  void direct_intro_request_redirects_home() throws Exception {
    MvcResult r = mockMvc.perform(get("/program/intro")).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(302);
    assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/");
  }

  @Test
  @DisplayName("given 이지장부·Easy 복귀 URL when 네이버 시작점에 접근하면 then 복귀 쿠키를 심는다")
  void naver_start_accepts_ledger_and_easy() throws Exception {
    for (String url : new String[] {"/ledger", "/easy"}) {
      MvcResult r = mockMvc.perform(get("/auth/naver/start").param("returnUrl", url)).andReturn();
      assertThat(r.getResponse().getHeaders("Set-Cookie").stream()
          .anyMatch(c -> c.startsWith("oauth_return_url=%2F" + url.substring(1) + ";"))).as(url).isTrue();
    }
  }
}
