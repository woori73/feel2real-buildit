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
}
