package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.ExceptionView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Outcome;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Result;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 감사회원 셀프 인증 API 권한·계약: 회원 API는 로그인 필요, 예외함 API는 ADMIN 전용(일반회원 403).
 * 인증 성공/실패 응답은 어느 항목이 틀렸는지 드러내지 않고, DB 1:1 경합은 별도 예외로 남긴다.
 */
@WebMvcTest(controllers = {NewStartThanksVerificationController.class, AdminNewStartThanksExceptionController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartThanksVerificationControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartThanksVerificationService verificationService;

  private Cookie student() { return new Cookie("ACCESS_TOKEN", tokenProvider.issue(7L, "naver_abc", "STUDENT")); }
  private Cookie admin() { return new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin-f2r", "ADMIN")); }

  @Test
  void anonymousIsRejectedOnBothSides() throws Exception {
    mockMvc.perform(get("/api/newstart/thanks-verification/status")).andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/newstart/thanks-verification").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"x\"}"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(get("/api/admin/newstart/thanks-exceptions")).andExpect(status().is4xxClientError());
  }

  @Test
  void memberVerifiesWithNicknameOnly_andGetsApprovedWithoutOperator() throws Exception {
    when(verificationService.verify(7L, "STUDENT", "카페닉")).thenReturn(new Outcome("APPROVED", "감사회원으로 확인되었습니다."));

    mockMvc.perform(post("/api/newstart/thanks-verification").cookie(student())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"카페닉\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
    verify(verificationService).verify(7L, "STUDENT", "카페닉");
  }

  @Test
  void adminCallsAreRoutedWithAdminRole_soServiceCanExemptThem() throws Exception {
    when(verificationService.status(9L, "ADMIN")).thenReturn(new Outcome("ADMIN", "운영자는 인증 불필요"));
    mockMvc.perform(get("/api/newstart/thanks-verification/status").cookie(admin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ADMIN"));
  }

  @Test
  void validationErrorIs400_failureAndExceptionAre200() throws Exception {
    when(verificationService.verify(eq(7L), anyString(), eq("a"))).thenReturn(new Outcome("ERROR", "닉네임을 정확히 입력해 주세요."));
    when(verificationService.verify(eq(7L), anyString(), eq("틀림"))).thenReturn(new Outcome("FAILED", "확인하지 못했습니다."));
    when(verificationService.verify(eq(7L), anyString(), eq("예외"))).thenReturn(new Outcome("EXCEPTION", "운영자에게 전달했습니다."));
    String[][] cases = {{"a", "400"}, {"틀림", "200"}, {"예외", "200"}};
    for (String[] c : cases) {
      var r = mockMvc.perform(post("/api/newstart/thanks-verification").cookie(student())
          .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + c[0] + "\"}")).andReturn().getResponse();
      org.assertj.core.api.Assertions.assertThat(r.getStatus()).as(c[0]).isEqualTo(Integer.parseInt(c[1]));
    }
  }

  @Test
  void concurrentUniqueViolationBecomesAnExceptionRecord() throws Exception {
    when(verificationService.verify(eq(7L), anyString(), eq("카페닉"))).thenThrow(new DataIntegrityViolationException("dup"));
    when(verificationService.recordConflict(7L, "카페닉")).thenReturn(new Outcome("EXCEPTION", "운영자에게 전달했습니다."));

    mockMvc.perform(post("/api/newstart/thanks-verification").cookie(student())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"카페닉\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("EXCEPTION"));
    verify(verificationService).recordConflict(7L, "카페닉");
  }

  @Test
  void exceptionInboxIsAdminOnly() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/thanks-exceptions").cookie(student())).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/newstart/thanks-exceptions/5/resolve").cookie(student())).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/newstart/thanks-exceptions/5/dismiss").cookie(student())
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    verify(verificationService, never()).adminResolve(anyLong(), any(), anyString());
  }

  @Test
  void adminResolvesAndConflictIs409() throws Exception {
    when(verificationService.adminListExceptions(any())).thenReturn(List.<ExceptionView>of());
    mockMvc.perform(get("/api/admin/newstart/thanks-exceptions").cookie(admin())).andExpect(status().isOk());

    ExceptionView view = new ExceptionView(5L, "EMAIL_MISSING", "OAuth 이메일 없음", "카페닉", 7L, "닉", 11L, "카페닉", List.of(),
        "SENT", "RESOLVED", null);
    when(verificationService.adminResolve(5L, null, "admin-f2r")).thenReturn(Result.success(view));
    mockMvc.perform(post("/api/admin/newstart/thanks-exceptions/5/resolve").cookie(admin()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.exception.status").value("RESOLVED"));

    when(verificationService.adminResolve(6L, null, "admin-f2r")).thenThrow(new DataIntegrityViolationException("dup"));
    mockMvc.perform(post("/api/admin/newstart/thanks-exceptions/6/resolve").cookie(admin())).andExpect(status().isConflict());
  }
}
