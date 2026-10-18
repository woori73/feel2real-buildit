package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Feel2Real | EDUNOVA 홈 계약: 확정 문구·실제 링크·폐기 서비스 비노출. DB 없이 실제 렌더한다. */
class LandingEdunovaTemplateContractTest {

  private static String render() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    return engine.process("public/landing", new Context());
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 레이아웃이 해석되고 AI AGENT 이미지·1회 20,000원·40문항·의뢰 버튼이 있다")
  void hero_showsAgentImageAndOffer() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .doesNotContain("th:block")
        .doesNotContain("th:replace")
        .contains("/images/ai-agent-hero.jpg")
        .contains("제작 예정")
        .contains("1회 20,000원")
        .contains("40문항")
        .contains("https://open.kakao.com/o/sMf4ifRi")
        .contains("mailto:woorinaesin@naver.com")
        .doesNotContain("/exam-request")
        .contains("예상문제 의뢰하기");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 상단 메뉴 6개가 순서대로 있고 학습자료실·고객센터는 없다")
  void menu_hasSixItemsInOrder() {
    // given / when
    String html = render();
    String nav = html.substring(html.indexOf("class=\"en-menu\""), html.indexOf("class=\"en-actions\""));
    // then
    String[] labels = {"예상문제", "대시보드", "EASY 장부", "모의고사위키", "학원 홈페이지 제작", "AI 콘텐츠 소개"};
    int prev = -1;
    for (String l : labels) {
      int at = nav.indexOf(">" + l + "<");
      assertThat(at).as(l).isGreaterThan(prev);
      prev = at;
    }
    assertThat(html).doesNotContain("학습자료실").doesNotContain("고객센터");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 폐기한 DesignCraft·기출분석·출제방향 제작이 공개 화면에 없다")
  void discardedServices_notExposed() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .doesNotContain("DesignCraft")
        .doesNotContain("PhotoCraft")
        .doesNotContain("기출분석")
        .doesNotContain("출제방향")
        .doesNotContain("/exam-analysis")
        .doesNotContain("/designcraft");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 회사 소개 문구와 모의고사 해설지 안내(탑재 예정, 완료 표현 없음)가 있다")
  void intro_and_notice() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("Feel2Real은 AI를 활용해 영어 교육과 에듀테인먼트 콘텐츠를 제작하는 회사입니다.")
        .contains("10월 21일 탑재 예정")
        .contains("href=\"/wiki\"")
        .doesNotContain("탑재 완료");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 카드 3개가 순서대로 있고 GPU 에듀테인먼트는 제작 착수 예정으로만 표기한다")
  void cards_inOrder() {
    // given / when
    String html = render();
    // then
    int exam = html.indexOf("id=\"svc-exam\"");
    int dash = html.indexOf("id=\"svc-dashboard\"");
    int ai = html.indexOf("id=\"ai-content\"");
    assertThat(exam).isPositive();
    assertThat(dash).isGreaterThan(exam);
    assertThat(ai).isGreaterThan(dash);
    assertThat(html).contains("2026년 11월 1일").contains("href=\"/dashboard\"").contains("href=\"/ledger\"").contains("href=\"/admin/hub\"");
  }
}
