package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** 수업 준비 › 학습지 화면 계약 — 위·왼쪽 메뉴 틀, 표 목록, 자료실, 만들기 2단계(범위·옵션 → 미리보기·편집), 보기·인쇄·배정. 화면은 빈 틀이고 자료는 API로 받는다. */
@DisplayName("학습지 화면 템플릿 계약")
class WorksheetTemplateContractTest {

  private static final String[] VIEWS =
      {"dashboard_worksheets", "dashboard_worksheet_library", "dashboard_worksheet_edit", "dashboard_worksheet_view"};

  private static String render(String view) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine.process(view, new Context());
  }

  private static String read(String path) throws IOException {
    return Files.readString(Path.of("src/main/resources/" + path), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("given 학습지 화면 3개 when 렌더 then 공통 상단 메뉴·휴대폰 viewport·공통 CSS/JS가 들어 있고 실제 파일이 있다")
  void templates_renderWithSharedResources() {
    for (String view : VIEWS) {
      // when
      String html = render(view);

      // then
      assertThat(html).contains("class=\"pp-top\"").contains("class=\"pp-rail\"").contains("width=device-width")
          .contains("/css/worksheets.css").contains("/js/api.js").contains("/js/worksheet_core.js")
          .contains("href=\"/dashboard/worksheets/library\"");
      Matcher m = Pattern.compile("(?:src|href)=\"(/(?:js|css)/[^\"?]+)").matcher(html);
      while (m.find()) {
        assertThat(Path.of("src/main/resources/static" + m.group(1))).as(view + " → " + m.group(1)).exists();
      }
    }
  }

  @Test
  @DisplayName("given 목록 화면 when 렌더 then 우리 학원·내 학습지·즐겨찾기·휴지통 구분, 타입 거르기, 표 머리, 만들기 단추가 있다")
  void list_hasTabsTableAndMakeButton() {
    // when
    String html = render("dashboard_worksheets");

    // then
    for (String tab : new String[] {"ACADEMY", "MINE", "FAVORITE", "TRASH"}) {
      assertThat(html).contains("data-tab=\"" + tab + "\"");
    }
    assertThat(html).contains("class=\"pp-table\"").contains("<th>학습지명</th>").contains("data-kind=\"내신\"")
        .contains("id=\"search\"").contains("href=\"/dashboard/worksheets/edit\"").contains("pp-rail")
        .contains("data-rail=\"worksheets\" class=\"is-active\"");
  }

  @Test
  @DisplayName("given 자료실 화면 when 렌더 then 왼쪽 묶음 목록·단원 단추·자료 표·미리보기 창이 있고 왼쪽 메뉴의 자료실이 켜져 있다")
  void library_hasSplitLayout() {
    // when
    String html = render("dashboard_worksheet_library");

    // then
    assertThat(html).contains("class=\"pp-split\"").contains("id=\"groups\"").contains("id=\"units\"")
        .contains("data-source=\"NAESIN\"").contains("data-source=\"GRAMMAR\"").contains("id=\"preview\"")
        .contains("data-rail=\"library\" class=\"is-active\"");
  }

  @Test
  @DisplayName("given 만들기 화면 when 렌더 then STEP 1은 범위 트리 + 문제 옵션, STEP 2는 시험지 미리보기 + 문제 목록·문제 추가·디자인 설정이다")
  void edit_hasTwoPaneSteps() {
    // when
    String html = render("dashboard_worksheet_edit");

    // then
    assertThat(html).contains("data-step=\"1\"").contains("data-step=\"2\"").contains("id=\"tree\"")
        .contains("id=\"remain\"").contains("id=\"count\"").contains("id=\"levels\"").contains("id=\"types\"")
        .contains("id=\"balanced\"").contains("id=\"paperBody\"").contains("data-panel=\"list\"")
        .contains("data-panel=\"add\"").contains("data-panel=\"design\"").contains("data-columns=\"1\"")
        .contains("id=\"saveBtn\"");
  }

  @Test
  @DisplayName("given 보기 화면 when 렌더 then 시험지·인쇄 단추·정답 함께·배정 판이 있고 인쇄할 때 메뉴와 배정은 숨긴다")
  void view_hasPrintAndAssign() throws IOException {
    // when
    String html = render("dashboard_worksheet_view");
    String css = read("static/css/worksheets.css");

    // then
    assertThat(html).contains("id=\"printBtn\"").contains("id=\"withAnswers\"").contains("id=\"assign\"")
        .contains("id=\"paperHead\"").contains("pp-panel pp-noprint");
    assertThat(css).contains("@media print").contains(".pp-noprint").contains("@media (max-width: 760px)");
  }

  @Test
  @DisplayName("given 화면 스크립트 when 읽기 then 학습지는 원장·관리자 공용 API, 배정은 관리자 전용 API만 부른다")
  void scripts_callExpectedApis() throws IOException {
    // given
    String list = read("static/js/worksheets.js");
    String library = read("static/js/worksheet_library.js");
    String edit = read("static/js/worksheet_edit.js");
    String view = read("static/js/worksheet_view.js");

    // when & then
    assertThat(list).contains("/api/tutor/easy/worksheets").doesNotContain("/api/admin/");
    assertThat(edit).contains("/api/tutor/easy/worksheets").doesNotContain("/api/admin/");
    assertThat(library).contains("/api/tutor/easy/worksheets").doesNotContain("/api/admin/");
    assertThat(view).contains("/api/admin/easy/worksheets").contains("redirectOnAuthError: false");
  }

  @Test
  @DisplayName("given 제작 공장 화면 when 렌더 then 같은 틀(위·왼쪽 메뉴)에 공정 6단계·작업 표·제작 요청 칸(구분·쪽수 구간·자료 위치·문항 표시)·원칙 안내가 있다")
  void factory_hasStagesTableAndRequestForm() throws IOException {
    // when
    String html = render("dashboard_factory");
    String js = read("static/js/factory.js");
    String css = read("static/css/worksheets.css");

    // then
    assertThat(html).contains("class=\"pp-top\"").contains("class=\"pp-rail\"").contains("width=device-width")
        .contains("/css/worksheets.css").contains("/js/api.js").contains("/js/worksheet_core.js").contains("/js/factory_core.js")
        .contains("href=\"/dashboard/factory\" data-menu=\"factory\" class=\"is-active\"");
    for (String stage : new String[] {"ANALYSIS", "SCOPE", "DRAFT", "REVIEW", "REVISE", "PUBLISH"}) {
      assertThat(html).contains("data-stage=\"" + stage + "\"");
    }
    assertThat(html).contains("다른 세션 3곳").contains("학생 풀이 페이지").contains("class=\"pp-table\"").contains("<th>지금 단계</th>")
        .contains("id=\"fTrack\"").contains("id=\"fRanges\"").contains("id=\"fPast\"").contains("id=\"fScope\"")
        .contains("id=\"fLabels\"").contains("id=\"fCount\"").contains("다른 회사의 지문·문항·그림은 싣지 않는다");
    Matcher m = Pattern.compile("(?:src|href)=\"(/(?:js|css)/[^\"?]+)").matcher(html);
    while (m.find()) assertThat(Path.of("src/main/resources/static" + m.group(1))).as(m.group(1)).exists();
    assertThat(js).contains("/api/admin/coord/factory").contains("redirectOnAuthError: false").doesNotContain("/api/tutor/");
    assertThat(css).contains(".pf-stages").contains("repeat(3, minmax(0, 1fr))");
  }

  @Test
  @DisplayName("given 학습지 화면들 when 렌더 then 왼쪽 메뉴에 제작 공장으로 가는 항목이 있다")
  void rail_linksToFactory() {
    for (String view : VIEWS) {
      // when & then
      assertThat(render(view)).as(view).contains("href=\"/dashboard/factory\"").contains("제작 공장");
    }
  }

  @Test
  @DisplayName("given 자료실·만들기 when 렌더 then 수능 영어·iBT·SAT·TOEIC 탭이 있고, 자료실에는 내신위키 자료 탭(시험 목록·자료 표·올리기 창)이 있다")
  void library_hasTestprepCsatAndWikiTabs() throws IOException {
    // when
    String library = render("dashboard_worksheet_library");
    String edit = render("dashboard_worksheet_edit");
    String js = read("static/js/worksheet_library.js");

    // then
    for (String source : new String[] {"NAESIN", "CSAT", "IBT", "SAT", "TOEIC", "GRAMMAR"}) {
      assertThat(library).contains("data-source=\"" + source + "\"");
      assertThat(edit).contains("data-source=\"" + source + "\"");
    }
    assertThat(library).contains("data-source=\"WIKI\"", "id=\"wikiPanel\"", "id=\"wikiContexts\"", "id=\"wikiList\"",
        "id=\"wikiUploadForm\"", "id=\"wuType\"", "id=\"wuFile\"", "/js/wiki_materials.js");
    assertThat(js).contains("WM.materials", "/material-contexts", "WM.upload");
    assertThat(read("static/js/wiki_materials.js")).contains("/problem-generator/wiki/api", "redirectOnAuthError: false");
  }

  @Test
  @DisplayName("given 제작 요청 창 when 렌더 then 기출·시험범위 칸 아래에 내신위키에서 고르기와 자료 올리기 링크가 있다")
  void factory_hasWikiPicker() throws IOException {
    // when
    String html = render("dashboard_factory");

    // then
    assertThat(html).contains("id=\"wikiPickBtn\"", "id=\"wikiPickList\"", "href=\"/dashboard/worksheets/library?tab=wiki\"",
        "/js/wiki_materials.js");
    assertThat(read("static/js/factory.js")).contains("WM.materials", "WM.ref");
  }

  @Test
  @DisplayName("given 업무 대시보드 when 읽기 then 자료·문제 제작 묶음에 학습지 링크가 있다")
  void dashboard_linksToWorksheets() throws IOException {
    // when & then
    assertThat(read("templates/dashboard.html")).contains("href=\"/dashboard/worksheets\"").contains("href=\"/dashboard/factory\"");
  }
}
