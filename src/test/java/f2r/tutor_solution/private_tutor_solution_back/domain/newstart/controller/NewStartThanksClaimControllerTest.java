package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.AdminClaimView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.MyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksClaimService.Result;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
 * 감사회원 본인확인 API 권한 계약: 회원 API는 로그인 필요, 운영자 API는 ADMIN 전용(일반회원 403).
 * 회원 API는 어떤 경로로도 승인/부여를 호출하지 않는다.
 */
@WebMvcTest(controllers = {NewStartThanksClaimController.class, AdminNewStartThanksClaimController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartThanksClaimControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartThanksClaimService claimService;

  private Cookie student() { return new Cookie("ACCESS_TOKEN", tokenProvider.issue(7L, "naver_abc", "STUDENT")); }
  private Cookie admin() { return new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin-f2r", "ADMIN")); }

  @Test
  void anonymousIsRejectedOnBothSides() throws Exception {
    mockMvc.perform(get("/api/newstart/thanks-claim/status")).andExpect(status().is4xxClientError());
    mockMvc.perform(get("/api/admin/newstart/thanks-claims")).andExpect(status().is4xxClientError());
  }

  @Test
  void memberCanRequestButCannotReachAdminApproval() throws Exception {
    when(claimService.submit(anyLong(), anyLong())).thenReturn(Result.success(
        new MyStatus("PENDING", 5L, "카페닉", "ssgw****", "ABCD2345", "운영자 확인 대기 중")));

    mockMvc.perform(post("/api/newstart/thanks-claim").cookie(student())
            .contentType(MediaType.APPLICATION_JSON).content("{\"rosterId\":11}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claim.state").value("PENDING"))
        .andExpect(jsonPath("$.claim.verifyCode").value("ABCD2345"));

    // 일반회원(STUDENT)은 승인/반려/목록 어디에도 접근할 수 없다 — 자기 요청을 스스로 승인하는 경로가 없다.
    mockMvc.perform(post("/api/admin/newstart/thanks-claims/5/approve").cookie(student()))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/newstart/thanks-claims/5/reject").cookie(student())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/newstart/thanks-claims").cookie(student())).andExpect(status().isForbidden());
    verify(claimService, never()).approve(anyLong(), anyString());
  }

  @Test
  void adminApprovesWithOperatorMarkerAndConflictIsTranslated() throws Exception {
    when(claimService.approve(5L, "admin-f2r")).thenReturn(Result.success(
        new AdminClaimView(5L, 7L, "naver_abc", "홍길동", "닉", 11L, "카페닉", "ssgw****", "ABCD2345", true, false,
            "APPROVED", null)));
    mockMvc.perform(post("/api/admin/newstart/thanks-claims/5/approve").cookie(admin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.claim.status").value("APPROVED"));
    verify(claimService).approve(5L, "admin-f2r");

    when(claimService.approve(6L, "admin-f2r")).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
    mockMvc.perform(post("/api/admin/newstart/thanks-claims/6/approve").cookie(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorMessage").value("이미 다른 회원과 연결된 명단이거나 이미 연결된 회원입니다."));
  }

  @Test
  void adminListsPendingClaims() throws Exception {
    when(claimService.adminList(any())).thenReturn(List.of());
    mockMvc.perform(get("/api/admin/newstart/thanks-claims").cookie(admin())).andExpect(status().isOk());
  }
}
