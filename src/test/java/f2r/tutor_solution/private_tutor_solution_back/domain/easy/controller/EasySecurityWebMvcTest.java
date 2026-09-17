package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyExecutiveService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyStudentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTuitionService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Easy 회계 — ADMIN 전용 계약. 익명 401 / STUDENT 403 / ADMIN 200.
 * 메뉴 숨김이 아니라 backend 매처가 강제한다.
 */
@DisplayName("Easy API — ADMIN 전용")
@WebMvcTest(controllers = {
    EasyBusinessController.class,
    EasyLedgerController.class,
    EasyStudentController.class,
    EasyTuitionController.class,
    EasyAccountController.class,
    EasyImportController.class,
    EasyInsightController.class,
    EasyAuditController.class
})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class EasySecurityWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean EasyBusinessService businessService;
  @MockitoBean EasyLedgerService ledgerService;
  @MockitoBean EasyStudentService studentService;
  @MockitoBean EasyTuitionService tuitionService;
  @MockitoBean EasyAccountService accountService;
  @MockitoBean EasyImportService importService;
  @MockitoBean EasyExecutiveService executiveService;
  @MockitoBean EasyAuditService auditService;
  @MockitoBean AcademyRepository academyRepository;

  private static final List<String> ENDPOINTS = List.of(
      "/api/admin/easy/businesses",
      "/api/admin/easy/summary",
      "/api/admin/easy/transactions",
      "/api/admin/easy/students",
      "/api/admin/easy/payments",
      "/api/admin/easy/accounts",
      "/api/admin/easy/accounts/rules",
      "/api/admin/easy/imports",
      "/api/admin/easy/executive-summary",
      "/api/admin/easy/reviews/summary",
      "/api/admin/easy/reviews",
      "/api/admin/easy/withholdings",
      "/api/admin/easy/closings");

  @Test
  @DisplayName("익명 → 401")
  void anonymous_unauthorized() throws Exception {
    for (String endpoint : ENDPOINTS) {
      mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  @DisplayName("STUDENT → 403")
  void student_forbidden() throws Exception {
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student01", "STUDENT"));
    for (String endpoint : ENDPOINTS) {
      mockMvc.perform(get(endpoint).cookie(student)).andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("ADMIN → 200")
  void admin_ok() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));
    for (String endpoint : ENDPOINTS) {
      mockMvc.perform(get(endpoint).cookie(admin)).andExpect(status().isOk());
    }
  }
}
