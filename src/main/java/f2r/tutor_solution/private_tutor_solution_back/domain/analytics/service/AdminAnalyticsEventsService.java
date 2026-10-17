package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Period;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse.ClickRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse.Definitions;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse.EventRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse.SectionRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsEventCatalog.Def;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsEventCatalog.Type;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 통계 조회. 기간은 오늘·7일·30일(오늘 포함, Asia/Seoul)·누적이며, 사람(HUMAN)·비내부(관리자 제외) 이벤트만 센다.
 * 이 값들은 {@code site_page_visits}의 PV와 정의가 달라 합산하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AdminAnalyticsEventsService {

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final int retentionDays;

  @Autowired
  public AdminAnalyticsEventsService(
      JdbcTemplate jdbc, @Value("${app.analytics.events.retention-days:395}") int retentionDays) {
    this(jdbc, Clock.system(ZoneId.of("Asia/Seoul")), retentionDays);
  }

  AdminAnalyticsEventsService(JdbcTemplate jdbc, Clock clock, int retentionDays) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.retentionDays = retentionDays;
  }

  private static String ok(String alias) {
    return alias + ".traffic_kind = 'HUMAN' AND " + alias + ".is_internal = 0";
  }

  public AdminAnalyticsEventsResponse events() {
    LocalDate today = LocalDate.now(clock);
    LocalDate[] froms = {today, today.minusDays(6), today.minusDays(29), null};

    // "TYPE|target" → [UV×4(오늘,7,30,누적), 횟수×4]
    Map<String, long[]> byTarget = new HashMap<>();
    for (int p = 0; p < froms.length; p++) {
      final int idx = p;
      jdbc.query("SELECT e.event_type, e.target, COUNT(DISTINCT e.visitor_key), COUNT(*) FROM site_events e WHERE "
          + ok("e") + range("e", froms[p]) + " GROUP BY e.event_type, e.target", rs -> {
            long[] v = byTarget.computeIfAbsent(rs.getString(1) + "|" + rs.getString(2), k -> new long[8]);
            v[idx] = rs.getLong(3);
            v[4 + idx] = rs.getLong(4);
          }, rangeArgs(froms[p], today));
    }

    List<SectionRow> sections = new ArrayList<>();
    for (Def d : AnalyticsEventCatalog.all(Type.SECTION_VIEW)) {
      long[] v = byTarget.getOrDefault("SECTION_VIEW|" + d.target(), new long[8]);
      sections.add(new SectionRow(d.target(), d.label(), uv(v), cnt(v)));
    }

    List<ClickRow> clicks = new ArrayList<>();
    for (Def d : AnalyticsEventCatalog.all(Type.CLICK)) {
      long[] v = byTarget.getOrDefault("CLICK|" + d.target(), new long[8]);
      Double[] rates = new Double[4];
      String sectionLabel = null;
      if (d.section() != null) {
        sectionLabel = AnalyticsEventCatalog.find("SECTION_VIEW", d.page(), d.section()).map(Def::label).orElse(d.section());
        for (int p = 0; p < froms.length; p++) {
          rates[p] = ctr(d, froms[p], today);
        }
      }
      clicks.add(new ClickRow(d.target(), d.label(), d.section(), sectionLabel, uv(v), cnt(v),
          rates[2], rates[0], rates[1], rates[3]));
    }

    return new AdminAnalyticsEventsResponse(
        jdbc.query("SELECT MIN(event_date) FROM site_events",
            rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null),
        retentionDays, sections, clicks, rows(Type.SERVICE_MOVE, byTarget), rows(Type.FLOW_START, byTarget),
        new Period(inquiries(today), inquiries(froms[1]), inquiries(froms[2]), inquiries(null)), definitions());
  }

  /** 클릭률 = (같은 방문에서 섹션을 본 뒤 클릭한 방문 수) ÷ (섹션이 노출된 방문 수). 노출이 없으면 null. */
  private Double ctr(Def click, LocalDate from, LocalDate today) {
    Long exposed = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT s.view_id) FROM site_events s WHERE s.event_type = 'SECTION_VIEW' AND s.target = ? AND "
            + ok("s") + range("s", from), Long.class, args(from, today, click.section()));
    if (exposed == null || exposed == 0) return null;
    Long clicked = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT c.view_id) FROM site_events c JOIN site_events s ON s.view_id = c.view_id"
            + " AND s.event_type = 'SECTION_VIEW' AND s.target = ? AND " + ok("s")
            + " WHERE c.event_type = 'CLICK' AND c.target = ? AND " + ok("c") + range("c", from),
        Long.class, args(from, today, click.section(), click.target()));
    return clicked == null ? 0.0 : (double) clicked / exposed;
  }

  private static Object[] args(LocalDate from, LocalDate today, Object... lead) {
    List<Object> out = new ArrayList<>(List.of(lead));
    for (Object o : rangeArgs(from, today)) out.add(o);
    return out.toArray();
  }

  private static String range(String alias, LocalDate from) {
    return from == null ? "" : " AND " + alias + ".event_date BETWEEN ? AND ?";
  }

  private static Object[] rangeArgs(LocalDate from, LocalDate today) {
    return from == null ? new Object[0] : new Object[] {from, today};
  }

  private List<EventRow> rows(Type type, Map<String, long[]> byTarget) {
    List<EventRow> out = new ArrayList<>();
    for (Def d : AnalyticsEventCatalog.all(type)) {
      long[] v = byTarget.getOrDefault(type.name() + "|" + d.target(), new long[8]);
      out.add(new EventRow(d.target(), d.label(), uv(v), cnt(v)));
    }
    return out;
  }

  private static Period uv(long[] v) {
    return new Period(v[0], v[1], v[2], v[3]);
  }

  private static Period cnt(long[] v) {
    return new Period(v[4], v[5], v[6], v[7]);
  }

  /** 상담신청 완료는 클라이언트 이벤트가 아니라 접수된 문의(academy_inquiries)에서 센다. */
  private long inquiries(LocalDate from) {
    try {
      Long n = from == null
          ? jdbc.queryForObject("SELECT COUNT(*) FROM academy_inquiries", Long.class)
          : jdbc.queryForObject("SELECT COUNT(*) FROM academy_inquiries WHERE created_at >= ?", Long.class, from.atStartOfDay());
      return n == null ? 0 : n;
    } catch (org.springframework.dao.DataAccessException e) {
      return 0;
    }
  }

  private Definitions definitions() {
    return new Definitions(
        "섹션 노출 = 화면에서 50% 이상(또는 화면 높이의 50% 이상) 보이는 상태가 1초 이상 유지됐을 때 페이지 방문당 1번. 스쳐 지나간 영역은 세지 않습니다. UV는 방문자 중복 제거, 횟수는 노출된 페이지 방문 수입니다.",
        "클릭 = 허용된 버튼을 누른 횟수(한 페이지 방문에서 버튼당 최대 5회). 입력값·문의 내용은 수집하지 않습니다.",
        "클릭률 = 같은 페이지 방문에서 해당 섹션을 본 뒤 그 버튼을 클릭한 방문 수 ÷ 섹션이 노출된 방문 수. 섹션 밖 버튼은 클릭률을 계산하지 않습니다.",
        "이벤트는 페이지 조회(PV)와 다른 정의의 별도 지표입니다. PV와 합산하지 않습니다. Bot과 관리자 계정 트래픽은 제외합니다.",
        "이벤트는 " + retentionDays + "일 보관 후 매일 새벽 자동 삭제됩니다. 상담신청 완료는 접수된 문의 건수(academy_inquiries) 기준입니다.");
  }
}
