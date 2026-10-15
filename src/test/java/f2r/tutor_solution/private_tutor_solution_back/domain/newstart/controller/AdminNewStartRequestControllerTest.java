package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestView;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 질문요청 운영자 검토 API의 권한 계약.
 *
 * <p>설계 §7의 "ADMIN은 전체"를 경로가 강제한다 — {@code /api/admin/**}는 SecurityConfig에서
 * {@code hasRole('ADMIN')}이므로 일반 회원은 controller에 닿기 전에 403이다(화면 숨김에 의존하지 않음).
 * 상태 이동 실패는 400 + 안내 문구만 내려보낸다.
 */
@WebMvcTest(controllers = AdminNewStartRequestController.class)
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
class AdminNewStartRequestControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartRequestService requestService;

  private static RequestView view(NewStartRequestStatus status) {
    return new RequestView(7L, "SITE_IMPROVEMENT", "12-3 링크 오류", "A to Z 12-3", "12-3",
        "오류 페이지로 갑니다", "공식 경로로 교체", status.name(), null, null, null, null, null,
        1L, "공용닉네임", "2026-09-27T09:00:00", List.of());
  }

  @Test
  @DisplayName("given: 익명 / when: 큐·검토 / then: 401")
  void anonymousCannotReachAdminApi() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/requests/queue")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/newstart/requests/7/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"ANSWERED\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given: 일반 회원 role / when: 큐·검토 / then: 403 + service 미호출")
  void memberRoleIsBlockedBeforeService() throws Exception {
    String studentToken = tokenProvider.issue(1L, "student", "STUDENT");

    mockMvc.perform(get("/api/admin/newstart/requests/queue")
            .cookie(new Cookie("ACCESS_TOKEN", studentToken)))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/newstart/requests/7/review")
            .cookie(new Cookie("ACCESS_TOKEN", studentToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"ANSWERED\"}"))
        .andExpect(status().isForbidden());

    verify(requestService, never()).queue(any());
    verify(requestService, never()).review(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("given: ADMIN / when: 큐 조회 / then: 기본 상태 RECEIVED, 오래된 순")
  void adminReadsReviewQueue() throws Exception {
    when(requestService.queue(NewStartRequestStatus.RECEIVED)).thenReturn(List.of(view(NewStartRequestStatus.RECEIVED)));

    mockMvc.perform(get("/api/admin/newstart/requests/queue")
            .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(7))
        .andExpect(jsonPath("$[0].aToZStep").value("12-3"))
        .andExpect(jsonPath("$[0].status").value("RECEIVED"));
  }

  @Test
  @DisplayName("given: ADMIN / when: ANSWERED 검토 / then: 200 + 변경 후 상태")
  void adminReviewMovesStatus() throws Exception {
    when(requestService.review(any(), eq(7L), eq(NewStartRequestStatus.ANSWERED),
        eq("공식 경로를 확인해 답변드립니다."), isNull(), isNull(), isNull()))
        .thenReturn(RequestResult.success(view(NewStartRequestStatus.ANSWERED)));

    mockMvc.perform(post("/api/admin/newstart/requests/7/review")
            .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"ANSWERED\",\"answer\":\"공식 경로를 확인해 답변드립니다.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.request.status").value("ANSWERED"));
  }

  @Test
  @DisplayName("given: 사유 없는 REJECTED / when: 검토 / then: 400 + 사유 요구")
  void adminRejectionWithoutReasonIs400() throws Exception {
    when(requestService.review(any(), eq(7L), eq(NewStartRequestStatus.REJECTED),
        any(), any(), any(), any()))
        .thenReturn(RequestResult.failed("거절·중복 처리에는 사유를 적어 주세요."));

    mockMvc.perform(post("/api/admin/newstart/requests/7/review")
            .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"REJECTED\",\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("거절·중복 처리에는 사유를 적어 주세요."));
  }

  @Test
  @DisplayName("given: 알 수 없는 상태 문자열 / when: 검토 / then: 400(서버가 8종 열거형만 허용)")
  void unknownStatusIs400() throws Exception {
    mockMvc.perform(post("/api/admin/newstart/requests/7/review")
            .cookie(new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"NOT_A_STATUS\"}"))
        .andExpect(status().isBadRequest());
    verify(requestService, never()).review(any(), any(), any(), any(), any(), any(), any());
  }
}
