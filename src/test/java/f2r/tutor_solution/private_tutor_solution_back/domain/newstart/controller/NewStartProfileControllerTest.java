package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NewStart 전용 별명 REST 계약 테스트. 인증은 기존 {@code /api/**} catch-all을 그대로 따르는지,
 * 대상 회원이 항상 principal(요청 body 아님)로만 결정되는지(타인 별명 spoof 불가) 검증한다.
 */
@WebMvcTest(controllers = NewStartProfileController.class)
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
class NewStartProfileControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartMemberProfileService memberProfileService;
  @MockitoBean UserRepository userRepository;

  private Cookie userCookie(long userId) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "user" + userId, "STUDENT"));
  }

  private User user(long userId) {
    User u = User.builder().loginId("user" + userId).password("x").name("실명").nickname("닉네임").role(Role.STUDENT).build();
    org.springframework.test.util.ReflectionTestUtils.setField(u, "userId", userId);
    return u;
  }

  @Test
  void anonymousGetIsRejected() throws Exception {
    mockMvc.perform(get("/api/newstart/profile/me"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void anonymousUpdateIsRejected() throws Exception {
    mockMvc.perform(put("/api/newstart/profile/nickname")
            .contentType("application/json")
            .content("{\"nickname\":\"해킹시도\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void meReturnsCurrentDisplayNickname() throws Exception {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
    when(memberProfileService.resolveDisplayNickname(any())).thenReturn("새출발러73");

    mockMvc.perform(get("/api/newstart/profile/me").cookie(userCookie(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("새출발러73"));
  }

  @Test
  void updateNicknameSavesForAuthenticatedUserOnly() throws Exception {
    when(memberProfileService.updateNickname(1L, "새별명")).thenReturn("새별명");

    mockMvc.perform(put("/api/newstart/profile/nickname")
            .cookie(userCookie(1L))
            .contentType("application/json")
            .content("{\"nickname\":\"새별명\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("새별명"));

    verify(memberProfileService).updateNickname(1L, "새별명");
  }

  @Test
  @org.junit.jupiter.api.DisplayName("요청 body에 다른 회원 userId를 실어도 principal(로그인한 본인)만 대상이 된다 — spoof 불가")
  void cannotUpdateAnotherUsersNickname() throws Exception {
    when(memberProfileService.updateNickname(anyLong(), any())).thenReturn("아무값");

    // body에 userId 필드 자체가 없다(NicknameRequest는 nickname만 받는다) — 그래도 혹시 몰라
    // 엉뚱한 필드를 얹어 보내 봐도(Jackson이 무시) principal의 userId(1L)로만 처리되는지 확인.
    mockMvc.perform(put("/api/newstart/profile/nickname")
            .cookie(userCookie(1L))
            .contentType("application/json")
            .content("{\"nickname\":\"내별명\",\"userId\":999}"))
        .andExpect(status().isOk());

    verify(memberProfileService).updateNickname(eq(1L), eq("내별명"));
    verify(memberProfileService, never()).updateNickname(eq(999L), any());
  }

  @Test
  void invalidNicknameReturnsBadRequestWithMessage() throws Exception {
    when(memberProfileService.updateNickname(anyLong(), any()))
        .thenThrow(new IllegalArgumentException("별명은 한글/영문/숫자/공백/_/- 조합 2~20자로 입력해 주세요."));

    mockMvc.perform(put("/api/newstart/profile/nickname")
            .cookie(userCookie(1L))
            .contentType("application/json")
            .content("{\"nickname\":\"<script>\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorMessage").exists());
  }
}
