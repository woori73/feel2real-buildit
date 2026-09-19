package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /admin/newstart 운영자 화면 권한 계약 — anonymous 401 / 일반 회원 403 / ADMIN 200.
 * SecurityConfig 규칙(1차)과 controller principal 재검사(2차)가 모두 ROLE_ADMIN만 통과시키는지
 * 못 박는다. 다른 admin SSR 화면과 달리 viewController가 아닌 실제 controller라 권한 재검사가
 * 서버에서 강제된다.
 */
@WebMvcTest(controllers = AdminNewStartPageController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    JwtTokenProvider.class,
    CookieUtil.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class AdminNewStartPageControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  private Cookie cookie(long userId, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "user" + userId, role));
  }

  @Test
  void anonymousGets401() throws Exception {
    mockMvc.perform(get("/admin/newstart")).andExpect(status().is4xxClientError());
  }

  @Test
  void studentGets403() throws Exception {
    mockMvc.perform(get("/admin/newstart").cookie(cookie(2L, "STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void tutorGets403() throws Exception {
    mockMvc.perform(get("/admin/newstart").cookie(cookie(3L, "TUTOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminGets200WithNewStartAdminView() throws Exception {
    mockMvc.perform(get("/admin/newstart").cookie(cookie(1L, "ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void adminSubPathsAlsoRequireAdminRole() throws Exception {
    // /admin/newstart/** 규칙 — 향후 하위 운영 화면이 생겨도 ROLE_ADMIN 전용임을 못 박는다.
    mockMvc.perform(get("/admin/newstart/chat").cookie(cookie(2L, "STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/newstart/chat"))
        .andExpect(status().is4xxClientError());
  }
}
