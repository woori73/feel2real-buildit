package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/** 예비고1 6부(고2 실전 독해 모의고사) 목록·응시 화면 템플릿. */
class PreHighPart6TemplateTest {
  private Document list(String part, boolean admin) {
    Map<String, Object> variables = new HashMap<>(Map.of(
        "rounds", List.of(Map.of("number", 1, "words", 15)),
        "grade2Rounds", List.of(Map.of("number", 1, "words", 15)),
        "part2Rounds", List.of(Map.of("number", 1, "source", "고1 모의고사")),
        "part6Rounds", List.of(Map.of("number", 1, "source", "내부 기록 변형")),
        "loggedIn", true,
        "grammarChapters", List.of(Map.of("number", 3, "title", "수동태")),
        "grammarFinalRounds", List.of(1),
        "vocabularyRoundCount", 50,
        "gradeTwoVocabularyRoundCount", 40,
        "canInstall", admin));
    return render("prehigh/list", part, variables);
  }

  private Document test(int part) {
    return render("prehigh/test", null, Map.of("round", 1, "part", part, "api", "/api/naesintest/pre-high/part" + part + "/1",
        "numbers", List.of(18, 19), "shared", List.of(false, true)));
  }

  private Document render(String template, String part, Map<String, Object> variables) {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);

    var request = new MockHttpServletRequest("GET", "/naesintest/pre-high");
    if (part != null) request.setParameter("part", part);
    var application = JakartaServletWebApplication.buildApplication(request.getServletContext());
    var context = new WebContext(application.buildExchange(request, new MockHttpServletResponse()));
    context.setVariables(variables);
    return Jsoup.parse(engine.process(template, context));
  }

  @Test
  @DisplayName("given 구성 목록, when 표를 보면, then 6부 고2 실전 독해 모의고사가 5부와 7부 사이에 있다")
  void chooserListsPart6BetweenPart5AndPart7() {
    // given / when
    Document chooser = list(null, false);
    // then
    List<String> rows = chooser.select(".nt-table.nt-schools tbody tr").eachText();
    assertThat(rows).hasSize(7);
    assertThat(rows.get(4)).startsWith("5부");
    assertThat(rows.get(5)).isEqualTo("6부 고2 실전 독해 모의고사 18~45번 1회 열기");
    assertThat(rows.get(6)).startsWith("7부");
    assertThat(chooser.select("#part6 a").attr("href")).isEqualTo("/naesintest/pre-high?part=6");
  }

  @Test
  @DisplayName("given part=6, when 관리자가 목록을 열면, then 6부 표에 문제지 PDF와 열기가 있고 다른 부는 보이지 않는다")
  void part6ListShowsRoundsAndAdminPaper() {
    // given / when
    Document admin = list("6", true);
    // then
    assertThat(admin.select("#ph-practice6-title").text()).isEqualTo("6부. 고2 실전 독해 모의고사");
    assertThat(admin.select(".nt-group").text()).contains("18~45번", "45분", "어휘 50문항");
    assertThat(admin.select("table tbody a").eachAttr("href")).containsExactly(
        "/api/admin/pre-high-english/part6/1/paper.pdf", "/naesintest/pre-high/part6/1");
    assertThat(admin.select("tr[data-part6=1]")).hasSize(1);
    assertThat(admin.select("#ph-parts-title, #ph-practice-title, #ph-install")).isEmpty();
    assertThat(admin.select(".nt-top a[href=/naesintest/pre-high]").text()).isEqualTo("구성 목록");
  }

  @Test
  @DisplayName("given part=6, when 학생이 목록을 열면, then 문제지 PDF 칸과 내부 기록(source)·'변형' 문구가 없다")
  void part6ListHidesPaperAndSourceFromStudents() {
    // given / when
    Document student = list("6", false);
    // then
    assertThat(student.select("table tbody a").eachAttr("href")).containsExactly("/naesintest/pre-high/part6/1");
    assertThat(student.select("thead th").eachText()).doesNotContain("문제지");
    assertThat(student.text()).doesNotContain("내부 기록").doesNotContain("변형");
  }

  @Test
  @DisplayName("given 회차가 없는 6부, when 목록을 열면, then 준비 중으로 보인다")
  void emptyPart6IsShownAsPreparing() {
    // given
    Map<String, Object> variables = Map.of("rounds", List.of(), "grade2Rounds", List.of(), "part2Rounds", List.of(),
        "part6Rounds", List.of(), "grammarChapters", List.of(), "grammarFinalRounds", List.of(),
        "vocabularyRoundCount", 50, "gradeTwoVocabularyRoundCount", 40, "canInstall", false);
    // when
    Document chooser = render("prehigh/list", null, variables);
    Document part6 = render("prehigh/list", "6", variables);
    // then
    assertThat(chooser.select("#part6").text()).contains("준비 중");
    assertThat(part6.select(".nt-msg").text()).isEqualTo("회차를 준비 중입니다.");
    assertThat(part6.select("table")).isEmpty();
  }

  @Test
  @DisplayName("given 6부 응시 화면, when 그리면, then 6부 제목·45분·6부 API가 나오고 2부 화면은 그대로다")
  void testPageShowsPart6TitleAndTime() {
    // given / when
    Document part6 = test(6);
    Document part2 = test(2);
    // then
    assertThat(part6.title()).isEqualTo("6부 고2 실전 독해 1회 · 예비고1 디지털 영어 모의고사");
    assertThat(part6.select("h1").text()).isEqualTo("예비고1 디지털 영어 모의고사 6부 · 고2 실전 독해 1회");
    assertThat(part6.select("#ph-time").text()).isEqualTo("45:00");
    assertThat(part6.select("#ph-body p").text()).contains("독해 18~45번 28문항을 45분 안에");
    assertThat(part6.select("#ph-test").attr("data-api")).isEqualTo("/api/naesintest/pre-high/part6/1");
    assertThat(part6.select("#ph-test").attr("data-part")).isEqualTo("6");
    assertThat(part6.select(".nt-top a.nt-btn").attr("href")).isEqualTo("/naesintest/pre-high?part=6");
    assertThat(part2.title()).isEqualTo("2부 1회 · 예비고1 디지털 영어 모의고사");
    assertThat(part2.select("h1").text()).isEqualTo("예비고1 디지털 영어 모의고사 2부 · 고1 실전 독해 1회");
    assertThat(part2.select("#ph-time").text()).isEqualTo("45:00");
  }
}
