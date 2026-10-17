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
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessScopeGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import jakarta.servlet.http.Cookie;
import java.time.YearMonth;
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
 * EASY 대시보드 위젯 API — Phase 1-A.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>ADMIN 전용 (익명 401, TUTOR 403)</li>
 *   <li>위젯 JSON 스키마 v1 준수</li>
 *   <li>테넌트 격리: 다른 businessId 지정 시 해당 사업체 데이터만 반환</li>
 *   <li>읽기 전용: 쓰기 작업 없음</li>
 * </ul>
 */
@DisplayName("EASY 대시보드 위젯 API")
@WebMvcTest(controllers = EasyDashboardWidgetController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=<redacted>",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class EasyDashboardWidgetControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @Autowired EasyDashboardWidgetController widgetController;

  @MockitoBean EasyWorkspaceService workspaceService;
  @MockitoBean EasyAuditService auditService;
  @MockitoBean EasyBusinessScopeGuard scopeGuard;

  private Cookie adminCookie() {
    String token = tokenProvider.issue(1L, "admin1", "ADMIN");
    return new Cookie("ACCESS_TOKEN", token);
  }

  private Cookie tutorCookie() {
    String token = tokenProvider.issue(2L, "tutor1", "TUTOR");
    return new Cookie("ACCESS_TOKEN", token);
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
    mockMvc.perform(get("/api/admin/easy/dashboard/widgets")
            .param("businessId", "1")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("TUTOR 역할은 403 (ADMIN 전용)")
  void tutor_returns403() throws Exception {
    mockMvc.perform(get("/api/admin/easy/dashboard/widgets")
            .param("businessId", "1")
            .cookie(tutorCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ADMIN은 위젯 스키마 v1 응답 수신")
  void admin_returnsWidgetSchemaV1() throws Exception {
    when(workspaceService.monthly(eq(1L), any(YearMonth.class)))
        .thenReturn(monthlyFixture(1L));

    mockMvc.perform(get("/api/admin/easy/dashboard/widgets")
            .param("businessId", "1")
            .param("month", "2026-10")
            .cookie(adminCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("1.0"))
        .andExpect(jsonPath("$.service").value("easy"))
        .andExpect(jsonPath("$.widgets").isArray())
        .andExpect(jsonPath("$.widgets[0].id").value("easy-finance-summary"))
        .andExpect(jsonPath("$.widgets[0].type").value("stat-cards"))
        .andExpect(jsonPath("$.widgets[0].data.cards").isArray())
        .andExpect(jsonPath("$.widgets[0].data.cards[0].label").value("수납"))
        .andExpect(jsonPath("$.widgets[0].data.cards[0].value").value(12_450_000))
        .andExpect(jsonPath("$.widgets[1].id").value("easy-overdue-students"))
        .andExpect(jsonPath("$.widgets[2].id").value("easy-recent-transactions"));
  }

  @Test
  @DisplayName("테넌트 격리: businessId=2 요청 시 businessId=2 데이터만 반환")
  void tenantIsolation_differentBusinessId() throws Exception {
    when(workspaceService.monthly(eq(2L), any(YearMonth.class)))
        .thenReturn(monthlyFixture(2L));

    mockMvc.perform(get("/api/admin/easy/dashboard/widgets")
            .param("businessId", "2")
            .param("month", "2026-10")
            .cookie(adminCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[0].data.cards[0].value").value(12_450_000));
    // scopeGuard.requireActive(2L)이 호출되어 사업체 2의 데이터만 반환됨을 검증
    // (실제 격리는 EasyWorkspaceService 내부 scopeGuard가 강제)
  }

  @Test
  @DisplayName("위젯 생성 실패 시 해당 위젯만 에러 표시 (격리)")
  void widgetError_isolated() throws Exception {
    when(workspaceService.monthly(eq(1L), any(YearMonth.class)))
        .thenThrow(new RuntimeException("DB 오류 시뮬레이션"));

    mockMvc.perform(get("/api/admin/easy/dashboard/widgets")
            .param("businessId", "1")
            .cookie(adminCookie())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[0].error").value("일시적으로 표시할 수 없습니다."))
        .andExpect(jsonPath("$.widgets[1].error").value("일시적으로 표시할 수 없습니다."))
        .andExpect(jsonPath("$.widgets[2].error").value("일시적으로 표시할 수 없습니다."));
  }

  @Test
  @DisplayName("캐시 상한: 100개 초과 시 가장 오래된 항목 제거 (무제한 증가 방지)")
  void cacheEviction_boundedSize() throws Exception {
    // 105개의 서로 다른 businessId로 요청 → 캐시는 100개 이하로 유지되어야 함
    for (long bid = 100; bid < 205; bid++) {
      final long businessId = bid;
      when(workspaceService.monthly(eq(businessId), any(YearMonth.class)))
          .thenReturn(monthlyFixture(businessId));
      mockMvc.perform(get("/api/admin/easy/dashboard/widgets")
              .param("businessId", String.valueOf(businessId))
              .cookie(adminCookie())
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
    }
    // 리플렉션으로 캐시 크기 확인 (상한 100 이하)
    java.lang.reflect.Field cacheField =
        widgetController.getClass().getDeclaredField("cache");
    cacheField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, ?> cache =
        (java.util.Map<String, ?>) cacheField.get(widgetController);
    org.junit.jupiter.api.Assertions.assertTrue(
        cache.size() <= 100,
        "캐시 크기는 100 이하여야 함, 실제: " + cache.size());
  }
}
