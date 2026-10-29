package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Feel2Real | EDUNOVA 홈 계약: 확정 문구·실제 링크·폐기 서비스 비노출. DB 없이 실제 렌더한다. */
class LandingEdunovaTemplateContractTest {

  private static String render() { return render("public/landing"); }

  private static String render(String template) {
    return render(template, f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController.DEFAULT_PROMO_PATH);
  }

  private static String render(String template, String promoPath) {
    return render(template, promoPath,
        f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController.DEFAULT_REVIEW_GAME_URL,
        f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController.HOME_AGENT_TRAILER_PATH);
  }

  private static String render(String template, String promoPath, String gameUrl, String trailerPath) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    Context context = new Context();
    context.setVariable("examOffer", HomeExamOffer.CURRENT);
    context.setVariable("examPromoPath", promoPath);
    context.setVariable("homeReviewGameUrl", gameUrl);
    context.setVariable("homeAgentTrailerPath", trailerPath);
    return engine.process(template, context);
  }

  @Test
  void givenContentPriority_whenRendered_thenRequestPromoGameTrailerOrderAndEmptySlotsAreHonest() {
    String html = render();
    String[] ids = {"aiHome", "examPromo", "review-game", "ai-content", "svc-exam"};
    int previous = -1;
    for (String id : ids) {
      int position = html.indexOf("id=\"" + id + "\"");
      assertThat(position).as(id).isGreaterThan(previous);
      previous = position;
    }
    assertThat(html).contains("id=\"homeReviewGame\"", "https://app.nimblefox.ai/g/g1y7QeOYd8", "id=\"homeAgentTrailer\"", "/video/landing/ai-agent-trailer-20261029-d23ef53c.mp4")
        .doesNotContain("트레일러 공개 준비 중", "g1y7QeOYd8/edit");
    assertThat(render("public/landing", "", "", "")).contains("게임 공개 준비 중")
        .doesNotContain("id=\"homeReviewGame\"");
    assertThat(html).contains("/video/landing/f2r-exam-remotion-v3-20261029.mp4", "id=\"examPromoSound\"");
    assertThat(render("public/landing", "")).doesNotContain("id=\"examPromo\"", "id=\"examPromoVideo\"")
        .contains("id=\"aiHome\"", "id=\"homeChat\"");
  }

  @Test
  void givenVerifiedContentSettings_whenRendered_thenPublicLinkAndManualTrailerUseSeparateSlots() {
    String html = render("public/landing", "", "https://app.nimblefox.ai/g/public-fixture",
        "/api/landing/videos/home-agent-trailer/stream");
    assertThat(html).contains("id=\"homeReviewGame\"", "https://app.nimblefox.ai/g/public-fixture",
        "rel=\"noopener noreferrer\"", "id=\"homeAgentTrailer\"", "preload=\"none\"",
        "/images/ai-agent-banner.png").doesNotContain("id=\"homeReviewGamePending\"",
        "id=\"homeAgentTrailerPending\"", "id=\"examPromoVideo\"");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 레이아웃이 해석되고 AI AGENT 이미지·2주 160문항·40문항·의뢰 버튼이 있다")
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
        .contains("2주 동안 160문항")
        .contains("40문항")
        .contains("https://open.kakao.com/o/sMf4ifRi")
        .contains("mailto:woorinaesin@naver.com")
        .doesNotContain("/exam-request")
        .contains("카카오톡 문의·신청");
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
  @DisplayName("given 홈 when 읽으면 then 회사 소개 문구와 기말고사 이용권 안내(완료 표현 없음)가 있다")
  void intro_and_notice() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("Feel2Real은 AI를 활용해 영어 교육과 에듀테인먼트 콘텐츠를 제작하는 회사입니다.")
        .contains("기출과 아주 유사한")
        .contains("id=\"pass-offer\"")
        .contains("href=\"/wiki\"")
        .doesNotContain("탑재 완료")
        .doesNotContain("10월 21일 탑재 예정");
  }

  @Test
  @DisplayName("given 새 우선순위 when 홈을 읽으면 then 기존 교육 서비스와 제작 예정 표기를 보존한다")
  void cards_inOrder() {
    // given / when
    String html = render();
    // then
    int exam = html.indexOf("id=\"svc-exam\"");
    int dash = html.indexOf("id=\"svc-dashboard\"");
    int ai = html.indexOf("id=\"ai-content\"");
    assertThat(exam).isPositive();
    assertThat(exam).isGreaterThan(dash);
    assertThat(dash).isGreaterThan(ai);
    assertThat(html).contains("AI 콘텐츠 제작").doesNotContain("EduNova").doesNotContain("EDUNOVA").contains("BuildIt").doesNotContain("GPU 에듀테인먼트").doesNotContain("AI AGENT 소개 보기");
    assertThat(html).contains("제작 예정").doesNotContain("href=\"#\"");
    assertThat(html).contains("href=\"/dashboard\"").contains("href=\"/ledger\"").contains("href=\"/admin/hub\"");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 복구한 적중 성과가 근거 문구·에듀홀릭 블로그 원문 링크와 함께 의뢰 버튼 앞에 있다")
  void restoredHitProof_linksToBlog() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .contains("압구정에듀홀릭 활용 사례")
        .contains("기출문항을 그대로 복제하거나 시험 적중을 보장하지 않습니다.")
        .doesNotContain("실제 적중률 100%")
        .contains("href=\"https://blog.naver.com/apgujungeduholic/224435388122\"");
    String[] sections = {"aiHome", "examPromo", "review-game", "ai-content", "svc-exam", "exam-samples", "exam-hit", "exam-blog", "exam-contact", "exam-payment", "chart-showcase"};
    int previous = -1;
    for (String id : sections) {
      int next = html.indexOf("id=\"" + id + "\"");
      assertThat(next).as(id).isGreaterThan(previous);
      previous = next;
    }
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
        .contains("href=\"mailto:woorinaesin@naver.com\"")
        .contains("data-copy-address=\"woorinaesin@naver.com\"")
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
    String hero = html.substring(html.indexOf("class=\"en-hero-art\""), html.indexOf("id=\"agentHighlightSlot\""));
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
  @Test
  @DisplayName("given current offer when rendered then all sales/SEO use 14 days, four deliveries, 160 questions without stale copy")
  void currentOffer_and_renderedEvidence() throws Exception {
    String html = render();
    assertThat(html).contains("2주 동안 160문항", "20,000원", "총 4회", "회당 40문항")
        .doesNotContain("3주", "240문항", "총 6회", "30,000원", "1회 20,000원");
    assertThat(html).contains("data-offer-version=\"final-exam-20261028-v2\"")
        .contains("id=\"examPromo\"").doesNotContain("src=\"\"");
    java.nio.file.Path output = java.nio.file.Path.of("build/verification/home-after.html");
    java.nio.file.Files.createDirectories(output.getParent());
    java.nio.file.Files.writeString(output, html);
    String baseline = System.getenv("F2R_BASELINE_TEMPLATE_ROOT");
    if (baseline != null) {
      var old = new org.thymeleaf.templateresolver.FileTemplateResolver();
      old.setPrefix(baseline + "/"); old.setSuffix(".html"); old.setCheckExistence(true); old.setOrder(1);
      old.setTemplateMode(TemplateMode.HTML); old.setCharacterEncoding("UTF-8");
      var fragments = new ClassLoaderTemplateResolver();
      fragments.setPrefix("templates/"); fragments.setSuffix(".html"); fragments.setOrder(2);
      fragments.setTemplateMode(TemplateMode.HTML); fragments.setCharacterEncoding("UTF-8");
      var engine = new TemplateEngine(); engine.addTemplateResolver(old); engine.addTemplateResolver(fragments);
      engine.addDialect(new LayoutDialect());
      java.nio.file.Files.writeString(output.resolveSibling("home-before.html"), engine.process("before-template", new Context()));
    }

  }

  @Test
  void givenOtherCurrentSalesPaths_whenRendered_thenUseSameOfferAndSeparateEmailControls() {
    for (String template : new String[]{"public/exam", "public/faq", "public/exam_insight"}) {
      String html = render(template);
      assertThat(html).as(template).contains("20,000원", "160문항", "총 4회")
          .doesNotContain("3주", "240문항", "총 6회", "기본 1회", "1회 20,000원");
    }
    assertThat(render("public/exam")).contains("href=\"mailto:woorinaesin@naver.com\"", "data-copy-address");
  }

}
