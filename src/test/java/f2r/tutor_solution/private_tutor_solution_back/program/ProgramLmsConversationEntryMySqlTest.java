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
 * 학원 LMS 운영 화면·대화형 학습 강사 전용 화면: 비회원은 원래 경로를 싣은 로그인 안내로,
 * 권한 없는 로그인 사용자는 기본 403 대신 서비스 안내 화면(403)으로. 접근 허용 규칙·API 인가는 그대로.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("프로그램 진입 — 학원 LMS 운영 화면·대화형 학습")
class ProgramLmsConversationEntryMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;

  private MvcResult page(String path, String role) throws Exception {
    var req = get(path).accept(MediaType.TEXT_HTML);
    if (role != null) {
      req = req.cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(900801L, "pe_lms_" + role, role)));
    }
    return mockMvc.perform(req).andReturn();
  }

  @Test
  @DisplayName("given 비회원 when 학원 학생 화면·대화 화면을 열면 then 원래 경로(returnUrl)를 싣고 로그인 안내로 302")
  void anonymous_is_sent_to_login_guidance_with_return_url() throws Exception {
    assertThat(page("/lms/demo/my", null).getResponse().getRedirectedUrl())
        .isEqualTo("/?login=required&returnUrl=%2Flms%2Fdemo%2Fmy");
    assertThat(page("/lms/demo/manage", null).getResponse().getRedirectedUrl())
        .isEqualTo("/?login=required&returnUrl=%2Flms%2Fdemo%2Fmanage");
    assertThat(page("/conversation/chat", null).getResponse().getRedirectedUrl())
        .isEqualTo("/?login=required&returnUrl=%2Fconversation%2Fchat");
    assertThat(page("/tutor/conversation/dashboard", null).getResponse().getRedirectedUrl())
        .isEqualTo("/?login=required&returnUrl=%2Ftutor%2Fconversation%2Fdashboard");
  }

  @Test
  @DisplayName("given 비회원 JSON 요청 when API를 호출하면 then 인증은 완화되지 않고 401")
  void anonymous_api_stays_401() throws Exception {
    MvcResult r = mockMvc.perform(get("/api/conversations").accept(MediaType.APPLICATION_JSON)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("given STUDENT when 학원 운영 화면·강사 전용 대화 화면을 열면 then 기본 403 대신 안내 화면으로 forward(403)")
  void student_gets_service_notice_on_tutor_only_pages() throws Exception {
    for (String path : new String[] {"/lms/demo/manage", "/lms/demo/contents", "/lms/demo/midterm-live",
        "/tutor/conversation/dashboard", "/tutor/conversation_dashboard", "/tutor/conversations/1"}) {
      MvcResult r = page(path, "STUDENT");
      assertThat(r.getResponse().getStatus()).as(path).isEqualTo(403);
      assertThat(r.getResponse().getForwardedUrl()).as(path).isEqualTo("/program/notice");
      assertThat(r.getRequest().getAttribute(ProgramIntroController.NOTICE_MESSAGE_ATTRIBUTE)).as(path).isNotNull();
    }
  }

  @Test
  @DisplayName("given 안내 속성 when /program/notice를 렌더하면 then 403 상태의 서비스 안내 본문, 속성 없는 직접 요청은 홈으로")
  void notice_page_renders_message_or_redirects_home() throws Exception {
    MvcResult r = mockMvc.perform(get("/program/notice").accept(MediaType.TEXT_HTML)
        .requestAttr(ProgramIntroController.NOTICE_TITLE_ATTRIBUTE, "강사 전용 화면 안내")
        .requestAttr(ProgramIntroController.NOTICE_MESSAGE_ATTRIBUTE, "이 화면은 강사(TUTOR) 계정 전용입니다.")).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(r.getResponse().getContentAsString()).contains("program-forbidden", "강사 전용 화면 안내", "강사(TUTOR) 계정 전용");

    MvcResult direct = mockMvc.perform(get("/program/notice")).andReturn();
    assertThat(direct.getResponse().getStatus()).isEqualTo(302);
    assertThat(direct.getResponse().getRedirectedUrl()).isEqualTo("/");
  }

  @Test
  @DisplayName("given TUTOR when 강사 전용 대화 화면을 열면 then 재로그인 없이 접근하고, STUDENT의 /conversation/chat은 기존대로 학생 홈으로")
  void tutor_reaches_page_and_student_chat_redirect_is_unchanged() throws Exception {
    assertThat(page("/tutor/conversation/dashboard", "TUTOR").getResponse().getStatus()).isIn(200, 302);
    assertThat(page("/tutor/conversation/dashboard", "TUTOR").getResponse().getForwardedUrl()).isNull();
    assertThat(page("/conversation/chat", "STUDENT").getResponse().getRedirectedUrl()).isEqualTo("/stdt_dashboard");
  }

  @Test
  @DisplayName("given 공개 LMS 목록 when 비회원이 열면 then 기존대로 공개(로그인 요구 없음)")
  void public_lms_pages_remain_public() throws Exception {
    MvcResult r = page("/lms/ghost", null);
    assertThat(r.getResponse().getRedirectedUrl()).isNull();
    assertThat(r.getResponse().getStatus()).isNotEqualTo(401);
  }
}
