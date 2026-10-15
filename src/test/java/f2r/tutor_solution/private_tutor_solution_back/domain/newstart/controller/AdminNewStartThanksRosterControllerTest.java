package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService.AddResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService.RosterView;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
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
 * 감사회원 명단 수동 관리 API 계약: ADMIN 전용(비로그인·일반회원 4xx), 추가/삭제 위임 확인.
 */
@WebMvcTest(controllers = {AdminNewStartThanksRosterController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class AdminNewStartThanksRosterControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartThanksRosterAdminService rosterAdminService;

  private Cookie admin() { return new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin-f2r", "ADMIN")); }
  private Cookie student() { return new Cookie("ACCESS_TOKEN", tokenProvider.issue(7L, "naver_abc", "STUDENT")); }

  @Test
  void anonymousAndStudentAreRejected() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/thanks-roster")).andExpect(status().is4xxClientError());
    mockMvc.perform(post("/api/admin/newstart/thanks-roster")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nickname\":\"비비\",\"naverIdPrefix\":\"sooj\"}"))
        .andExpect(status().is4xxClientError());
    mockMvc.perform(get("/api/admin/newstart/thanks-roster").cookie(student()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void adminCanList() throws Exception {
    when(rosterAdminService.listManual(anyInt())).thenReturn(List.of(
        new RosterView(1L, "비비", "sooj", "감사멤버", LocalDate.of(2026, 10, 12), "UNMATCHED", false)));
    mockMvc.perform(get("/api/admin/newstart/thanks-roster").cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nickname").value("비비"));
  }

  @Test
  void adminCanAdd_delegatesToService() throws Exception {
    when(rosterAdminService.addManual(eq("비비"), eq("sooj"), any()))
        .thenReturn(new AddResult(true, null,
            new RosterView(2L, "비비", "sooj", "감사멤버", LocalDate.of(2026, 10, 12), "UNMATCHED", false)));
    mockMvc.perform(post("/api/admin/newstart/thanks-roster").cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nickname\":\"비비\",\"naverIdPrefix\":\"sooj\",\"gradeChangedAt\":\"2026-10-12\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.roster.nickname").value("비비"));
    verify(rosterAdminService).addManual(eq("비비"), eq("sooj"), eq(LocalDate.of(2026, 10, 12)));
  }

  @Test
  void adminAddDuplicate_is400() throws Exception {
    when(rosterAdminService.addManual(any(), any(), any()))
        .thenReturn(new AddResult(false, "이미 등록된 명단입니다.", null));
    mockMvc.perform(post("/api/admin/newstart/thanks-roster").cookie(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nickname\":\"비비\",\"naverIdPrefix\":\"sooj\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"));
  }

  @Test
  void adminCanDelete_delegatesToService() throws Exception {
    when(rosterAdminService.removeManual(3L)).thenReturn(new AddResult(true, null, null));
    mockMvc.perform(delete("/api/admin/newstart/thanks-roster/3").cookie(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
    verify(rosterAdminService).removeManual(3L);
  }

  @Test
  void adminDeleteLinked_is400() throws Exception {
    when(rosterAdminService.removeManual(anyLong()))
        .thenReturn(new AddResult(false, "이미 회원과 연결된 명단은 삭제할 수 없습니다.", null));
    mockMvc.perform(delete("/api/admin/newstart/thanks-roster/4").cookie(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"));
  }
}
