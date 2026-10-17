package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.AnalyticsEventCatalog.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnalyticsEventCatalog — 허용 목록과 실제 홈페이지 마크업의 일치")
class AnalyticsEventCatalogTest {

  private static String landing() throws Exception {
    return Files.readString(Path.of("src/main/resources/templates/public/landing.html"));
  }

  private static Set<String> attrValues(String html, String attr) {
    Set<String> out = new HashSet<>();
    Matcher m = Pattern.compile(attr + "=\"([^\"]+)\"").matcher(html);
    while (m.find()) out.add(m.group(1));
    return out;
  }

  @Test
  @DisplayName("given 카탈로그의 홈 섹션 when 랜딩 마크업과 비교하면 then 모두 실제 섹션(id·data-track-section)으로 존재한다")
  void homeSections_existInLanding() throws Exception {
    // given
    String html = landing();
    // when / then
    for (var def : AnalyticsEventCatalog.all(Type.SECTION_VIEW)) {
      if (!def.page().equals("home")) continue;
      assertThat(html).as("section " + def.target()).contains("data-track-section=\"" + def.target() + "\"");
      assertThat(html).as("id of " + def.target()).contains("id=\"" + def.target() + "\"");
    }
  }

  @Test
  @DisplayName("given 랜딩 마크업의 data-track-* 값 when 카탈로그와 비교하면 then 전부 허용 목록에 있다(카탈로그에 없는 값은 서버가 버린다)")
  void landingTrackAttributes_areAllowListed() throws Exception {
    // given
    String html = landing();
    // then
    for (String id : attrValues(html, "data-track-section")) {
      assertThat(AnalyticsEventCatalog.find("SECTION_VIEW", "home", id)).as("section " + id).isPresent();
    }
    for (String t : attrValues(html, "data-track-click")) {
      assertThat(AnalyticsEventCatalog.find("CLICK", "home", t)).as("click " + t).isPresent();
    }
    for (String t : attrValues(html, "data-track-move")) {
      assertThat(AnalyticsEventCatalog.find("SERVICE_MOVE", "home", t)).as("move " + t).isPresent();
    }
    for (String t : attrValues(html, "data-track-flow")) {
      assertThat(AnalyticsEventCatalog.find("FLOW_START", "home", t)).as("flow " + t).isPresent();
    }
  }

  @Test
  @DisplayName("given 허용 목록에 없는 이름·페이지·대상 when 조회하면 then 빈 값이다")
  void unknownEvents_rejected() {
    assertThat(AnalyticsEventCatalog.find("DROP_TABLE", "home", "wiki")).isEmpty();
    assertThat(AnalyticsEventCatalog.find("SECTION_VIEW", "evil", "wiki")).isEmpty();
    assertThat(AnalyticsEventCatalog.find("SECTION_VIEW", "home", "../../etc/passwd")).isEmpty();
    assertThat(AnalyticsEventCatalog.find(null, "home", "wiki")).isEmpty();
    assertThat(AnalyticsEventCatalog.find("CLICK", "academy_public", "nav_contact")).isEmpty();
  }

  @Test
  @DisplayName("given 클릭 정의 when 소속 섹션을 보면 then 섹션이 있으면 같은 페이지의 실제 섹션 정의여야 한다")
  void clickSections_areDefined() {
    for (var click : AnalyticsEventCatalog.all(Type.CLICK)) {
      if (click.section() == null) continue;
      assertThat(AnalyticsEventCatalog.find("SECTION_VIEW", click.page(), click.section()))
          .as("section of click " + click.target()).isPresent();
    }
  }

  @Test
  @DisplayName("given 학원 공개 페이지 when 마크업과 스크립트를 보면 then 추적 설정과 상담 시작 연결이 있다")
  void academyPublic_wired() throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/public/academy_public.html"));
    String js = Files.readString(Path.of("src/main/resources/static/js/academy_public.js"));
    assertThat(html).contains("data-track-page=\"academy_public\"").contains("/js/site_events.js");
    assertThat(js).contains("F2RSiteEvents.flow('consult_academy')");
    assertThat(AnalyticsEventCatalog.find("FLOW_START", "academy_public", "consult_academy")).isPresent();
  }
}
