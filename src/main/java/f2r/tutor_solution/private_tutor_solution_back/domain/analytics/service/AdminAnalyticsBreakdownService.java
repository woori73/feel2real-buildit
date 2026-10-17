package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Conversion;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.DayPoint;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Definitions;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Period;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.TopPath;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsCatalog.PageGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsCatalog.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스·페이지 그룹별 UV/PV, 일별 추세, 실제 수집된 전환 지표.
 *
 * <ul>
 *   <li>PV: {@code site_page_visits} 1행 = 서버가 HTML 페이지를 200으로 응답한 1회(새로고침도 1회).
 *   <li>UV: 기간 안의 {@code visitor_key} distinct. 로그인 U:{id}, 비로그인 V:{쿠키 UUID}, 과거 S:{세션}. IP·fingerprint 미사용.
 *   <li>Bot: User-Agent가 Bot 패턴이면 traffic_kind=BOT으로 저장되며 여기서는 모두 제외한다.
 *   <li>관리자 화면(/admin/**)은 서비스 방문이 아니므로 제외한다. 날짜는 Asia/Seoul 기준이다.
 * </ul>
 */
@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AdminAnalyticsBreakdownService {

  private static final int TREND_DAYS = 30;
  private static final int TOP_PATHS = 15;
  private static final String HUMAN = "traffic_kind = 'HUMAN'";

  private final JdbcTemplate jdbc;
  private final Clock clock;

  @Autowired
  public AdminAnalyticsBreakdownService(JdbcTemplate jdbc) {
    this(jdbc, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  AdminAnalyticsBreakdownService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** @param trendService 추세를 한 서비스로 좁힐 때의 {@link Service} 이름. null이면 전체(관리자 화면 제외). */
  public AdminAnalyticsBreakdownResponse breakdown(String trendService) {
    LocalDate today = LocalDate.now(clock);
    LocalDate from7 = today.minusDays(6);
    LocalDate from30 = today.minusDays(29);
    Service selected = parseService(trendService);

    Map<String, long[]> svc = grouped(AnalyticsCatalog.serviceCaseSql("path"), today, from7, from30);
    Map<String, long[]> pg = grouped(AnalyticsCatalog.pageGroupCaseSql("path"), today, from7, from30);

    List<Row> services = new ArrayList<>();
    for (Service s : EnumSet.complementOf(EnumSet.of(Service.ADMIN_CONSOLE))) {
      services.add(row(s.name(), s.label(), svc.get(s.name())));
    }
    List<Row> pageGroups = new ArrayList<>();
    for (PageGroup g : PageGroup.values()) {
      pageGroups.add(row(g.name(), g.label(), pg.get(g.name())));
    }

    List<TopPath> top = jdbc.query(
        "SELECT path, COUNT(*) pv, COUNT(DISTINCT visitor_key) uv FROM site_page_visits WHERE " + HUMAN
            + " AND visit_date BETWEEN ? AND ? AND path NOT LIKE '/admin%' GROUP BY path ORDER BY pv DESC, path LIMIT " + TOP_PATHS,
        (rs, i) -> new TopPath(rs.getString(1), AnalyticsCatalog.serviceOf(rs.getString(1)).label(), rs.getLong(2), rs.getLong(3)),
        from30, today);

    String serviceFilter = selected == null
        ? " AND " + AnalyticsCatalog.serviceCaseSql("path") + " <> 'ADMIN_CONSOLE'"
        : " AND " + AnalyticsCatalog.serviceCaseSql("path") + " = '" + selected.name() + "'";
    Map<LocalDate, DayPoint> byDay = new HashMap<>();
    jdbc.query("SELECT visit_date, COUNT(DISTINCT visitor_key) uv, COUNT(*) pv FROM site_page_visits WHERE " + HUMAN
        + " AND visit_date BETWEEN ? AND ?" + serviceFilter + " GROUP BY visit_date",
        rs -> {
          LocalDate d = rs.getDate(1).toLocalDate();
          byDay.put(d, new DayPoint(d, rs.getLong(2), rs.getLong(3)));
        }, from30, today);
    List<DayPoint> trend = new ArrayList<>();
    for (LocalDate d = from30; !d.isAfter(today); d = d.plusDays(1)) {
      trend.add(byDay.getOrDefault(d, new DayPoint(d, 0, 0)));
    }

    LocalDate since = jdbc.query("SELECT MIN(visit_date) FROM site_page_visits",
        rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);

    return new AdminAnalyticsBreakdownResponse(since, services, pageGroups, AnalyticsCatalog.PAGES_WITHOUT_ROUTE, top,
        trend, selected == null ? null : selected.name(), conversions(today, from7, from30), definitions());
  }

  private List<Conversion> conversions(LocalDate today, LocalDate from7, LocalDate from30) {
    Period inquiries = new Period(
        inquiryCount(today), inquiryCount(from7), inquiryCount(from30), inquiryCount(null));
    String notCollected = "클라이언트 이벤트(버튼 클릭·섹션 노출) 수집이 아직 없습니다. 수집 테이블 도입 승인 후 집계됩니다.";
    return List.of(
        new Conversion("ACADEMY_INQUIRY", "학원 상담신청 접수(완료)", true, inquiries,
            "공개 학원 홈페이지에서 접수 완료된 건수(academy_inquiries). 방문 지표와 다른 기준이며 합산하지 않습니다."),
        new Conversion("CONSULT_START", "상담신청 시작(버튼 클릭)", false, null, notCollected),
        new Conversion("EXAM_START", "예상문제 제작 시작", false, null, notCollected),
        new Conversion("SERVICE_MOVE", "서비스 이동 클릭", false, null, notCollected),
        new Conversion("SECTION_VIEW", "홈페이지 섹션 노출", false, null, notCollected));
  }

  private long inquiryCount(LocalDate from) {
    try {
      Long n = from == null
          ? jdbc.queryForObject("SELECT COUNT(*) FROM academy_inquiries", Long.class)
          : jdbc.queryForObject("SELECT COUNT(*) FROM academy_inquiries WHERE created_at >= ?", Long.class, from.atStartOfDay());
      return n == null ? 0 : n;
    } catch (org.springframework.dao.DataAccessException e) {
      return 0; // 테이블이 아직 없는 환경(마이그레이션 전)에서도 화면은 뜬다.
    }
  }

  /** 서비스/페이지 그룹별 [오늘UV, 오늘PV, 7일UV, 7일PV, 30일UV, 30일PV, 누적UV, 누적PV]. */
  private Map<String, long[]> grouped(String caseSql, LocalDate today, LocalDate from7, LocalDate from30) {
    Map<String, long[]> out = new HashMap<>();
    LocalDate[] froms = {today, from7, from30, null};
    for (int p = 0; p < froms.length; p++) {
      final int idx = p;
      String where = HUMAN + (froms[p] == null ? "" : " AND visit_date BETWEEN ? AND ?");
      String sql = "SELECT " + caseSql + " k, COUNT(DISTINCT visitor_key) uv, COUNT(*) pv FROM site_page_visits WHERE " + where
          + " GROUP BY k";
      Object[] args = froms[p] == null ? new Object[0] : new Object[] {froms[p], today};
      jdbc.query(sql, rs -> {
        long[] v = out.computeIfAbsent(rs.getString(1), k -> new long[8]);
        v[idx * 2] = rs.getLong(2);
        v[idx * 2 + 1] = rs.getLong(3);
      }, args);
    }
    return out;
  }

  private static Row row(String key, String label, long[] v) {
    long[] x = v == null ? new long[8] : v;
    return new Row(key, label, new Period(x[0], x[2], x[4], x[6]), new Period(x[1], x[3], x[5], x[7]));
  }

  private static Service parseService(String name) {
    if (name == null || name.isBlank()) return null;
    try {
      Service s = Service.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
      return s == Service.ADMIN_CONSOLE ? null : s;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Definitions definitions() {
    return new Definitions(
        "UV = 기간 안의 방문자 식별값(visitor_key) 중복 제거. 로그인 회원은 회원 ID, 비로그인은 브라우저 쿠키(90일), 과거 데이터는 세션. IP·기기 지문은 쓰지 않습니다.",
        "PV = 서버가 HTML 페이지를 정상(200)으로 응답한 횟수. 새로고침·뒤로가기로 다시 열면 다시 셉니다. 정적 파일·API 호출은 세지 않습니다.",
        "Bot = User-Agent에 bot·crawler·spider 등이 포함된 요청(Googlebot, Bingbot, GPTBot 등). 모든 UV/PV에서 제외하고 '제외된 Bot 요청'에만 따로 셉니다.",
        "서비스별·페이지별 UV를 더한 값은 전체 UV가 아닙니다(한 방문자가 여러 서비스를 쓰면 각각 1명으로 셉니다). 관리자 화면 방문은 제외합니다.",
        "오늘·7일·30일은 오늘을 포함한 한국시간(Asia/Seoul) 달력 기준이며, 누적은 수집 시작일부터입니다.");
  }
}
