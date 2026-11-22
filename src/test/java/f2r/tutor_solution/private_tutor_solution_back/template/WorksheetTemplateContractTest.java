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

/** 수업 준비 › 학습지 화면 계약 — 목록 구분 5개, 만들기 3단계, 보기·인쇄·배정. 화면은 빈 틀이고 자료는 API로 받는다. */
@DisplayName("학습지 화면 템플릿 계약")
class WorksheetTemplateContractTest {

  private static final String[] VIEWS =
      {"dashboard_worksheets", "dashboard_worksheet_edit", "dashboard_worksheet_view"};

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
      assertThat(html).contains("admin-nav").contains("width=device-width").contains("/css/easy.css")
          .contains("/css/worksheets.css").contains("/js/api.js").contains("/js/worksheet_core.js");
      Matcher m = Pattern.compile("(?:src|href)=\"(/(?:js|css)/[^\"?]+)").matcher(html);
      while (m.find()) {
        assertThat(Path.of("src/main/resources/static" + m.group(1))).as(view + " → " + m.group(1)).exists();
      }
    }
  }

  @Test
  @DisplayName("given 목록 화면 when 렌더 then 우리 학원·내 학습지·즐겨찾기·F2R 기본 자료·휴지통 구분과 새로 만들기 링크가 있다")
  void list_hasFiveTabs() {
    // when
    String html = render("dashboard_worksheets");

    // then
    for (String tab : new String[] {"ACADEMY", "MINE", "FAVORITE", "BANK", "TRASH"}) {
      assertThat(html).contains("data-tab=\"" + tab + "\"");
    }
    assertThat(html).contains("href=\"/dashboard/worksheets/edit\"");
  }

  @Test
  @DisplayName("given 만들기 화면 when 렌더 then 범위 고르기 → 문항 편집 → 저장 3단계와 남은 문항 수 표시가 있다")
  void edit_hasThreeSteps() {
    // when
    String html = render("dashboard_worksheet_edit");

    // then
    assertThat(html).contains("data-step=\"1\"").contains("data-step=\"2\"").contains("data-step=\"3\"")
        .contains("id=\"remain\"").contains("id=\"source\"").contains("id=\"sets\"").contains("id=\"types\"")
        .contains("id=\"levels\"").contains("id=\"count\"").contains("id=\"saveBtn\"");
  }

  @Test
  @DisplayName("given 보기 화면 when 렌더 then 인쇄 단추·정답 함께 보기·배정 영역이 있고 인쇄할 때 메뉴와 배정은 숨긴다")
  void view_hasPrintAndAssign() throws IOException {
    // when
    String html = render("dashboard_worksheet_view");
    String css = read("static/css/worksheets.css");

    // then
    assertThat(html).contains("id=\"printBtn\"").contains("id=\"withAnswers\"").contains("id=\"assign\"")
        .contains("class=\"easy-card ws-noprint\"");
    assertThat(css).contains("@media print").contains(".ws-noprint");
  }

  @Test
  @DisplayName("given 화면 스크립트 when 읽기 then 학습지는 원장·관리자 공용 API, 배정은 관리자 전용 API만 부른다")
  void scripts_callExpectedApis() throws IOException {
    // given
    String list = read("static/js/worksheets.js");
    String edit = read("static/js/worksheet_edit.js");
    String view = read("static/js/worksheet_view.js");

    // when & then
    assertThat(list).contains("/api/tutor/easy/worksheets").doesNotContain("/api/admin/");
    assertThat(edit).contains("/api/tutor/easy/worksheets").doesNotContain("/api/admin/");
    assertThat(view).contains("/api/admin/easy/worksheets").contains("redirectOnAuthError: false");
  }

  @Test
  @DisplayName("given 업무 대시보드 when 읽기 then 자료·문제 제작 묶음에 학습지 링크가 있다")
  void dashboard_linksToWorksheets() throws IOException {
    // when & then
    assertThat(read("templates/dashboard.html")).contains("href=\"/dashboard/worksheets\"");
  }
}
