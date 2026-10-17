package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsEventsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteEventRecordingService.Event;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** site_events 저장(중복·허용 목록·Bot/내부)·정리·집계(UV·노출·클릭률)를 실제 MySQL에서 검증한다. 테스트는 롤백되는 트랜잭션 안에서 실행한다. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("site_events MySQL — 중복 방지·허용 목록·Bot/내부 제외·보관 정리·클릭률")
class SiteEventMySqlTest {

  @Autowired private JdbcTemplate jdbc;

  private SiteEventRecordingService recorder() {
    return new SiteEventRecordingService(jdbc, Clock.system(ZoneId.of("Asia/Seoul")), 395);
  }

  private AdminAnalyticsEventsService stats() {
    return new AdminAnalyticsEventsService(jdbc, Clock.system(ZoneId.of("Asia/Seoul")), 395);
  }

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM site_events");
  }

  private static String view() {
    return UUID.randomUUID().toString();
  }

  private static Event ev(String type, String target) {
    return new Event(type, target, 0);
  }

  private long rows() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM site_events", Long.class);
  }

  @Test
  @DisplayName("given 같은 페이지 방문(viewId)에서 같은 섹션 노출을 여러 번 보내면 when 저장하면 then 1건만 저장된다")
  void sameViewSectionExposure_storedOnce() {
    // given
    String v = view();
    // when
    int first = recorder().record("home", v, List.of(ev("SECTION_VIEW", "wiki")), "V:a", false, false);
    int again = recorder().record("home", v, List.of(ev("SECTION_VIEW", "wiki"), ev("SECTION_VIEW", "wiki")), "V:a", false, false);
    // then
    assertThat(first).isEqualTo(1);
    assertThat(again).isZero();
    assertThat(rows()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 같은 방문자가 새 페이지 방문(다른 viewId)에서 다시 보면 when 저장하면 then 노출 2회·UV 1명으로 집계된다")
  void newViewCountsAgain_uvStaysOne() {
    // given
    recorder().record("home", view(), List.of(ev("SECTION_VIEW", "wiki")), "V:a", false, false);
    recorder().record("home", view(), List.of(ev("SECTION_VIEW", "wiki")), "V:a", false, false);
    // when
    var wiki = stats().events().sections().stream().filter(s -> s.key().equals("wiki")).findFirst().orElseThrow();
    // then
    assertThat(wiki.exposures().today()).isEqualTo(2);
    assertThat(wiki.visitors().today()).isEqualTo(1);
    assertThat(wiki.exposures().total()).isEqualTo(2);
  }

  @Test
  @DisplayName("given 허용 목록 밖 이벤트·잘못된 페이지·잘못된 viewId·seq 범위 밖 when 저장하면 then 하나도 저장하지 않는다")
  void invalidInputs_rejected() {
    String v = view();
    assertThat(recorder().record("home", v, List.of(ev("SECTION_VIEW", "no_such_section")), "V:a", false, false)).isZero();
    assertThat(recorder().record("home", v, List.of(ev("DROP", "wiki")), "V:a", false, false)).isZero();
    assertThat(recorder().record("evil", v, List.of(ev("SECTION_VIEW", "wiki")), "V:a", false, false)).isZero();
    assertThat(recorder().record("home", "not-a-uuid", List.of(ev("SECTION_VIEW", "wiki")), "V:a", false, false)).isZero();
    assertThat(recorder().record("home", v, List.of(new Event("CLICK", "nav_contact", 99)), "V:a", false, false)).isZero();
    assertThat(recorder().record("home", v, List.of(new Event("CLICK", "nav_contact", -1)), "V:a", false, false)).isZero();
    assertThat(rows()).isZero();
  }

  @Test
  @DisplayName("given 같은 방문에서 같은 버튼을 7번 클릭 when 저장하면 then 최대 5회까지만 센다")
  void repeatedClicks_capped() {
    String v = view();
    for (int i = 0; i < 7; i++) {
      recorder().record("home", v, List.of(new Event("CLICK", "nav_contact", i)), "V:a", false, false);
    }
    assertThat(rows()).isEqualTo(5);
  }

  @Test
  @DisplayName("given 페이지·섹션이 서버 카탈로그 값 when 저장하면 then 클라이언트가 보낸 값이 아니라 카탈로그 값이 저장된다")
  void sectionComesFromCatalog() {
    recorder().record("home", view(), List.of(ev("CLICK", "wiki_kakao")), "V:a", false, false);
    String section = jdbc.queryForObject("SELECT section FROM site_events", String.class);
    String pageKey = jdbc.queryForObject("SELECT page_key FROM site_events", String.class);
    assertThat(section).isEqualTo("wiki");
    assertThat(pageKey).isEqualTo("home");
  }

  @Test
  @DisplayName("given Bot·관리자(내부) 이벤트 when 집계하면 then 저장은 되지만 통계에서 제외된다")
  void botAndInternalExcludedFromStats() {
    recorder().record("home", view(), List.of(ev("SECTION_VIEW", "faq")), "B:BOT", true, false);
    recorder().record("home", view(), List.of(ev("SECTION_VIEW", "faq")), "U:1", false, true);
    recorder().record("home", view(), List.of(ev("SECTION_VIEW", "faq")), "V:human", false, false);
    var faq = stats().events().sections().stream().filter(s -> s.key().equals("faq")).findFirst().orElseThrow();
    assertThat(rows()).isEqualTo(3);
    assertThat(faq.exposures().total()).isEqualTo(1);
    assertThat(faq.visitors().total()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 섹션을 본 뒤 클릭한 방문·보지 않고 클릭한 방문 when 클릭률을 계산하면 then 본 방문만 분자에 들어간다")
  void ctrCountsOnlyViewsThatSawSection() {
    // given: 방문 A(본 뒤 클릭) · B(보기만) · C(보지 않고 클릭)
    String a = view(), b = view(), c = view();
    recorder().record("home", a, List.of(ev("SECTION_VIEW", "wiki"), ev("CLICK", "wiki_kakao")), "V:a", false, false);
    recorder().record("home", b, List.of(ev("SECTION_VIEW", "wiki")), "V:b", false, false);
    recorder().record("home", c, List.of(ev("CLICK", "wiki_kakao")), "V:c", false, false);
    // when
    AdminAnalyticsEventsResponse r = stats().events();
    var kakao = r.clicks().stream().filter(x -> x.key().equals("wiki_kakao")).findFirst().orElseThrow();
    // then: 클릭 2회(A·C)지만 클릭률 = 1/2(노출 A·B 중 클릭한 방문 A)
    assertThat(kakao.clicks().today()).isEqualTo(2);
    assertThat(kakao.ctrToday()).isEqualTo(0.5);
    assertThat(kakao.ctrTotal()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("given 섹션 노출이 없는 버튼·섹션 밖 버튼 when 클릭률을 보면 then 0으로 나누지 않고 null이다")
  void ctrIsNullWithoutExposureOrSection() {
    recorder().record("home", view(), List.of(ev("CLICK", "wiki_kakao"), ev("CLICK", "nav_contact")), "V:a", false, false);
    var r = stats().events();
    assertThat(r.clicks().stream().filter(x -> x.key().equals("wiki_kakao")).findFirst().orElseThrow().ctrToday()).isNull();
    assertThat(r.clicks().stream().filter(x -> x.key().equals("nav_contact")).findFirst().orElseThrow().ctrToday()).isNull();
  }

  @Test
  @DisplayName("given 서비스 이동·흐름 시작 이벤트 when 집계하면 then 횟수와 UV로 나뉘고 PV(site_page_visits)에는 영향이 없다")
  void actionsAreSeparateFromPageViews() {
    long pvBefore = jdbc.queryForObject("SELECT COUNT(*) FROM site_page_visits", Long.class);
    recorder().record("home", view(), List.of(ev("SERVICE_MOVE", "builder"), ev("FLOW_START", "exam_request")), "V:a", false, false);
    recorder().record("home", view(), List.of(ev("SERVICE_MOVE", "builder")), "V:a", false, false);
    var r = stats().events();
    var move = r.serviceMoves().stream().filter(x -> x.key().equals("builder")).findFirst().orElseThrow();
    var flow = r.flowStarts().stream().filter(x -> x.key().equals("exam_request")).findFirst().orElseThrow();
    assertThat(move.counts().today()).isEqualTo(2);
    assertThat(move.visitors().today()).isEqualTo(1);
    assertThat(flow.counts().today()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_page_visits", Long.class)).isEqualTo(pvBefore);
  }

  @Test
  @DisplayName("given 보관 기간(395일)이 지난 이벤트 when 정리 작업을 실행하면 then 지난 것만 삭제하고 최근 것은 남는다")
  void retentionPurge_deletesOnlyExpired() {
    recorder().record("home", view(), List.of(ev("SECTION_VIEW", "faq")), "V:a", false, false);
    jdbc.update("INSERT INTO site_events (event_date, occurred_at, event_type, target, section, page_key, view_id, seq,"
        + " visitor_key, traffic_kind, is_internal) VALUES (DATE_SUB(CURDATE(), INTERVAL 400 DAY), NOW(), 'SECTION_VIEW',"
        + " 'faq', NULL, 'home', ?, 0, 'V:old', 'HUMAN', 0)", view());
    assertThat(rows()).isEqualTo(2);
    int deleted = recorder().purgeExpired();
    assertThat(deleted).isEqualTo(1);
    assertThat(rows()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT visitor_key FROM site_events", String.class)).isEqualTo("V:a");
  }

  @Test
  @DisplayName("given 저장된 행 when 컬럼을 보면 then 개인정보·입력값·URL을 담을 컬럼이 없다")
  void schemaHasNoPersonalColumns() {
    List<String> cols = jdbc.queryForList(
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_events'",
        String.class);
    assertThat(cols).containsExactlyInAnyOrder("event_id", "event_date", "occurred_at", "event_type", "target", "section",
        "page_key", "view_id", "seq", "visitor_key", "traffic_kind", "is_internal");
  }
}
