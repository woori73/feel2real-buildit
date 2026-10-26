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
        .contains("/images/ai-agent-banner.png")
        .contains("/images/ai-agent-banner.webp")
        .contains("/images/ai-agent-banner-m.webp")
        .doesNotContain("/images/ai-agent-hero.jpg")
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
    String[] labels = {"예상문제", "대시보드", "이지장부", "모의고사위키", "학원 홈페이지 제작", "AI 콘텐츠 소개"};
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
    assertThat(html).contains("AI 콘텐츠 제작").doesNotContain("EduNova").doesNotContain("EDUNOVA").contains("BuildIt").doesNotContain("GPU 에듀테인먼트").doesNotContain("AI AGENT 소개 보기");
    assertThat(html).contains("class=\"en-coming-soon\"").contains("COMING SOON").doesNotContain("href=\"#top\"").doesNotContain("href=\"#\"");
    assertThat(html).contains("2026년 11월 1일").contains("href=\"/dashboard\"").contains("href=\"/ledger\"").contains("href=\"/admin/hub\"");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 복구한 적중 성과가 근거 문구·에듀홀릭 블로그 원문 링크와 함께 의뢰 버튼 앞에 있다")
  void restoredHitProof_linksToBlog() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("40문항 직보 예상문제,")
        .contains("실제 적중률 100%")
        .contains("압구정중2 영어 시험은 어떻게 나왔을까?")
        .contains("href=\"#exam-hit\"")
        .contains("100% 출제")
        .contains("외부지문 2문항(5 · 9번) 제외, 28문항 전부")
        .contains("href=\"https://blog.naver.com/apgujungeduholic/224435388122\"");
    int hero = html.indexOf("id=\"enExamCta\"");
    int hit = html.indexOf("id=\"exam-hit\"");
    int hitBlog = html.indexOf("id=\"enHitBlog\"");
    int chart = html.indexOf("id=\"chart-showcase\"");
    int notice = html.indexOf("class=\"en-notice\"");
    int services = html.indexOf("id=\"svc-exam\"");
    // 공지는 상단(히어로 직후), 적중 성과가 그 다음, BUILD IT CHART는 맨 마지막
    assertThat(hero).isPositive();
    assertThat(notice).isGreaterThan(hero);
    assertThat(hit).isGreaterThan(notice);
    assertThat(hitBlog).isGreaterThan(hit);
    assertThat(services).isGreaterThan(hitBlog);
    assertThat(chart).isGreaterThan(services);
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then BUILD IT CHART 공개 영역이 기존 스크립트 훅과 /chart 링크로 복구돼 있고 이름 검색·관리자 경로는 없다")
  void restoredChart_usesPublicHooks() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("BUILD IT CHART")
        .contains("id=\"chartRegionPicker\"", "id=\"chartProvince\"", "id=\"chartDistrict\"", "id=\"landingChartTop5\"")
        .contains("/js/landing_chart.js")
        .contains("href=\"/chart\"", "/chart#criteria")
        .doesNotContain("id=\"chartNameSearch\"")
        .doesNotContain("/admin/chart");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 이메일 주소가 화면에 보이고 복사 안내가 있으며 로그인 안내 복귀 스크립트가 있다")
  void emailAddressVisible_andLoginGuidance() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("class=\"en-mail-addr\">woorinaesin@naver.com</b>")
        .contains("data-copy-mail=\"woorinaesin@naver.com\"")
        .contains("params.get('login') !== 'required'")
        .contains("f2r:login");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then PC 상단 메뉴·모바일 메뉴·AI AGENT 배너에서 제작협업실로 이동하는 링크가 보인다")
  void studioEntryLinks_visibleInMenusAndBanner() {
    // given / when
    String html = render();
    String nav = html.substring(html.indexOf("class=\"en-menu\""), html.indexOf("class=\"en-actions\""));
    String drawer = html.substring(html.indexOf("id=\"enDrawer\""), html.indexOf("class=\"en-mobile-services\""));
    String mobile = html.substring(html.indexOf("class=\"en-mobile-services\""), html.indexOf("<main"));
    String hero = html.substring(html.indexOf("class=\"en-hero\""), html.indexOf("class=\"en-offer\""));
    // then: 접근 권한(ADMIN·modu)은 서버가 확인하므로 링크 자체는 숨기지 않는다
    assertThat(nav).contains("href=\"/ai-agent-studio\"").contains(">AI AGENT 제작협업실<");
    assertThat(drawer).contains("href=\"/ai-agent-studio\"").contains(">AI AGENT 제작협업실<");
    assertThat(mobile).contains("href=\"/ai-agent-studio\"").contains(">AI AGENT 제작협업실<");
    assertThat(hero).contains("id=\"enHeroStudio\" href=\"/ai-agent-studio\"").contains("/images/ai-agent-banner.png");
    assertThat(html).doesNotContain("data-studio-entry hidden");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then PC 상단 메뉴·모바일 메뉴에 내신 제작소가 있고 중등부·고등부 제작소로 이동한다")
  void naesinStudioEntry_visibleInMenusWithDivisionChoice() {
    // given / when
    String html = render();
    String nav = html.substring(html.indexOf("class=\"en-menu\""), html.indexOf("class=\"en-actions\""));
    String drawer = html.substring(html.indexOf("id=\"enDrawer\""), html.indexOf("class=\"en-mobile-services\""));
    // then: 접근 권한(ADMIN·modu)은 서버가 확인하므로 링크 자체는 숨기지 않는다
    for (String menu : new String[] {nav, drawer}) {
      assertThat(menu)
          .contains("<summary>내신 제작소</summary>")
          .contains("href=\"/naesin-production-studio?division=MIDDLE\">중등부 제작소<")
          .contains("href=\"/naesin-production-studio?division=HIGH\">고등부 제작소<")
          .contains("href=\"/wiki\"")
          .contains("href=\"/ai-agent-studio\"");
    }
  }
}
