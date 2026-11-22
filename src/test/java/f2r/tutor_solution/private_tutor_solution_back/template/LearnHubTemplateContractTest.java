package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Card;
import f2r.tutor_solution.private_tutor_solution_back.domain.learnhub.LearnHubCatalog.Group;
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

/** 학습 콘텐츠 모음(/learn) 화면 계약 — 공개 layout, 카드마다 링크·학년·로그인 안내, 홈 메뉴 링크 한 줄씩. */
@DisplayName("학습 콘텐츠 모음 화면 템플릿 계약")
class LearnHubTemplateContractTest {

  private static String render() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    Context context = new Context();
    context.setVariable("groups", List.of(new Group("naesin", "내신 대비", "학교 시험 대비", List.of(
        new Card("압구정중학교 2학년 예상문제", "/naesintest/school/apgujeong-m2", "설명", List.of("중2"), "압구정중학교",
            LearnHubCatalog.LIST_OPEN, "16회차"),
        new Card("2027 수능 영어 해설", "/csat/2027-english", "설명", List.of("고3·수능"), null, LearnHubCatalog.OPEN, null)))));
    context.setVariable("audiences", LearnHubCatalog.AUDIENCES);
    context.setVariable("schools", List.of("압구정중학교"));
    return engine.process("public/learn", context);
  }

  private static String read(String path) throws IOException {
    return Files.readString(Path.of("src/main/resources/" + path), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("given 카드 2장 when 렌더 then 카드마다 링크·학년·학교 표시가 붙고, 로그인 안내는 색이 다른 표시로 나온다")
  void cards_renderLinksAndTags() {
    // when
    String html = render();

    // then
    assertThat(html).contains("href=\"/naesintest/school/apgujeong-m2\"").contains("data-audiences=\"중2\"")
        .contains("data-school=\"압구정중학교\"").contains("16회차").contains("lh-access is-login")
        .contains("href=\"/csat/2027-english\"").contains("lh-access is-open").contains("id=\"lh-naesin\"");
    assertThat(html.split("class=\"lh-volume\"", -1)).hasSize(2);
  }

  @Test
  @DisplayName("given 화면 when 렌더 then 공개 layout(방문 분석)·휴대폰 viewport·학년/학교 거르기 단추·대표 주소가 있다")
  void page_hasLayoutFiltersAndCanonical() throws IOException {
    // when
    String html = render();
    String template = read("templates/public/learn.html");

    // then
    assertThat(template).contains("layout:decorate=\"~{layouts/public").contains("layout:fragment=\"content\"");
    assertThat(html).contains("GTM-MMJ4MGNN").contains("width=device-width")
        .contains("https://feel2real.co.kr/learn").contains("data-audience=\"예비고1\"")
        .contains("data-school=\"압구정중학교\"").contains("id=\"lhEmpty\"");
    assertThat(Path.of("src/main/resources/static/css/learn_hub.css")).exists();
    assertThat(Path.of("src/main/resources/static/js/learn_hub.js")).exists();
  }

  @Test
  @DisplayName("given 홈 화면 when 읽기 then 상단 학습 콘텐츠 메뉴와 전체 메뉴에 모음 링크가 한 줄씩만 있고 홈 이동 표시는 붙이지 않는다")
  void landing_linksOncePerMenu() throws IOException {
    // when
    String landing = read("templates/public/landing.html");

    // then
    assertThat(landing.split("href=\"/learn\"", -1)).hasSize(3);
    assertThat(landing).contains("<a href=\"/learn\">학습 콘텐츠 모음</a>")
        .contains("<a href=\"/learn\"><span>학습 콘텐츠 모음</span></a>");
  }
}
