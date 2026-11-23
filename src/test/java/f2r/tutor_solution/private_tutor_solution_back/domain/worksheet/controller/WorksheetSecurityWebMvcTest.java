package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetAssignmentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService;
import jakarta.servlet.http.Cookie;
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

@WebMvcTest(controllers = {WorksheetController.class, WorksheetAssignmentController.class})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("학습지 API 권한 — 원장·관리자 공용, 배정은 관리자 전용")
class WorksheetSecurityWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean EasyTutorAccessService accessService;
  @MockitoBean WorksheetService worksheetService;
  @MockitoBean WorksheetQuestionBank questionBank;
  @MockitoBean WorksheetAssignmentService assignmentService;

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "user-a", role));
  }

  @Test
  @DisplayName("given 원장(TUTOR) when 학습지 목록 then 200이고 사업체 권한을 서버에서 확인한다")
  void tutorCanList_withBusinessCheck() throws Exception {
    // given
    Cookie tutor = cookie("TUTOR");

    // when & then
    mockMvc.perform(get("/api/tutor/easy/worksheets").param("businessId", "3").cookie(tutor))
        .andExpect(status().isOk());
    verify(accessService).requireAllowedBusiness(any(), eq(3L));
    verify(worksheetService).list(eq(3L), eq("ACADEMY"), any());
  }

  @Test
  @DisplayName("given 원장 when 만들기·휴지통 then 201·204이고 매번 사업체 권한을 확인한다")
  void tutorCanCreateAndTrash() throws Exception {
    // given
    Cookie tutor = cookie("TUTOR");

    // when & then
    mockMvc.perform(post("/api/tutor/easy/worksheets").param("businessId", "3").cookie(tutor)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"가\",\"refs\":[\"NAESIN:1\"]}"))
        .andExpect(status().isCreated());
    mockMvc.perform(delete("/api/tutor/easy/worksheets/100").param("businessId", "3").cookie(tutor))
        .andExpect(status().isNoContent());
    verify(worksheetService).trash(eq(3L), eq(100L), any());
  }

  @Test
  @DisplayName("given 학생 회원·비회원 when 학습지 API then 403·401")
  void studentAndAnonymousAreBlocked() throws Exception {
    // given & when & then
    mockMvc.perform(get("/api/tutor/easy/worksheets").param("businessId", "3").cookie(cookie("STUDENT")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/tutor/easy/worksheets").param("businessId", "3"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 원장(TUTOR) when 배정 API then 403, 관리자(ADMIN)는 200 — 학생 이름이 나오는 배정은 관리자 전용")
  void assignmentIsAdminOnly() throws Exception {
    // given & when & then
    mockMvc.perform(get("/api/admin/easy/worksheets/100/assignments").param("businessId", "3").cookie(cookie("TUTOR")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/easy/worksheets/100/assignments").param("businessId", "3").cookie(cookie("ADMIN")))
        .andExpect(status().isOk());
    verify(assignmentService).panel(3L, 100L);
  }
}
