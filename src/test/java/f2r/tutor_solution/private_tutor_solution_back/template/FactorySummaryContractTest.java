package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 제작 공장·학습지·학생 문제 풀기가 업무 대시보드·통합 대시보드·관리자 메뉴에 보인다(대표 지시 2026-11-23). */
@DisplayName("제작 공장 요약 카드 — 업무 대시보드·통합 대시보드·관리자 메뉴 노출")
class FactorySummaryContractTest {

  private static String read(String path) throws IOException {
    return Files.readString(Path.of("src/main/resources/" + path), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("given 요약 조각 when 읽기 then 제작 공장·학습지·학생 문제 풀기 세 카드와 숫자 칸, 원장이 아닐 때의 안내가 있고 CSS·JS 파일이 있다")
  void fragment_hasThreeCards() throws IOException {
    // given & when
    String frag = read("templates/fragments/_factory_summary.html");

    // then
    assertThat(frag).contains("href=\"/dashboard/factory\"", "href=\"/dashboard/worksheets\"", "href=\"/learn\"")
        .contains("data-factory-summary", "data-fs=\"working\"", "data-fs=\"approve\"", "data-fs=\"published\"",
            "data-fs-fallback", "data-fs-alert");
    assertThat(Path.of("src/main/resources/static/css/factory_summary.css")).exists();
    assertThat(read("static/js/factory_summary.js")).contains("/api/admin/coord/factory").contains("redirectOnAuthError: false");
  }

  @Test
  @DisplayName("given 업무 대시보드 when 읽기 then 요약 카드가 학부모 카카오톡 바로 아래에 있고 위 메뉴에 문제 제작·학습지가 있다")
  void dashboard_showsSummaryNearTop() throws IOException {
    // given & when
    String html = read("templates/dashboard.html");

    // then
    assertThat(html).contains("~{fragments/_factory_summary :: head}");
    int notice = html.indexOf("class=\"ah-parent-notice\"");
    int cards = html.indexOf("~{fragments/_factory_summary :: cards}");
    int students = html.indexOf("id=\"ahStudentsPanel\"");
    assertThat(cards).isGreaterThan(notice).isLessThan(students);
    String nav = html.substring(html.indexOf("<nav class=\"ah-nav\""), html.indexOf("</nav>", html.indexOf("<nav class=\"ah-nav\"")));
    assertThat(nav).contains("href=\"/dashboard/factory\">문제 제작<", "href=\"/dashboard/worksheets\">학습지<");
  }

  @Test
  @DisplayName("given 통합 대시보드 when 읽기 then 요약 카드가 이번 달 요약과 자주 가는 곳 사이에 있고, 자주 가는 곳 타일 9개에 제작 공장·학습지·학생 문제 풀기가 있다")
  void hub_showsSummary() throws IOException {
    // given & when
    String html = read("templates/admin/hub.html");

    // then
    assertThat(html).contains("~{fragments/_factory_summary :: head}");
    int cards = html.indexOf("~{fragments/_factory_summary :: cards}");
    assertThat(cards).isGreaterThan(html.indexOf("id=\"sumTitle\"")).isLessThan(html.indexOf("id=\"tileRow\""));
    String tiles = html.substring(html.indexOf("id=\"tileRow\""), html.indexOf("<details class=\"hub-more\""));
    assertThat(tiles).contains("href=\"/dashboard/factory\"", "href=\"/dashboard/worksheets\"", "href=\"/learn\"");
    // 기존 6칸 + 제작 공장·학습지·학생 문제 풀기 3칸 + 결제선생 바로가기 1칸(2026-11-23)
    assertThat(tiles.split("class=\"hub-tile\"", -1).length - 1).isEqualTo(10);
  }

  @Test
  @DisplayName("given 관리자 위 메뉴 when 읽기 then 전체 메뉴 「콘텐츠·제작」에 제작 공장·학습지·학생 문제 풀기가 있다")
  void adminTopnav_listsFactory() throws IOException {
    // given & when
    String nav = read("templates/fragments/_admin_topnav.html");

    // then
    String group = nav.substring(nav.indexOf("콘텐츠·제작"), nav.indexOf("새출발"));
    assertThat(group).contains("href=\"/dashboard/factory\"", "href=\"/dashboard/worksheets\"", "href=\"/learn\"");
  }
}
