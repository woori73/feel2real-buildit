package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 이벤트 저장. 허용 목록({@link AnalyticsEventCatalog})에 없는 이벤트·페이지는 버리고, 페이지·섹션은 카탈로그가 정한 값만 저장한다.
 * 같은 (view_id, 이벤트, 대상, seq)는 unique key + INSERT IGNORE로 한 번만 저장된다(페이지 방문 내 중복 노출 방지).
 * 통계 기능 실패가 사용자 흐름을 깨서는 안 되므로 예외를 삼킨다.
 */
@Slf4j
@Service
public class SiteEventRecordingService {

  public static final int MAX_EVENTS_PER_REQUEST = 20;
  /** 클릭·서비스 이동은 한 방문에서 대상별 최대 이 횟수까지만 센다. */
  static final int MAX_REPEAT_PER_VIEW = 5;

  public record Event(String type, String target, Integer seq) {}

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final int retentionDays;

  @Autowired
  public SiteEventRecordingService(
      JdbcTemplate jdbc, @Value("${app.analytics.events.retention-days:395}") int retentionDays) {
    this(jdbc, Clock.system(ZoneId.of("Asia/Seoul")), retentionDays);
  }

  SiteEventRecordingService(JdbcTemplate jdbc, Clock clock, int retentionDays) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.retentionDays = retentionDays;
  }

  /**
   * @return 새로 저장된 이벤트 수(중복·허용 외 이벤트는 제외). 요청 형식이 잘못되면 0.
   */
  public int record(String page, String viewId, List<Event> events, String visitorKey, boolean bot, boolean internal) {
    if (!AnalyticsEventCatalog.isKnownPage(page) || !AnalyticsVisitorIdentity.isUuid(viewId)
        || events == null || events.isEmpty() || visitorKey == null) {
      return 0;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDate today = now.toLocalDate();
    Set<String> seen = new HashSet<>();
    int saved = 0;
    try {
      for (Event e : events.subList(0, Math.min(events.size(), MAX_EVENTS_PER_REQUEST))) {
        if (e == null) continue;
        var def = AnalyticsEventCatalog.find(e.type(), page, e.target());
        if (def.isEmpty()) continue;
        int seq = normalizedSeq(def.get().type(), e.seq());
        if (seq < 0 || !seen.add(def.get().type() + "|" + def.get().target() + "|" + seq)) continue;
        saved += jdbc.update(
            "INSERT IGNORE INTO site_events (event_date, occurred_at, event_type, target, section, page_key, view_id,"
                + " seq, visitor_key, traffic_kind, is_internal) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            today, now, def.get().type().name(), def.get().target(), def.get().section(), page, viewId, seq,
            visitorKey, bot ? "BOT" : "HUMAN", internal ? 1 : 0);
      }
    } catch (Exception ex) {
      log.warn("사이트 이벤트 기록 실패(page={}): {}", page, ex.getMessage());
    }
    return saved;
  }

  /** 노출·흐름 시작은 방문당 1회(seq 0 고정), 클릭·서비스 이동은 0..{@link #MAX_REPEAT_PER_VIEW}-1. 범위 밖이면 -1. */
  static int normalizedSeq(AnalyticsEventCatalog.Type type, Integer seq) {
    if (type == AnalyticsEventCatalog.Type.SECTION_VIEW || type == AnalyticsEventCatalog.Type.FLOW_START) return 0;
    int s = seq == null ? 0 : seq;
    return s >= 0 && s < MAX_REPEAT_PER_VIEW ? s : -1;
  }

  /** 보관 기간이 지난 이벤트를 배치로 삭제한다. @return 삭제 행 수 */
  public int purgeExpired() {
    LocalDate cutoff = LocalDate.now(clock).minusDays(retentionDays);
    int total = 0;
    int n;
    do {
      n = jdbc.update("DELETE FROM site_events WHERE event_date < ? LIMIT 5000", cutoff);
      total += n;
    } while (n == 5000);
    return total;
  }

  public int retentionDays() {
    return retentionDays;
  }
}
