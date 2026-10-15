package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialStatementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EasyTutorController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class EasyTutorSecurityWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean EasyTutorAccessService accessService;
  @MockitoBean EasyWorkspaceService workspaceService;
  @MockitoBean EasyAuditService auditService;
  @MockitoBean EasyLedgerInquiryService inquiryService;
  @MockitoBean EasyFinancialStatementService statementService;
  @MockitoBean AcademyRepository academyRepository;

  @Test
  @DisplayName("TUTOR는 원장 Easy API에 접근할 수 있다")
  void tutorCanAccessTutorEasyApi() throws Exception {
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "owner-a", "TUTOR"));
    mockMvc.perform(get("/api/tutor/easy/businesses").cookie(tutor))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("TUTOR 분개장 조회에도 사업체 권한 검증을 적용한다")
  void tutorJournalRequiresAllowedBusiness() throws Exception {
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "owner-a", "TUTOR"));
    mockMvc.perform(get("/api/tutor/easy/accounting/journal")
            .param("businessId", "7").cookie(tutor))
        .andExpect(status().isOk());
    verify(accessService).requireAllowedBusiness(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L));
  }

  @Test
  @DisplayName("TUTOR가 ADMIN Easy API에 직접 접근하면 403")
  void tutorCannotAccessAdminEasyApi() throws Exception {
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "owner-a", "TUTOR"));
    mockMvc.perform(get("/api/admin/easy/businesses").cookie(tutor))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ADMIN은 신규 Easy 장부 API를 사용할 수 있다")
  void adminCanAccessLedgerEasyApi() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "admin", "ADMIN"));
    mockMvc.perform(get("/api/tutor/easy/businesses").cookie(admin))
        .andExpect(status().isOk());
  }
}
