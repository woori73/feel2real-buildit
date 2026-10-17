package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.dto.AdminAnalyticsBreakdownResponse.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.SitePageVisitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 서비스·페이지별 UV/PV 집계를 실제 MySQL(site_page_visits)에서 검증한다. 기존 데이터가 있어도 증가분(delta)으로 비교한다. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("AdminAnalyticsBreakdownService MySQL — UV 중복 제거·Bot 제외·기간·서비스 분류")
class AdminAnalyticsBreakdownMySqlTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private SitePageVisitRepository visits;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private AdminAnalyticsBreakdownService service() {
    return new AdminAnalyticsBreakdownService(jdbc, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  private void visit(int daysAgo, String path, String key, AnalyticsTrafficKind kind) {
    LocalDate d = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(daysAgo);
    boolean guest = key.startsWith("V:") || key.startsWith("B:");
    visits.save(SitePageVisit.of(d, LocalDateTime.of(d, java.time.LocalTime.NOON), false, path,
        guest ? null : 1L, guest ? "GUEST" : "ADMIN", guest ? "비회원" : "관리자", key, AnalyticsServiceArea.OTHER, kind));
  }

  private static Row row(List<Row> rows, String key) {
    return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("given 같은 방문자의 반복 방문·Bot·관리자 화면·옛 방문 when 집계하면 then UV는 중복 제거, PV는 전부, Bot·관리자 화면은 제외, 기간별로 나뉜다")
  void breakdownCountsAsDefined() {
    // given
    AdminAnalyticsBreakdownResponse before = service().breakdown(null);
    visit(0, "/easy", "U:1", AnalyticsTrafficKind.HUMAN);
    visit(0, "/easy", "U:1", AnalyticsTrafficKind.HUMAN);       // 같은 사람 새로고침: PV만 +1
    visit(0, "/easy", "V:aaa", AnalyticsTrafficKind.HUMAN);
    visit(0, "/builder", "V:aaa", AnalyticsTrafficKind.HUMAN);  // 한 방문자가 두 서비스 사용
    visit(0, "/easy", "B:BOT", AnalyticsTrafficKind.BOT);       // Bot: 제외
    visit(0, "/admin/analytics", "U:1", AnalyticsTrafficKind.HUMAN); // 관리자 화면: 제외
    visit(10, "/testprep", "V:bbb", AnalyticsTrafficKind.HUMAN); // 오늘·7일 밖, 30일 안
    visit(40, "/testprep", "V:ccc", AnalyticsTrafficKind.HUMAN); // 누적에만
    em.flush();
    // when
    AdminAnalyticsBreakdownResponse after = service().breakdown(null);
    // then
    Row e0 = row(before.services(), "EASY"), e1 = row(after.services(), "EASY");
    assertThat(e1.views().today() - e0.views().today()).isEqualTo(3);
    assertThat(e1.visitors().today() - e0.visitors().today()).isEqualTo(2);
    Row b0 = row(before.services(), "BUILDIT"), b1 = row(after.services(), "BUILDIT");
    assertThat(b1.visitors().today() - b0.visitors().today()).isEqualTo(1);
    Row t0 = row(before.services(), "TESTPREP"), t1 = row(after.services(), "TESTPREP");
    assertThat(t1.views().today() - t0.views().today()).isZero();
    assertThat(t1.views().recent7() - t0.views().recent7()).isZero();
    assertThat(t1.views().recent30() - t0.views().recent30()).isEqualTo(1);
    assertThat(t1.views().total() - t0.views().total()).isEqualTo(2);
    assertThat(after.services()).extracting(Row::key).doesNotContain("ADMIN_CONSOLE");
    assertThat(after.topPaths()).extracting(p -> p.path()).doesNotContain("/admin/analytics");
  }

  @Test
  @DisplayName("given 여러 서비스의 경로 when 집계하면 then SQL 분류가 Java 분류표와 같은 서비스·페이지 그룹을 만든다")
  void sqlAndJavaClassificationAgree() {
    // given
    String[] paths = {"/", "/home", "/easy", "/easy/x", "/ledger", "/wemade", "/wemade/china/news/1", "/choonggi", "/choonggi/vlog/a",
        "/builder", "/academies/a", "/problem-generator", "/problem-generator/wiki/index.html", "/testprep", "/testprep/run",
        "/lms/a", "/exam-analysis", "/designcraft", "/chart", "/wiki/x", "/admissions/guide", "/newstart/consultation",
        "/admin/members", "/login", "/easyx"};
    for (String p : paths) visit(0, p, "V:parity", AnalyticsTrafficKind.HUMAN);
    em.flush();
    // when
    var rows = jdbc.queryForList("SELECT DISTINCT path, " + AnalyticsCatalog.serviceCaseSql("path") + " s, "
        + AnalyticsCatalog.pageGroupCaseSql("path") + " g FROM site_page_visits WHERE visitor_key = 'V:parity'");
    // then
    assertThat(rows).hasSize(paths.length);
    for (var r : rows) {
      String path = (String) r.get("path");
      assertThat(r.get("s")).as("service of " + path).isEqualTo(AnalyticsCatalog.serviceOf(path).name());
      assertThat(r.get("g")).as("page group of " + path).isEqualTo(AnalyticsCatalog.pageGroupOf(path).name());
    }
  }

  @Test
  @DisplayName("given 서비스를 고른 추세 when 조회하면 then 30일치가 빈 날 0으로 채워지고 선택 서비스만 센다")
  void trendIsFilledAndFiltered() {
    // given
    visit(2, "/wemade", "V:w1", AnalyticsTrafficKind.HUMAN);
    visit(2, "/easy", "V:e1", AnalyticsTrafficKind.HUMAN);
    em.flush();
    // when
    var trend = service().breakdown("WEMADE");
    // then
    assertThat(trend.trend()).hasSize(30);
    assertThat(trend.trendService()).isEqualTo("WEMADE");
    long twoDaysAgo = trend.trend().get(27).views();
    assertThat(twoDaysAgo).isGreaterThanOrEqualTo(1);
    assertThat(service().breakdown("ADMIN_CONSOLE").trendService()).isNull();
  }

  @Test
  @DisplayName("given 전환 지표 when 조회하면 then 접수된 상담신청만 담고 이벤트 지표는 별도 API로 분리된다")
  void conversionsContainOnlyInquiries() {
    // given / when
    var conv = service().breakdown(null).conversions();
    // then
    assertThat(conv).extracting(c -> c.key()).containsExactly("ACADEMY_INQUIRY");
    assertThat(conv).allSatisfy(c -> assertThat(c.collected()).isTrue());
  }
}
