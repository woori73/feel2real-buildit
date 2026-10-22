package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import jakarta.servlet.http.Cookie;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BuildIt 통합 대시보드 공용 위젯 API.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>ADMIN·TUTOR 공용 (익명 401, STUDENT 403)</li>
 *   <li>TUTOR는 본인 학원 연결 사업체만 조회 (미연결 businessId → 403)</li>
 *   <li>ADMIN은 허용된 사업체 조회</li>
 *   <li>위젯 JSON 스키마 v1 준수</li>
 * </ul>
 */
@DisplayName("통합 대시보드 공용 위젯 API")
@WebMvcTest(controllers = DashboardWidgetController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class DashboardWidgetControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @Autowired DashboardWidgetController controller;

  @MockitoBean EasyWorkspaceService workspaceService;
  @MockitoBean EasyTutorAccessService tutorAccessService;

  @BeforeEach
  void clearWidgetCache() {
    controller.clearCache();
  }

  private Cookie adminCookie() {
    String token = tokenProvider.issue(1L, "admin1", "ADMIN");
    return new Cookie("ACCESS_TOKEN", token);
  }

  private Cookie tutorCookie() {
    String token = tokenProvider.issue(2L, "tutor1", "TUTOR");
    return new Cookie("ACCESS_TOKEN", token);
  }

  private Cookie studentCookie() {
    String token = tokenProvider.issue(3L, "student1", "STUDENT");
    return new Cookie("ACCESS_TOKEN", token);
  }

  private EasyBusiness businessFixture(Long id) {
    EasyBusiness b = EasyBusiness.builder().code("test-"+id).name("테스트학원").active(true).build();
    org.springframework.test.util.ReflectionTestUtils.setField(b,"businessId",id);
    return b;
  }

  private EasyWorkspaceMonthlyResponse monthlyFixture(Long businessId) {
    return new EasyWorkspaceMonthlyResponse(
        "2026-10", businessId, "테스트학원",
        12_450_000L, 3_210_000L, 9_240_000L, 1_850_000L, 142L,
        0L, 0L, 0L, 0L, 0L, 0L,
        "OPEN", false,
        EasyWorkspaceMonthlyResponse.Sources.contract(),
        List.of(),
        List.of(new EasyDashboardResponse.EasyOverdueStudent(1L, "홍길동", "테스트중", 500_000L)));
  }

  @Test
  @DisplayName("익명 요청은 401")
  void anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/dashboard/widgets")
            .param("businessId", "1")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("STUDENT 역할은 403")
  void student_returns403() throws Exception {
    mockMvc.perform(get("/api/dashboard/widgets")
            .param("businessId", "1")
            .cookie(studentCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("TUTOR는 본인 사업체 조회 성공")
  void tutor_ownBusiness_returnsOk() throws Exception {
    when(tutorAccessService.requireAllowedBusiness(any(), eq(1L)))
        .thenReturn(businessFixture(1L));
    when(workspaceService.monthly(eq(1L), any(YearMonth.class)))
        .thenReturn(monthlyFixture(1L));

    mockMvc.perform(get("/api/dashboard/widgets")
            .param("businessId", "1")
            .param("month", "2026-10")
            .cookie(tutorCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("1.0"))
        .andExpect(jsonPath("$.service").value("easy"))
        .andExpect(jsonPath("$.widgets[0].id").value("easy-finance-summary"));
  }

  @Test
  @DisplayName("TUTOR가 다른 학원 사업체 조회 시 403")
  void tutor_otherBusiness_returns403() throws Exception {
    when(tutorAccessService.requireAllowedBusiness(any(), eq(99L)))
        .thenThrow(new AccessDeniedException("연결되지 않은 사업체입니다."));

    mockMvc.perform(get("/api/dashboard/widgets")
            .param("businessId", "99")
            .cookie(tutorCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ADMIN은 허용된 사업체 조회 성공")
  void admin_allowedBusiness_returnsOk() throws Exception {
    when(tutorAccessService.requireAllowedBusiness(any(), eq(1L)))
        .thenReturn(businessFixture(1L));
    when(workspaceService.monthly(eq(1L), any(YearMonth.class)))
        .thenReturn(monthlyFixture(1L));

    mockMvc.perform(get("/api/dashboard/widgets")
            .param("businessId", "1")
            .cookie(adminCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets").isArray());
  }

  @Test
  @DisplayName("사업체 목록: TUTOR는 빈 목록도 정상 (연결 없음)")
  void businesses_tutorEmpty_returnsOk() throws Exception {
    when(tutorAccessService.allowedBusinesses(any())).thenReturn(List.of());

    mockMvc.perform(get("/api/dashboard/businesses")
            .cookie(tutorCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }
  @Test
  void givenCachedWidgets_whenExplicitlyRefreshed_thenReadsLedgerAgain() throws Exception {
    when(tutorAccessService.requireAllowedBusiness(any(), eq(1L))).thenReturn(businessFixture(1L));
    when(workspaceService.monthly(eq(1L), any(YearMonth.class))).thenReturn(monthlyFixture(1L));
    mockMvc.perform(get("/api/dashboard/widgets").param("businessId","1").cookie(tutorCookie())).andExpect(status().isOk());
    Mockito.clearInvocations(workspaceService);
    mockMvc.perform(get("/api/dashboard/widgets").param("businessId","1").cookie(tutorCookie())).andExpect(status().isOk());
    Mockito.verifyNoInteractions(workspaceService);
    mockMvc.perform(get("/api/dashboard/widgets").param("businessId","1").param("refresh","true").cookie(tutorCookie())).andExpect(status().isOk());
    Mockito.verify(workspaceService,Mockito.atLeastOnce()).monthly(eq(1L),any(YearMonth.class));
  }
  @Test
  void givenRevokedAccessAndCachedWidgets_whenRefreshed_thenForbiddenBeforeLedgerRead() throws Exception {
    when(tutorAccessService.requireAllowedBusiness(any(),eq(1L))).thenReturn(businessFixture(1L));
    when(workspaceService.monthly(eq(1L),any(YearMonth.class))).thenReturn(monthlyFixture(1L));
    mockMvc.perform(get("/api/dashboard/widgets").param("businessId","1").cookie(tutorCookie())).andExpect(status().isOk());
    Mockito.clearInvocations(workspaceService);
    when(tutorAccessService.requireAllowedBusiness(any(),eq(1L))).thenThrow(new AccessDeniedException("권한 회수"));
    mockMvc.perform(get("/api/dashboard/widgets").param("businessId","1").param("refresh","true").cookie(tutorCookie())).andExpect(status().isForbidden());
    Mockito.verifyNoInteractions(workspaceService);
  }
}
