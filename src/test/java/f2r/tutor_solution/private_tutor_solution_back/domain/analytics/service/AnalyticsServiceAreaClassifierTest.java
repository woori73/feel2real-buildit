package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnalyticsServiceAreaClassifierTest {

  @ParameterizedTest
  @CsvSource({
      "'/', HOME",
      "/home, HOME",
      "/chart, CHART",
      "/chart/educators/7, CHART",
      "/academies, WEB",
      "/academies/edu-holic, WEB",
      "/academy-posts, WEB",
      "/subdomain-home, WEB",
      "/builder, ACADEMY",
      "/tutor/academy/setup, ACADEMY",
      "/lms/edu-holic/courses/3, ACADEMY",
      "/wiki, WIKI",
      "/wiki/questions/31, WIKI",
      "/community, OTHER"
  })
  void classifiesActualRoutes(String path, AnalyticsServiceArea expected) {
    assertThat(AnalyticsServiceAreaClassifier.classify(path)).isEqualTo(expected);
  }
}
