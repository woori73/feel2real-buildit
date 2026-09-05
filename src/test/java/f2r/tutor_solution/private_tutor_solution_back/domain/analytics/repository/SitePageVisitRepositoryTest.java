package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:analyticsverify;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class SitePageVisitRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 5);

  @Autowired SitePageVisitRepository repository;

  @Test
  void humanUvUsesDistinctVisitorKey_pvCountsRows_botIsSeparated_andLegacySessionWorks() {
    save("V:0f12a5be-7d4c-4b36-9e20-38b18d143be1", AnalyticsServiceArea.HOME,
        AnalyticsTrafficKind.HUMAN, "/home");
    save("V:0f12a5be-7d4c-4b36-9e20-38b18d143be1", AnalyticsServiceArea.HOME,
        AnalyticsTrafficKind.HUMAN, "/home");
    save("V:fa3c6fa1-a927-4635-bf6f-e18a118af931", AnalyticsServiceArea.HOME,
        AnalyticsTrafficKind.HUMAN, "/home");
    save("S:legacy-session", AnalyticsServiceArea.HOME, AnalyticsTrafficKind.HUMAN, "/home");
    save("B:BOT", AnalyticsServiceArea.HOME, AnalyticsTrafficKind.BOT, "/home");
    save("V:wiki-visitor", AnalyticsServiceArea.WIKI, AnalyticsTrafficKind.HUMAN, "/wiki");

    assertThat(repository.countDistinctVisitorsByArea(AnalyticsServiceArea.HOME, DAY, DAY)).isEqualTo(3);
    assertThat(repository.countPageViewsByArea(AnalyticsServiceArea.HOME, DAY, DAY)).isEqualTo(4);
    assertThat(repository.countBotRequests(DAY, DAY)).isEqualTo(1);
    assertThat(repository.countDistinctWikiVisitors(DAY, DAY)).isEqualTo(1);
    assertThat(repository.countWikiPageViews(DAY, DAY)).isEqualTo(1);
  }

  private void save(String visitorKey, AnalyticsServiceArea area,
      AnalyticsTrafficKind kind, String path) {
    boolean wiki = area == AnalyticsServiceArea.WIKI;
    repository.saveAndFlush(SitePageVisit.of(
        DAY, LocalDateTime.of(2026, 9, 5, 13, 0), wiki, path,
        null, "GUEST", "비회원", visitorKey, area, kind));
  }
}
