package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Action;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Section;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Tab;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** 학생 풀이 첫 화면(/learn) 화면 계약 — 공개 layout, 과목·시험 탭, 풀기 단추의 값, 준비 중 안내, 홈 메뉴 링크. */
@DisplayName("학생 풀이 첫 화면 템플릿 계약")
class LearnHubTemplateContractTest {

  private static String render(String activeTab) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    Context context = new Context();
    context.setVariable("tabs", List.of(
        new Tab("naesin", "내신", "내신 설명", LearnHubCatalog.NAESIN_ACCESS, true, List.of(
            new Section("apgujeong-m2", "압구정중학교 2학년", "시험 범위 Lesson 7·8", "압구정중학교", "중2", false, List.of(
                new Row("1회 · Lesson 7 예상문제 1회", "Lesson 7 학습지", "40문항 · 20분 · 134점", null,
                    List.of(new Action("풀기", "NAESIN", "TP-NAESIN-APM2-2602-R01", null, null, null)), List.of())), "준비 중"))),
        new Tab("csat", "수능", "수능 설명", LearnHubCatalog.PREHIGH_ACCESS, true, List.of(
            new Section("csat-mock", "디지털 영어 모의고사", null, null, null, false, List.of(
                new Row("2부 · 실전 독해", "예비고1", "2회", null, List.of(), List.of(
                    new Action("1회", "LINK", null, "/naesintest/pre-high/part2/1", null, null),
                    new Action("2회", "LINK", null, "/naesintest/pre-high/part2/2", null, null)))), "준비 중"))),
        new Tab("toeic", "TOEIC", "TOEIC 설명", LearnHubCatalog.LOGIN, true, List.of(
            new Section("toeic-focus", "Part 5·6 어휘·어법 집중", "설명", null, null, true, List.of(
                new Row("기출 유사 1회", null, "46문항", "집중",
                    List.of(new Action("Part 5 · 30문항", "TP", "TP-TOEIC-FULL-01", null, "TOEIC_P5", "문법-품사"),
                        new Action("176~200번 · 25문항", "TP", "TP-TOEIC-RC-01", null, "TOEIC_P7", null, 176, 200)), List.of())), "준비 중"))),
        new Tab("korean", "국어", "국어 설명", LearnHubCatalog.NAESIN_ACCESS, true, List.of(
            new Section("korean-sets", "국어 디지털 모의고사", null, null, null, false, List.of(), LearnHubCatalog.SUBJECT_EMPTY)))));
    context.setVariable("activeTab", activeTab);
    context.setVariable("grades", List.of("중1", "중2"));
    context.setVariable("schools", List.of("압구정중학교"));
    return engine.process("public/learn", context);
  }

  private static String read(String path) throws IOException {
    return Files.readString(Path.of("src/main/resources/" + path), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("given 탭 4개 when 내신 탭으로 렌더 then 탭마다 주소가 있고 고른 탭만 펼쳐지며 자료 없는 탭에는 준비 중 표시가 붙는다")
  void tabs_renderWithActivePanel() {
    // when
    String html = render("naesin");

    // then
    assertThat(html).contains("href=\"/learn?tab=toeic\"").contains("data-tab=\"naesin\" aria-selected=\"true\"")
        .contains("data-tab=\"toeic\" aria-selected=\"false\"").contains("<span>국어</span><em>준비 중</em>");
    assertThat(html).containsPattern("data-panel=\"naesin\"[^>]*>").doesNotContainPattern("data-panel=\"naesin\"[^>]*hidden");
    assertThat(html).containsPattern("data-panel=\"toeic\"[^>]*hidden").containsPattern("data-panel=\"korean\"[^>]*hidden");
    assertThat(html).contains(LearnHubCatalog.SUBJECT_EMPTY).contains("data-active=\"naesin\"");
  }

  @Test
  @DisplayName("given 시험 줄 when 렌더 then 풀기 단추에 풀이 화면 종류·시험 코드·TOEIC 파트·유형 값이 실리고 회차는 주소 링크로 나온다")
  void rows_renderStartButtonsAndRoundLinks() {
    // when
    String html = render("toeic");

    // then
    assertThat(html).contains("data-run=\"NAESIN\" data-exam=\"TP-NAESIN-APM2-2602-R01\"")
        .contains("data-run=\"TP\" data-exam=\"TP-TOEIC-FULL-01\" data-parts=\"TOEIC_P5\" data-tag=\"문법-품사\"")
        .contains("data-run=\"TP\" data-exam=\"TP-TOEIC-RC-01\" data-parts=\"TOEIC_P7\" data-from=\"176\" data-to=\"200\"")
        .contains("href=\"/naesintest/pre-high/part2/2\"").contains("회차 고르기")
        .contains("40문항 · 20분 · 134점").contains("lh-sec is-focus").contains("<span class=\"lh-badge\">집중</span>");
    assertThat(html).containsPattern("data-panel=\"naesin\"[^>]*hidden");
  }

  @Test
  @DisplayName("given 화면 when 렌더 then 공개 layout(방문 분석)·휴대폰 viewport·대표 주소가 있고 학년·학교 거르기는 내신 탭 안에만 있다")
  void page_hasLayoutAndNaesinOnlyFilters() throws IOException {
    // when
    String html = render("naesin");
    String template = read("templates/public/learn.html");

    // then
    assertThat(template).contains("layout:decorate=\"~{layouts/public").contains("layout:fragment=\"content\"");
    assertThat(html).contains("GTM-MMJ4MGNN").contains("width=device-width").contains("https://feel2real.co.kr/learn")
        .contains("data-grade=\"중2\"").contains("data-school=\"압구정중학교\"").contains("lh-access is-login");
    assertThat(html.split("class=\"lh-filters\"", -1)).hasSize(2);
    int filters = html.indexOf("class=\"lh-filters\"");
    assertThat(filters).isGreaterThan(html.indexOf("data-panel=\"naesin\"")).isLessThan(html.indexOf("data-panel=\"csat\""));
    assertThat(html.split("id=\"lhToeicWrong\"", -1)).hasSize(2);
    assertThat(Path.of("src/main/resources/static/css/learn_hub.css")).exists();
    assertThat(Path.of("src/main/resources/static/js/learn_hub.js")).exists();
  }

  @Test
  @DisplayName("given 홈 화면 when 읽기 then 상단 학습 콘텐츠 메뉴와 전체 메뉴에 문제 풀기 링크가 한 줄씩만 있다")
  void landing_linksOncePerMenu() throws IOException {
    // when
    String landing = read("templates/public/landing.html");

    // then
    assertThat(landing.split("href=\"/learn\"", -1)).hasSize(3);
    assertThat(landing).contains("<a href=\"/learn\">문제 풀기</a>").contains("<a href=\"/learn\"><span>문제 풀기</span></a>");
  }
}
