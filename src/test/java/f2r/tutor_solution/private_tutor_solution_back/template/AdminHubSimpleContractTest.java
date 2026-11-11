package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("통합 대시보드 단순화 — AI에게 부탁하고 사람은 숫자만 본다")
class AdminHubSimpleContractTest {

  private static final Path ROOT = Path.of("src/main/resources");

  private static String read(String rel) throws Exception {
    return Files.readString(ROOT.resolve(rel));
  }

  @Test
  @DisplayName("given 허브 화면 when 읽으면 then AI 부탁 → 한눈에 보기 → 자주 가는 곳 → 더 보기(접힘) 순서다")
  void hubOrderIsAskThenSummaryThenTilesThenMore() throws Exception {
    // given
    String html = read("templates/admin/hub.html");
    // when
    int ask = html.indexOf("id=\"askForm\"");
    int sum = html.indexOf("id=\"numRow\"");
    int tiles = html.indexOf("id=\"tileRow\"");
    int more = html.indexOf("<details class=\"hub-more\"");
    // then
    assertThat(ask).isPositive();
    assertThat(sum).isGreaterThan(ask);
    assertThat(tiles).isGreaterThan(sum);
    assertThat(more).isGreaterThan(tiles);
    assertThat(html).contains("AI에게 부탁하기", "이번 달 한눈에 보기", "자주 가는 곳");
    assertThat(html).doesNotContain("<details class=\"hub-more\" open");
  }

  @Test
  @DisplayName("given 허브 화면 when 읽으면 then 기존 링크(학생명단·시험 접수·예상문제 의뢰)는 그대로 남는다")
  void hubKeepsExistingLinks() throws Exception {
    // given
    String html = read("templates/admin/hub.html");
    // when / then
    assertThat(html).contains("hub-roster-link", "href=\"/admin/student-roster\"", "href=\"/admin/hub/exam-bank\"",
        "href=\"/admin/hub/exam-requests\"", "https://open.kakao.com/o/sMf4ifRi", "href=\"/easy/attendance\"",
        "href=\"/easy/connections\"", "href=\"/ledger\"", "href=\"/admissions/workspace\"");
    assertThat(html).contains("/js/hub_view.js", "/js/hub_dashboard.js");
  }

  @Test
  @DisplayName("given AI 업무 비서 when 읽으면 then ?ask= 글은 입력칸에만 채우고 자동 전송하지 않는다")
  void assistantPrefillsAskWithoutSending() throws Exception {
    // given
    String js = read("static/js/unified_assistant.js");
    String dash = read("templates/dashboard_assistant.html");
    // when
    int fn = js.indexOf("function prefillAsk(){");
    String body = js.substring(fn, js.indexOf('\n', fn));
    // then
    assertThat(dash).contains("/js/hub_view.js");
    assertThat(body).contains("askFromSearch(location.search)", "$('uaCommand').value=ask");
    assertThat(body).doesNotContain("requestSubmit", "send(");
  }

  @Test
  @DisplayName("given 상단 메뉴 when 읽으면 then 자주 쓰는 5개만 바로 보이고 나머지는 「전체 메뉴」 안에 묶인다")
  void topnavShowsFiveMainLinksAndGroupsTheRest() throws Exception {
    // given
    String nav = read("templates/fragments/_admin_topnav.html");
    // when
    int more = nav.indexOf("<details class=\"admin-nav-more\"");
    String visible = nav.substring(0, more);
    // then
    assertThat(more).isPositive();
    assertThat(visible.split("admin-nav-main", -1).length - 1).isEqualTo(5);
    assertThat(visible).contains("href=\"/admin/hub\"", "href=\"/dashboard/assistant\"", "href=\"/easy\"",
        "href=\"/easy/attendance\"", "href=\"/admin/student-roster\"");
    assertThat(nav.substring(more)).contains("학원 운영", "장부·출결", "콘텐츠·제작", "새출발", "관리");
  }
}
