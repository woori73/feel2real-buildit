package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.AdminView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermCampaignAdminService;
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
 * 인가 경계: 캠페인 관리 API 는 TUTOR role 필터를 통과해야 도달한다. "다른 학원 TUTOR"·ADMIN
 * 차단은 서비스의 {@code requireOwnedAcademyByHandle} 담당이라 여기서 검증하지 않는다.
 */
@WebMvcTest(controllers = MidtermCampaignAdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class,
    JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class MidtermCampaignAdminAuthWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean MidtermCampaignAdminService service;

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, role.toLowerCase(), role));
  }

  private AdminView emptyView() {
    return new AdminView(false, null, null, null, null, null, null, null, null, null, null, null,
        null, false, false, null, null, null, null, List.of(), List.of(), List.of(), List.of());
  }

  @Test
  void anonymous_isRejected() throws Exception {
    mockMvc.perform(get("/api/tutor/lms/eduholic/midterm-campaign"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void student_isForbidden() throws Exception {
    mockMvc.perform(get("/api/tutor/lms/eduholic/midterm-campaign").cookie(cookie("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousWrite_isRejected() throws Exception {
    mockMvc.perform(put("/api/tutor/lms/eduholic/midterm-campaign")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"examName\":\"x\",\"semester\":\"2학기\",\"examType\":\"중간고사\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void tutor_reachesController() throws Exception {
    when(service.getView(anyString())).thenReturn(emptyView());
    mockMvc.perform(get("/api/tutor/lms/eduholic/midterm-campaign").cookie(cookie("TUTOR")))
        .andExpect(status().isOk());
  }
}
