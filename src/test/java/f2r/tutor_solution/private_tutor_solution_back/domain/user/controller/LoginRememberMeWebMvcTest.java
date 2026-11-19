package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.LoginRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.LoginResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminPasswordChangeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.PasswordResetService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.TemporaryPasswordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.UserService;
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

/** {@code POST /api/auth/login}의 "로그인 상태 유지"(rememberMe) — 쿠키 유지 기간. */
@DisplayName("로그인 상태 유지 — rememberMe면 30일, 아니면 기본 만료")
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class LoginRememberMeWebMvcTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean UserService userService;
  @MockitoBean PasswordResetService passwordResetService;
  @MockitoBean AdminPasswordChangeService adminPasswordChangeService;
  @MockitoBean AcademyRepository academyRepository;
  @MockitoBean TemporaryPasswordService temporaryPasswordService;

  private String loginCookie(String body, boolean passwordChangeRequired) throws Exception {
    when(userService.login(any(LoginRequest.class))).thenReturn(
        new LoginResponse(7L, "student01", "홍길동", "길동", "길동", "STUDENT", passwordChangeRequired));
    return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
  }

  @Test
  @DisplayName("rememberMe=true → 쿠키 30일")
  void rememberMe_true_keepsThirtyDays() throws Exception {
    // given
    String body = "{\"loginId\":\"student01\",\"password\":\"pw\",\"rememberMe\":true}";

    // when
    String setCookie = loginCookie(body, false);

    // then
    assertThat(setCookie).contains("ACCESS_TOKEN=").contains("Max-Age=" + AuthController.REMEMBER_ME_SECONDS);
  }

  @Test
  @DisplayName("rememberMe 없음 → 기본 만료(24시간)")
  void rememberMe_missing_usesDefault() throws Exception {
    // given
    String body = "{\"loginId\":\"student01\",\"password\":\"pw\"}";

    // when
    String setCookie = loginCookie(body, false);

    // then
    assertThat(setCookie).contains("Max-Age=86400");
  }

  @Test
  @DisplayName("임시 비밀번호 로그인은 rememberMe여도 기본 만료")
  void rememberMe_withTemporaryPassword_usesDefault() throws Exception {
    // given
    String body = "{\"loginId\":\"student01\",\"password\":\"pw\",\"rememberMe\":true}";

    // when
    String setCookie = loginCookie(body, true);

    // then
    assertThat(setCookie).contains("Max-Age=86400");
  }
}
