package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AcademyVisitStatisticsContractTest {

  @Test
  void publicAcademyPage_recordsAndRendersVisitStatistics() throws Exception {
    // given / when
    String script = Files.readString(Path.of("src/main/resources/static/js/academy_public.js"));
    String css = Files.readString(Path.of("src/main/resources/static/css/academy_public.css"));

    // then
    assertThat(script).contains(
        "renderVisitStatistics",
        "/visits",
        "method: shouldRecord ? 'POST' : 'GET'",
        "class=\"counter\"",
        "오늘 <span id=\"visitToday\">-");
    String footerCss = Files.readString(Path.of("src/main/resources/static/css/academy_business_footer.css"));
    assertThat(css).doesNotContain("visit-statistics-floating", "visit-stat-chart");
    assertThat(footerCss).contains(
        ".academy-business-footer .counter",
        ".academy-business-footer .counter .d");
  }

  @Test
  void adminAcademyPage_rendersEveryAcademyStatistics() throws Exception {
    // given / when
    String template = Files.readString(Path.of("src/main/resources/templates/admin/academies.html"));

    // then
    assertThat(template).contains(
        "academy.visitStatistics",
        "renderVisitChart",
        "오늘 방문",
        "누적 방문");
  }

  @Test
  void anonymousVisitors_canRecordPublicAcademyVisit() throws Exception {
    // given / when
    String security = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/config/SecurityConfig.java"));

    // then
    assertThat(security).contains(
        ".requestMatchers(HttpMethod.POST, \"/api/academies/*/visits\").permitAll()");
  }
}
