package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminPasswordChangeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.PasswordResetService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/auth/admin/password-change} 계약 — 인증 없이 도달 가능하고,
 * 권한 판정은 서비스(서버 고정 ADMIN 대상 + 현재 비번 검증)가 수행한다.
 */
@DisplayName("Admin 비밀번호 변경 API — permitAll 진입, 판정은 서비스")
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class AdminPasswordChangeControllerWebMvcTest {

  private static final String BODY =
      "{\"loginId\":\"admin-f2r\",\"currentPassword\":\"current-password-000\","
          + "\"newPassword\":\"next-password-000000\",\"confirmPassword\":\"next-password-000000\"}";

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean UserService userService;
  @MockitoBean PasswordResetService passwordResetService;
  @MockitoBean AdminPasswordChangeService adminPasswordChangeService;
  @MockitoBean AcademyRepository academyRepository;

  @Test
  @DisplayName("익명 정상 요청 → 204 (로그인 화면에서 호출 가능)")
  void anonymous_valid_noContent() throws Exception {
    doNothing().when(adminPasswordChangeService)
        .changePassword(anyString(), anyString(), anyString(), anyString());

    mockMvc.perform(post("/api/auth/admin/password-change")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("현재 비번 오류 → 401")
  void wrongCurrent_unauthorized() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "x"))
        .when(adminPasswordChangeService).changePassword(anyString(), anyString(), anyString(), anyString());

    mockMvc.perform(post("/api/auth/admin/password-change")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("STUDENT 쿠키 + 학생 ID → 403 (역할로 우회 불가)")
  void student_forbidden() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "x"))
        .when(adminPasswordChangeService).changePassword(anyString(), anyString(), anyString(), anyString());
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "student01", "STUDENT"));

    mockMvc.perform(post("/api/auth/admin/password-change")
            .cookie(student)
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY.replace("admin-f2r", "student01")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("검증 실패 → 400, 차단 초과 → 429")
  void validation_badRequest_rateLimit_tooManyRequests() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "x"))
        .when(adminPasswordChangeService).changePassword(anyString(), anyString(), anyString(), anyString());
    mockMvc.perform(post("/api/auth/admin/password-change")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY))
        .andExpect(status().isBadRequest());

    doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "x"))
        .when(adminPasswordChangeService).changePassword(anyString(), anyString(), anyString(), anyString());
    mockMvc.perform(post("/api/auth/admin/password-change")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY))
        .andExpect(status().isTooManyRequests());
  }
}
