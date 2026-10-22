package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse.ListData;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse.ListItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse.StatCard;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse.StatCardsData;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardWidgetResponse.Widget;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt 통합 대시보드 공용 위젯 API v1 (EASY).
 *
 * <p>읽기 전용. 기존 {@link EasyWorkspaceService#monthly} 데이터를
 * Hub 공통 위젯 JSON 스키마 v1으로 변환만 한다. 비즈니스 로직을 중복 구현하지 않는다.
 *
 * <p>권한: ADMIN·TUTOR 공용.
 * 테넌트 격리: {@link EasyTutorAccessService#requireAllowedBusiness}가
 * 요청 businessId에 대한 접근 권한을 서버에서 검증한다.
 * 다른 학원의 businessId를 지정하면 403을 반환한다.
 * 캐시 키: businessId + month (사용자 간 데이터 혼합 방지).
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
public class DashboardWidgetController {

  private static final String WIDGET_VERSION = "1.0";
  private static final String SERVICE_ID = "easy";
  private static final int REFRESH_INTERVAL_SEC = 300;

  private final EasyWorkspaceService workspaceService;
  private final EasyTutorAccessService tutorAccessService;

  /** 캐시 키: businessId + month. 항목 수·수명 상한과 만료 제거는 {@link EasyDashboardWidgetCache}가 맡는다. */
  private final EasyDashboardWidgetCache cache = new EasyDashboardWidgetCache();

  /** 테스트용 — 캐시를 비운다. */
  void clearCache() {
    cache.clear();
  }

  @GetMapping("/widgets")
  public ResponseEntity<EasyDashboardWidgetResponse> widgets(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(defaultValue = "false") boolean refresh,
      @RequestParam(required = false)
      @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    // 서버에서 접근 권한 검증 — 다른 학원의 businessId면 403
    EasyBusiness business = tutorAccessService.requireAllowedBusiness(principal, businessId);

    YearMonth target = month == null ? YearMonth.now() : month;
    String cacheKey = business.getBusinessId() + ":" + target;

    // Approval/manual refresh must read committed ledger state rather than the five-minute cache.
    var cached = refresh ? java.util.Optional.<EasyDashboardWidgetResponse>empty() : cache.get(cacheKey);
    if (cached.isPresent()) {
      return ResponseEntity.ok(cached.get());
    }

    List<Widget> widgets = new ArrayList<>();
    String dataAsOf = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    Long allowedBusinessId = business.getBusinessId();

    try {
      widgets.add(buildFinanceSummary(allowedBusinessId, target, dataAsOf));
    } catch (Exception e) {
      log.warn("대시보드 위젯 생성 실패 (finance-summary): businessId={}", allowedBusinessId, e);
      widgets.add(errorWidget("easy-finance-summary", "이번달 수납/지출", dataAsOf));
    }

    try {
      widgets.add(buildOverdueStudents(allowedBusinessId, target, dataAsOf));
    } catch (Exception e) {
      log.warn("대시보드 위젯 생성 실패 (overdue-students): businessId={}", allowedBusinessId, e);
      widgets.add(errorWidget("easy-overdue-students", "미수 원생", dataAsOf));
    }

    try {
      widgets.add(buildRecentTransactions(allowedBusinessId, target, dataAsOf));
    } catch (Exception e) {
      log.warn("대시보드 위젯 생성 실패 (recent-transactions): businessId={}", allowedBusinessId, e);
      widgets.add(errorWidget("easy-recent-transactions", "최근 거래", dataAsOf));
    }

    EasyDashboardWidgetResponse response = new EasyDashboardWidgetResponse(
        WIDGET_VERSION, SERVICE_ID, dataAsOf, widgets);
    // 오류 위젯이 섞인 응답은 캐시하지 않는다.
    if (widgets.stream().noneMatch(w -> w.error() != null)) {
      cache.put(cacheKey, response);
    }
    return ResponseEntity.ok(response);
  }

  /**
   * 허용된 사업체 목록 조회.
   * ADMIN: 전체 사업체, TUTOR: 본인 학원에 연결된 사업체만.
   * 사업체 연결이 없는 TUTOR는 빈 목록을 반환한다.
   */
  @GetMapping("/businesses")
  public ResponseEntity<List<f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse>> businesses(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(tutorAccessService.allowedBusinesses(principal));
  }

  /**
   * 수납/지출/미수금 stat-cards 위젯.
   * 실제 구현된 데이터만 사용 (income/expense/receivable/studentCount).
   */
  private Widget buildFinanceSummary(Long businessId, YearMonth target, String dataAsOf) {
    EasyWorkspaceMonthlyResponse monthly = workspaceService.monthly(businessId, target);
    List<StatCard> cards = List.of(
        new StatCard("수납", monthly.income(), "원", null, null),
        new StatCard("지출", monthly.expense(), "원", null, null),
        new StatCard("미수금", monthly.receivable(), "원", null, null),
        new StatCard("원생 수", monthly.studentCount(), "명", null, null));
    return new Widget(
        "easy-finance-summary",
        "이번달 수납/지출",
        "stat-cards",
        new StatCardsData(cards),
        "/easy",
        REFRESH_INTERVAL_SEC,
        null,
        dataAsOf);
  }

  /** 미수 원생 list 위젯. */
  private Widget buildOverdueStudents(Long businessId, YearMonth target, String dataAsOf) {
    EasyWorkspaceMonthlyResponse monthly = workspaceService.monthly(businessId, target);
    List<ListItem> items = monthly.overdueStudents().stream()
        .limit(5)
        .map(s -> new ListItem(
            s.name(),
            "미수금 " + String.format("%,d", s.balance()) + "원",
            s.school()))
        .map(i -> (ListItem) i)
        .toList();
    return new Widget(
        "easy-overdue-students",
        "미수 원생",
        "list",
        new ListData(items, "미수 원생이 없습니다."),
        "/easy",
        REFRESH_INTERVAL_SEC,
        null,
        dataAsOf);
  }

  /** 최근 거래 list 위젯. */
  private Widget buildRecentTransactions(Long businessId, YearMonth target, String dataAsOf) {
    EasyWorkspaceMonthlyResponse monthly = workspaceService.monthly(businessId, target);
    List<ListItem> items = monthly.recentTransactions().stream()
        .limit(5)
        .map(t -> new ListItem(
            t.transaction().description(),
            String.format("%,d원", t.transaction().amount()),
            t.transaction().occurredOn().toString()))
        .map(i -> (ListItem) i)
        .toList();
    return new Widget(
        "easy-recent-transactions",
        "최근 거래",
        "list",
        new ListData(items, "최근 거래가 없습니다."),
        "/easy",
        REFRESH_INTERVAL_SEC,
        null,
        dataAsOf);
  }

  /** 위젯별 오류 격리: 실패한 위젯만 에러 표시, 다른 위젯은 정상. */
  private Widget errorWidget(String id, String title, String dataAsOf) {
    return new Widget(id, title, "stat-cards",
        new StatCardsData(List.of()),
        "/easy", REFRESH_INTERVAL_SEC,
        "일시적으로 표시할 수 없습니다.",
        dataAsOf);
  }
}
