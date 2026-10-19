package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessCardAccountingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyReportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyShopImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyExecutiveService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionManagementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionSchedulerService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLocalCollectorService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessScopeGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectorTaskService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyStudentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTuitionService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import f2r.tutor_solution.private_tutor_solution_back.program.Program;
import f2r.tutor_solution.private_tutor_solution_back.program.ProgramIntroController;
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
    EasyAuditController.class,
    EasyReportController.class,
    EasyCollectionController.class,
    EasyCardReviewController.class,
    EasyCollectorAdminController.class,
    EasyWorkspaceController.class,
    ProgramIntroController.class
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
  @MockitoBean EasyReportService reportService;
  @MockitoBean EasyShopImportService shopImportService;
  @MockitoBean EasyCollectionIngestService collectionIngestService;
  @MockitoBean EasyCollectionManagementService collectionManagementService;
  @MockitoBean EasyCollectionSchedulerService collectionSchedulerService;
  @MockitoBean EasyBusinessCardAccountingService cardAccountingService;
  @MockitoBean EasyLocalCollectorService localCollectorService;
  @MockitoBean EasyCollectorTaskService collectorTaskService;
  @MockitoBean EasyWorkspaceService workspaceService;
  @MockitoBean EasyBusinessScopeGuard businessScopeGuard;
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
      "/api/admin/easy/closings",
      "/api/admin/easy/pl",
      "/api/admin/easy/filing-references",
      "/api/admin/easy/income-tax",
      "/api/admin/easy/corrections/logs",
      "/api/admin/easy/collections/connections?businessId=1",
      "/api/admin/easy/card-events?businessId=1",
      "/api/admin/easy/workspace/monthly?businessId=1&month=2026-10",
      "/api/admin/easy/collector?businessId=1",
      "/api/admin/easy/collector/tasks?businessId=1");

  @Test
  @DisplayName("익명 브라우저 /ledger → 로그인 이동 대신 공개 소개 화면(내부 forward)")
  void anonymousLedger_showsPublicIntro() throws Exception {
    mockMvc.perform(get("/ledger").accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/program/intro"))
        .andExpect(request().attribute(ProgramIntroController.PROGRAM_ATTRIBUTE, Program.LEDGER));
  }

  @Test
  @DisplayName("STUDENT 브라우저 /ledger → raw 403·로그인 이동 대신 서비스 안내 화면(내부 forward)")
  void nonTutorLedger_showsServiceNoticeInsteadOfRawForbidden() throws Exception {
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student01", "STUDENT"));
    mockMvc.perform(get("/ledger").cookie(student).accept(MediaType.TEXT_HTML))
        .andExpect(forwardedUrl("/program/intro"))
        .andExpect(request().attribute(ProgramIntroController.PROGRAM_ATTRIBUTE, Program.LEDGER));
  }

  @Test
  @DisplayName("이지장부 하위 화면 → 익명은 원래 경로를 싣고 로그인 안내, 권한 없는 로그인은 /ledger 안내로")
  void ledgerSubPages_anonymousLoginGuidance_and_nonTutorNotice() throws Exception {
    mockMvc.perform(get("/ledger/accounts").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?login=required&returnUrl=%2Fledger%2Faccounts"));
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student01", "STUDENT"));
    mockMvc.perform(get("/ledger/accounts").cookie(student).accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/ledger"));
  }

  @Test
  @DisplayName("given 비회원 브라우저 when /dashboard를 열면 then raw 401 대신 원래 경로를 싣고 로그인 안내로 이동한다")
  void dashboard_anonymousBrowser_redirectsToLoginGuidance() throws Exception {
    // given / when / then
    mockMvc.perform(get("/dashboard").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?login=required&returnUrl=%2Fdashboard"));
  }

  @Test
  @DisplayName("given 비회원 JSON 요청 when /api/dashboard/widgets를 호출하면 then 기존 401 계약을 유지한다")
  void dashboardApi_anonymous_stays401() throws Exception {
    // given / when / then
    mockMvc.perform(get("/api/dashboard/widgets").param("businessId", "1").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("TUTOR·ADMIN 브라우저 /ledger → 화면 접근")
  void tutorAndAdminLedger_ok() throws Exception {
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(3L, "tutor01", "TUTOR"));
    mockMvc.perform(get("/ledger").cookie(tutor).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));
    mockMvc.perform(get("/ledger").cookie(admin).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null));
  }

  @Test
  @DisplayName("Easy 장부 /easy → 익명·TUTOR는 소개/안내 화면, ADMIN만 화면 접근")
  void easyRoot_introForAnonymousAndTutor_adminOk() throws Exception {
    mockMvc.perform(get("/easy").accept(MediaType.TEXT_HTML))
        .andExpect(forwardedUrl("/program/intro"))
        .andExpect(request().attribute(ProgramIntroController.PROGRAM_ATTRIBUTE, Program.EASY));
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(3L, "tutor01", "TUTOR"));
    mockMvc.perform(get("/easy").cookie(tutor).accept(MediaType.TEXT_HTML))
        .andExpect(forwardedUrl("/program/intro"))
        .andExpect(request().attribute(ProgramIntroController.PROGRAM_ATTRIBUTE, Program.EASY));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));
    mockMvc.perform(get("/easy").cookie(admin).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null));
  }

  @Test
  @DisplayName("Easy 하위 화면 → 익명은 원래 경로를 싣고 로그인 안내, TUTOR는 /easy 안내로, ADMIN은 접근")
  void easySubPages_guidanceAndAdminAccess() throws Exception {
    mockMvc.perform(get("/easy/students").accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?login=required&returnUrl=%2Feasy%2Fstudents"));
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(3L, "tutor01", "TUTOR"));
    mockMvc.perform(get("/easy/students").cookie(tutor).accept(MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/easy"));
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));
    mockMvc.perform(get("/easy/students").cookie(admin).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("익명 → 401")
  void anonymous_unauthorized() throws Exception {
    for (String endpoint : ENDPOINTS) {
      mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  @DisplayName("TUTOR → ADMIN Easy API 403")
  void tutor_forbiddenFromAdminEasyApi() throws Exception {
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokenProvider.issue(3L, "tutor01", "TUTOR"));
    for (String endpoint : ENDPOINTS) {
      mockMvc.perform(get(endpoint).cookie(tutor)).andExpect(status().isForbidden());
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
