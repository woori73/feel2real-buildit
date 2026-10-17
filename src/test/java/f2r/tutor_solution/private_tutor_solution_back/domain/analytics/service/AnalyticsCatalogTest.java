package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsCatalog.PageGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsCatalog.Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AnalyticsCatalog — 경로 → 서비스·페이지 그룹 분류")
class AnalyticsCatalogTest {

  @ParameterizedTest(name = "{0} → {1} / {2}")
  @CsvSource({
      "/,HOME,HOME_MAIN",
      "/home,HOME,HOME_MAIN",
      "/easy,EASY,SERVICE_INTRO",
      "/easy/ledger,EASY,SERVICE_DETAIL",
      "/ledger,EASY,SERVICE_INTRO",
      "/tutor/easy/x,EASY,SERVICE_DETAIL",
      "/wemade,WEMADE,SERVICE_INTRO",
      "/wemade/china/news/3,WEMADE,SERVICE_DETAIL",
      "/choonggi,CHOONGGI,SERVICE_INTRO",
      "/choonggi/vlog/hello,CHOONGGI,SERVICE_DETAIL",
      "/builder,BUILDIT,SERVICE_INTRO",
      "/academies/my-academy,BUILDIT,SERVICE_DETAIL",
      "/tutor/academy/inquiries,BUILDIT,SERVICE_DETAIL",
      "/problem-generator,NAESIN_WIKI,EXAM_MAKING",
      "/problem-generator/wiki/index.html,NAESIN_WIKI,EXAM_MAKING",
      "/testprep,TESTPREP,SERVICE_INTRO",
      "/testprep/run,TESTPREP,SERVICE_DETAIL",
      "/lms/my-academy,ACADEMYCRAFT,SERVICE_DETAIL",
      "/exam-analysis,OTHER_SERVICE,EXAM_MAKING",
      "/designcraft,OTHER_SERVICE,SERVICE_INTRO",
      "/chart,OTHER_SERVICE,SERVICE_INTRO",
      "/wiki/exam,OTHER_SERVICE,SERVICE_DETAIL",
      "/admissions/guide,OTHER_SERVICE,CONSULT",
      "/newstart/consultation,OTHER_SERVICE,CONSULT",
      "/admin/analytics,ADMIN_CONSOLE,OTHER",
      "/login,UNCLASSIFIED,OTHER",
      "/easyx,UNCLASSIFIED,OTHER"
  })
  @DisplayName("given 방문 경로 when 분류하면 then 서비스와 페이지 그룹이 정해진다")
  void classifies(String path, Service service, PageGroup group) {
    // given / when / then
    assertThat(AnalyticsCatalog.serviceOf(path)).isEqualTo(service);
    assertThat(AnalyticsCatalog.pageGroupOf(path)).isEqualTo(group);
  }

  @Test
  @DisplayName("given null·빈 경로 when 분류하면 then 미분류·기타")
  void handlesBlank() {
    // given / when / then
    assertThat(AnalyticsCatalog.serviceOf(null)).isEqualTo(Service.UNCLASSIFIED);
    assertThat(AnalyticsCatalog.pageGroupOf(" ")).isEqualTo(PageGroup.OTHER);
  }

  @Test
  @DisplayName("given 분류표 when SQL CASE를 만들면 then 모든 서비스 접두와 상수만 들어 있고 사용자 입력 자리는 없다")
  void sqlCaseUsesOnlyConstants() {
    // given / when
    String sql = AnalyticsCatalog.serviceCaseSql("path");
    // then
    assertThat(sql).contains("'/easy/%'", "'/problem-generator/%'", "'/lms/%'", "'/wemade/%'").doesNotContain("?");
    assertThat(AnalyticsCatalog.pageGroupCaseSql("path")).contains("'/newstart/consult%'").doesNotContain("?");
  }

  @Test
  @DisplayName("given 가격 안내 전용 페이지 when 목록을 보면 then 전용 경로가 없는 항목으로 명시된다")
  void pricingHasNoRoute() {
    // given / when / then
    assertThat(AnalyticsCatalog.PAGES_WITHOUT_ROUTE).contains("가격 및 상품 안내");
  }
}
