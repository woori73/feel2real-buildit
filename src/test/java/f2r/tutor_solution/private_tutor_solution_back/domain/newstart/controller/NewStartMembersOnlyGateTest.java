package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** NewStart 정책(2026-10-02): 홈·로그인만 공개, 나머지 /newstart/** 화면은 회원 전용. */
@WebMvcTest(controllers = {
    NewStartPageController.class,
    NewStartLoginPageController.class,
    NewStartChatPageController.class,
    NewStartPracticalToolsPageController.class,
    NewStartGalmuriPageController.class
})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartMembersOnlyGateTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartOfficialUpdateService updateService;
  @MockitoBean f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService atozCaseService;
  @MockitoBean f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService entitlementService;

  @Test
  void homeAndLoginStayPublic() throws Exception {
    when(updateService.latestPublished(3)).thenReturn(List.of());
    mockMvc.perform(get("/newstart")).andExpect(status().isOk());
    mockMvc.perform(get("/newstart/login")).andExpect(status().isOk());
  }

  @Test
  void anonymousInternalScreensRedirectToLoginWithNext() throws Exception {
    mockMvc.perform(get("/newstart/chat"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/newstart/login?next=%2Fnewstart%2Fchat"));
    mockMvc.perform(get("/newstart/tools/credit4u"))
        .andExpect(redirectedUrl("/newstart/login?next=%2Fnewstart%2Ftools%2Fcredit4u"));
    mockMvc.perform(get("/newstart/galmuri").queryParam("a", "b"))
        .andExpect(redirectedUrl("/newstart/login?next=%2Fnewstart%2Fgalmuri%3Fa%3Db"));
  }

  @Test
  void memberOpensInternalScreen() throws Exception {
    Cookie member = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "user2", "STUDENT"));
    mockMvc.perform(get("/newstart/chat").cookie(member)).andExpect(status().isOk());
  }

  @Test
  void aToZIsAdminOnly() throws Exception {
    Cookie member = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "user2", "STUDENT"));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "admin", "ADMIN"));
    // 비회원: 회원 전용 게이트가 로그인으로
    mockMvc.perform(get("/newstart/a-to-z"))
        .andExpect(redirectedUrl("/newstart/login?next=%2Fnewstart%2Fa-to-z"));
    // 일반회원(감사회원 포함): URL 직접 입력해도 홈으로
    mockMvc.perform(get("/newstart/a-to-z").cookie(member))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/newstart"));
    // ADMIN: 정상
    mockMvc.perform(get("/newstart/a-to-z").cookie(admin)).andExpect(status().isOk());
  }

  @Test
  void homeShowsAiInputOnlyToThanksMemberAndAdmin() throws Exception {
    when(updateService.latestPublished(3)).thenReturn(List.of());
    Cookie member = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "user2", "STUDENT"));
    Cookie thanks = new Cookie("ACCESS_TOKEN", tokenProvider.issue(3L, "user3", "STUDENT"));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "admin", "ADMIN"));
    when(entitlementService.isThanksMember(org.mockito.ArgumentMatchers.argThat(p -> p != null && p.userId() == 3L)))
        .thenReturn(true);

    String guestHtml = mockMvc.perform(get("/newstart")).andReturn().getResponse().getContentAsString();
    String memberHtml = mockMvc.perform(get("/newstart").cookie(member)).andReturn().getResponse().getContentAsString();
    String thanksHtml = mockMvc.perform(get("/newstart").cookie(thanks)).andReturn().getResponse().getContentAsString();
    String adminHtml = mockMvc.perform(get("/newstart").cookie(admin)).andReturn().getResponse().getContentAsString();

    for (String html : List.of(guestHtml, memberHtml)) {
      org.assertj.core.api.Assertions.assertThat(html).doesNotContain("id=\"ns-ai-form\"").contains("id=\"ns-ai-locked\"");
    }
    for (String html : List.of(thanksHtml, adminHtml)) {
      org.assertj.core.api.Assertions.assertThat(html).contains("id=\"ns-ai-form\"").doesNotContain("id=\"ns-ai-locked\"");
    }
    // 모든 방문자 HOME 에는 A to Z 링크가 없다
    for (String html : List.of(guestHtml, memberHtml, thanksHtml, adminHtml)) {
      org.assertj.core.api.Assertions.assertThat(html).doesNotContain("/newstart/a-to-z").contains("href=\"/newstart/consultations\"");
    }
  }
}
