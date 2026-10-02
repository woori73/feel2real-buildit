package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartPasswordResetResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartAdminMemberQueryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartAdminPasswordResetService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartMemberPasswordService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 비밀번호 관리의 권한 HARD GATE — SECURITY 실체(SecurityConfig 필터 체인)를 태운 채로
 * "누가 초기화할 수 없는가"를 4가지 케이스로 고정한다.
 *
 * <p>감사회원은 {@code newstart_member_grants}의 capability이지 role이 아니다. JWT에 담기는 것은
 * role뿐이므로 감사회원에게도 이 경로는 일반 회원과 똑같이 ROLE_ADMIN 규칙에 막힌다 — 그래서
 * 아래 두 케이스는 모두 role=STUDENT 토큰으로 검증한다(grant 여부와 무관하게 403).
 */
@DisplayName("NewStart 비밀번호 권한 게이트 — 회원 초기화는 ADMIN만")
@WebMvcTest(controllers = {AdminNewStartMemberController.class, NewStartMemberPasswordController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartPasswordAuthorityTest {

  private static final String RESET_PATH = "/api/admin/newstart/members/77/password-reset";

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean NewStartAdminMemberQueryService queryService;
  @MockitoBean NewStartAdminPasswordResetService resetService;
  @MockitoBean NewStartMemberPasswordService passwordService;

  @Test
  @DisplayName("비로그인 → 초기화 DENY(401) — 서비스는 호출되지 않는다")
  void anonymousCannotReset() throws Exception {
    mockMvc.perform(post(RESET_PATH))
        .andExpect(status().isUnauthorized());
    verify(resetService, never()).reset(any(), any());
  }

  @Test
  @DisplayName("일반회원(STUDENT) → 다른 회원 초기화 DENY(403)")
  void generalMemberCannotResetOther() throws Exception {
    mockMvc.perform(post(RESET_PATH).cookie(token("STUDENT", 901L)))
        .andExpect(status().isForbidden());
    verify(resetService, never()).reset(any(), any());
  }

  @Test
  @DisplayName("감사회원 → 다른 회원 초기화 DENY(403) — capability는 ADMIN 권한이 아니다")
  void thanksMemberCannotResetOther() throws Exception {
    // 감사회원 신분에 해당하는 토큰(role=STUDENT)으로도 동일하게 막힌다.
    mockMvc.perform(post(RESET_PATH).cookie(token("STUDENT", 902L)))
        .andExpect(status().isForbidden());
    // 목록·상세 조회 역시 ADMIN 전용 — 회원 정보를 열람할 수 없다.
    mockMvc.perform(get("/api/admin/newstart/members").cookie(token("STUDENT", 902L)))
        .andExpect(status().isForbidden());
    verify(resetService, never()).reset(any(), any());
  }

  @Test
  @DisplayName("ADMIN → 지정 회원 초기화 PASS(200) + no-store, 응답에 1회용 값이 실린다")
  void adminCanReset() throws Exception {
    when(resetService.reset(eq(77L), any()))
        .thenReturn(new AdminNewStartPasswordResetResponse(
            77L, "ns-member-01", "새출발러", "kq7m2xt9pbad", LocalDateTime.of(2026, 10, 2, 9, 0)));

    mockMvc.perform(post(RESET_PATH).cookie(token("ADMIN", 1L)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.temporaryPassword").value("kq7m2xt9pbad"))
        .andExpect(jsonPath("$.userId").value(77));
  }

  @Test
  @DisplayName("ADMIN이 아닌 principal로 서비스를 직접 호출하면 AccessDenied → 403")
  void serviceLevelGuardAlsoDenies() throws Exception {
    when(resetService.reset(eq(77L), any())).thenThrow(new AccessDeniedException("운영자만 회원 비밀번호를 초기화할 수 있습니다."));

    mockMvc.perform(post(RESET_PATH).cookie(token("ADMIN", 1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("회원 본인 API — 비로그인 DENY, 일반회원 PASS(대상 지정 인자가 없다)")
  void memberPasswordApiIsSelfOnly() throws Exception {
    mockMvc.perform(get("/api/newstart/me/password"))
        .andExpect(status().isUnauthorized());

    when(passwordService.status(901L)).thenReturn(new NewStartPasswordStatus(true, 8, null));
    mockMvc.perform(get("/api/newstart/me/password").cookie(token("STUDENT", 901L)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.changeRequired").value(true))
        .andExpect(jsonPath("$.minPasswordLength").value(8));

    mockMvc.perform(post("/api/newstart/me/password")
            .cookie(token("STUDENT", 901L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"kq7m2xt9pbad\",\"newPassword\":\"newpass123\",\"confirmPassword\":\"newpass123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.changeRequired").value(false));
    verify(passwordService).change(eq(901L), any());
  }

  @Test
  @DisplayName("본인 API로 다른 회원을 지정할 방법은 없다 — 경로에 userId 자리가 없다")
  void adminListSearchStaysAdminOnly() throws Exception {
    when(queryService.search(any(), any(), any())).thenReturn(
        new AdminNewStartMemberPage("", 0, 20, 0, 0, List.of()));

    mockMvc.perform(get("/api/admin/newstart/members").cookie(token("ADMIN", 1L)))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/admin/newstart/members").cookie(token("TUTOR", 5L)))
        .andExpect(status().isForbidden());
  }

  private Cookie token(String role, long userId) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "t-" + role.toLowerCase() + "-" + userId, role));
  }
}
