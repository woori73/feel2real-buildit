package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController;
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
        f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController.DEFAULT_REVIEW_GAME_URL, "");
  }

  private static String render(String template, String promoPath, String gameUrl, String trailerPath) {
    return render(template, promoPath, gameUrl, trailerPath,
        f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.HomeReviewGameController.PAGE_PATH);
  }

  private static String render(String template, String promoPath, String gameUrl, String trailerPath, String gamePath) {
    return render(template, promoPath, gameUrl, trailerPath, gamePath, null);
  }

  private static String render(String template, String promoPath, String gameUrl, String trailerPath, String gamePath,
      f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeBankTransfer bank) {
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
    context.setVariable("homeReviewGamePath", gamePath);
    context.setVariable("homeBankTransfer", bank);
    return engine.process(template, context);
  }

  @Test
  @DisplayName("given 개편 순서 when 렌더하면 then 상품→영상→콘텐츠 버튼→트레일러→게임→차트→비서→기존 안내 순서이고 빈 칸은 솔직하게 표시한다")
  void givenContentPriority_whenRendered_thenRequestPromoGameTrailerOrderAndEmptySlotsAreHonest() {
    String html = render();
    String[] ids = {"pass-offer", "examPromo", "homeJump", "ai-content", "review-game", "chart-showcase", "aiHome", "svc-exam"};
    int previous = -1;
    for (String id : ids) {
      int position = html.indexOf("id=\"" + id + "\"");
      assertThat(position).as(id).isGreaterThan(previous);
      previous = position;
    }
    assertThat(html).contains("id=\"homeReviewGame\"", "https://app.nimblefox.ai/g/g1y7QeOYd8", "트레일러 공개 준비 중")
        .contains("data-game-src=\"/games/g1-oct-review/v1/\"", "id=\"homeGameStart\"")
        .doesNotContain("id=\"homeAgentTrailer\"", "g1y7QeOYd8/edit");
    // The game iframe is created only after the user presses 게임 시작 (layout-level noscript tags excluded).
    assertThat(html.substring(html.indexOf("<main"), html.indexOf("</main>"))).doesNotContain("<iframe");
    String jump = html.substring(html.indexOf("id=\"homeJump\""), html.indexOf("id=\"ai-content\""));
    int trailer = jump.indexOf("href=\"#ai-content\""), game = jump.indexOf("href=\"#review-game\""), chart = jump.indexOf("href=\"#chart-showcase\"");
    assertThat(trailer).isPositive().isLessThan(game);
    assertThat(game).isLessThan(chart);
    assertThat(jump).contains("AI AGENT 트레일러", "고1 10월 영어 복습 게임", "BuildIt 차트 소개");
    assertThat(render("public/landing", "", "", "", "")).contains("게임 공개 준비 중")
        .doesNotContain("id=\"homeReviewGame\"", "id=\"homeGame\"");
    assertThat(html).contains("/video/landing/f2r-exam-remotion-v3-20261029.mp4", "id=\"examPromoSound\"");
    assertThat(render("public/landing", "")).doesNotContain("id=\"examPromo\"", "id=\"examPromoVideo\"")
        .contains("id=\"aiHome\"", "id=\"homeChat\"");
  }

  @Test
  void givenVerifiedContentSettings_whenRendered_thenPublicLinkAndManualTrailerUseSeparateSlots() {
    String html = render("public/landing", "", "https://app.nimblefox.ai/g/public-fixture",
        "/api/landing/videos/home-agent-trailer/stream");
    assertThat(html).contains("id=\"homeReviewGame\"", "https://app.nimblefox.ai/g/public-fixture",
        "rel=\"noopener noreferrer\"", "id=\"homeAgentTrailer\"", "preload=\"none\"").doesNotContain("id=\"homeReviewGamePending\"",
        "id=\"homeAgentTrailerPending\"", "id=\"examPromoVideo\"");
    String bundled = render("public/landing", "", "", "/video/landing/ai-agent-trailer-v16i-20261029.mp4");
    assertThat(bundled).contains("src=\"/video/landing/ai-agent-trailer-v16i-20261029.mp4\"", "id=\"homeAgentTrailerPlay\"")
        .doesNotContain("id=\"homeAgentTrailerPending\"");
  }

  @Test
  @DisplayName("given 공통 footer CSS when 읽으면 then 상담 팝업 디자인은 public_footer.css 한 곳에만 있고 테두리 상자 기준·밝은 연락처 색을 쓴다")
  void contactModalStyle_isSharedInPublicFooterCss() throws Exception {
    // given
    java.util.function.Function<String, String> css = path -> {
      try (var in = LandingEdunovaTemplateContractTest.class.getClassLoader().getResourceAsStream(path)) {
        return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    };
    // when
    String shared = css.apply("static/css/public_footer.css");
    // then
    assertThat(shared).contains("#contactModal { padding:16px; box-sizing:border-box;",
        "#contactModal *, #contactModal *::before, #contactModal *::after { box-sizing:border-box; }",
        "#contactModal .contact-modal-panel { width:100%; max-width:360px;", "#contactModal .contact-option-detail { margin-top:2px; color:#6ee4ff;",
        "#contactModal .contact-modal-close { width:100%; min-height:48px;");
    for (String page : new String[] {"static/css/home_exam.css", "static/css/landing_edunova.css", "static/css/f2r_home_agent.css"}) {
      assertThat(css.apply(page)).as(page).doesNotContain("#contactModal", ".public-contact-option");
    }
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 하단 문의하기는 버튼이고 상담 팝업은 클래스 스타일·전화·이메일 링크·닫기 버튼을 가진다")
  void footerContactButton_andReadableContactModal() {
    // given / when
    String html = render();
    String modal = html.substring(html.indexOf("id=\"contactModal\""), html.indexOf("<script", html.indexOf("id=\"contactModal\"")));
    // then
    assertThat(html).contains("<a href=\"#contactModal\" role=\"button\" class=\"footer-contact\" onclick=\"openContactModal(); return false;\">문의하기</a>");
    assertThat(modal).contains("class=\"contact-modal-panel\" role=\"dialog\"", "href=\"tel:010-8507-1453\"",
        "href=\"mailto:woorinaesin@naver.com\"", "<div class=\"contact-option-detail\">010-8507-1453</div>",
        "class=\"contact-modal-close\"");
    assertThat(modal).doesNotContain("color:#6b7280", "background:#fff;");
    assertThat(html).contains("/css/public_footer.css?v=20261030-2");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 제목은 두 줄 단위로 나뉘고 문제지·해설지 샘플 섹션은 없으며 활용 사례가 바로 이어진다")
  void titleLines_andSampleSectionRemoved() {
    // given / when
    String html = render();
    // then
    assertThat(html).contains("<h1 class=\"home-offer-title\" id=\"siHomeTitle\"><span class=\"home-offer-title-line\">기출과 아주 유사한</span> "
        + "<span class=\"home-offer-title-line\">영어 기말 대비 문제</span></h1>");
    assertThat(html).doesNotContain("id=\"exam-samples\"", "WORKSHEETS &amp; EXPLANATIONS", "문제지와 해설지 살펴보기",
        "공개 샘플은 준비 중", "문제지·해설지 문의");
    String between = html.substring(html.indexOf("id=\"svc-exam\""), html.indexOf("id=\"exam-hit\""));
    assertThat(between.split("<section", -1)).hasSize(2);
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 2주·160문항·20,000원은 위 상품 안내에 한 번만 보이고 영상 아래 반복 문구·버튼이 없다")
  void offer_isShownOnceAtTop() {
    // given / when
    String html = render();
    String offer = html.substring(html.indexOf("id=\"pass-offer\""), html.indexOf("id=\"examPromo\""));
    String promo = html.substring(html.indexOf("id=\"examPromo\""), html.indexOf("id=\"homeJump\""));
    // then
    assertThat(offer).contains("2주 동안 160문항", "주 2회 · 총 4회 제공").containsOnlyOnce("20,000원");
    assertThat(promo).doesNotContain("맞춤 문제 문의·신청", "exam-caption\">학교별")
        .contains("id=\"examPromoSummary\" class=\"sr\"");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 레이아웃이 해석되고 2주 160문항·40문항·의뢰 버튼이 있으며 트레일러 아래 AI AGENT 배너(화보)는 없다")
  void hero_showsOfferWithoutAgentBanner() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .doesNotContain("th:block")
        .doesNotContain("th:replace")
        .doesNotContain("/images/ai-agent-banner.png", "/images/ai-agent-banner-m.webp", "en-hero-art", "id=\"enHeroStudio\"")
        .doesNotContain("/images/ai-agent-hero.jpg")
        .contains("2주 동안 160문항")
        .contains("40문항")
        .contains("https://open.kakao.com/o/sMf4ifRi")
        .contains("mailto:woorinaesin@naver.com")
        .doesNotContain("/exam-request")
        .contains("카카오톡 문의·신청");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 상단 메뉴는 5개로 줄고 BuildIt 표기·예상문제·AI 콘텐츠 소개는 없으며 기존 서비스 링크는 더보기에 모두 남는다")
  void menu_isCompactAndKeepsEveryService() {
    // given / when
    String html = render();
    String nav = html.substring(html.indexOf("class=\"en-menu\""), html.indexOf("class=\"en-actions\""));
    String drawer = html.substring(html.indexOf("id=\"enDrawer\""), html.indexOf("<main"));
    String header = html.substring(html.indexOf("id=\"enNav\""), html.indexOf("<main"));
    // then
    String[] labels = {">문법 문제은행<", "<summary>내신 제작소</summary>", ">AI AGENT 제작협업실<", "<summary>더보기</summary>"};
    int prev = -1;
    for (String l : labels) {
      int at = nav.indexOf(l);
      assertThat(at).as(l).isGreaterThan(prev);
      prev = at;
    }
    assertThat(nav.indexOf(">모의고사위키<")).isBetween(nav.indexOf(">문법 문제은행<"), nav.indexOf("<summary>내신 제작소</summary>"));
    assertThat(header).doesNotContain(">예상문제<", ">AI 콘텐츠 소개<", "en-logo-sub", ">BuildIt<", "en-mobile-services");
    String[] links = {"/grammar-items", "/wiki", "/naesin-production-studio?division=MIDDLE", "/naesin-production-studio?division=HIGH",
        "/ai-agent-studio", "/dashboard", "/ledger", "/builder", "/chart"};
    for (String menu : new String[] {nav, drawer}) {
      for (String link : links) assertThat(menu).as(link).contains("href=\"" + link + "\"");
    }
    assertThat(menuLinks(nav)).isEqualTo(menuLinks(drawer));
    assertThat(html).doesNotContain("학습자료실").doesNotContain("고객센터");
  }

  private static java.util.List<String> menuLinks(String fragment) {
    return java.util.regex.Pattern.compile("href=\"([^\"]+)\"").matcher(fragment).results()
        .map(m -> m.group(1)).toList();
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
  @DisplayName("given 새 우선순위 when 홈을 읽으면 then 기존 교육 서비스 링크를 보존한다")
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
    assertThat(html).doesNotContain("href=\"#\"");
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
    String[] sections = {"pass-offer", "examPromo", "homeJump", "ai-content", "review-game", "chart-showcase", "aiHome", "svc-exam", "exam-hit", "exam-blog", "exam-contact", "exam-payment"};
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
  @DisplayName("given 홈 when 읽으면 then PC 상단 메뉴·모바일 메뉴에서 제작협업실로 이동하는 링크가 보인다")
  void studioEntryLinks_visibleInMenus() {
    // given / when
    String html = render();
    String nav = html.substring(html.indexOf("class=\"en-menu\""), html.indexOf("class=\"en-actions\""));
    String drawer = html.substring(html.indexOf("id=\"enDrawer\""), html.indexOf("<main"));
    // then: 접근 권한(ADMIN·modu)은 서버가 확인하므로 링크 자체는 숨기지 않는다
    assertThat(nav).contains("href=\"/ai-agent-studio\"").contains(">AI AGENT 제작협업실<");
    assertThat(drawer).contains("href=\"/ai-agent-studio\"").contains(">AI AGENT 제작협업실<");
    assertThat(html).doesNotContain("data-studio-entry hidden");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then PC 상단 메뉴·모바일 메뉴에 내신 제작소가 있고 중등부·고등부 제작소로 이동한다")
  void naesinStudioEntry_visibleInMenusWithDivisionChoice() {
    // given / when
    String html = render();
    String nav = html.substring(html.indexOf("class=\"en-menu\""), html.indexOf("class=\"en-actions\""));
    String drawer = html.substring(html.indexOf("id=\"enDrawer\""), html.indexOf("<main"));
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


  @Test
  @DisplayName("given 계좌 공개 when 홈 결제 안내를 그리면 then 무통장입금 은행·계좌·예금주·금액·서비스와 복사 버튼을 보여 주고 '아직 제공하지 않습니다' 문구가 없다")
  void bankTransfer_isShownOnlyWhenPublic() {
    // given
    var bank = f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeBankTransfer.CURRENT;
    // when
    String shown = render("public/landing", LandingPageController.DEFAULT_PROMO_PATH,
        LandingPageController.DEFAULT_REVIEW_GAME_URL, "", f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.HomeReviewGameController.PAGE_PATH, bank);
    String hidden = render();
    // then
    String payment = shown.substring(shown.indexOf("id=\"exam-payment\""), shown.indexOf("</section>", shown.indexOf("id=\"exam-payment\"")));
    assertThat(payment).contains("이용료와 무통장입금 안내", "신한은행", "110-498-250866", "박정민", "20,000원",
        "<dt>서비스</dt>", "2주 · 주 2회 · 총 4회 · 160문항", "data-copy-address=\"110-498-250866\"", "관리자가 입금을 직접 확인");
    assertThat(shown).doesNotContain("온라인 계좌 안내는 아직", "임의로 입금하지");
    assertThat(payment).contains("href=\"/policies/refunds\"", "청약철회 제한과 회차별 환불 기준");  // 청약철회 제한 사전 표시
    assertThat(payment).containsOnlyOnce("20,000원").containsOnlyOnce("2주 · 주 2회 · 총 4회 · 160문항").doesNotContain("exam-payment-price");
    assertThat(hidden).doesNotContain("110-498-250866").contains("입금 계좌는 문의해 주시면 안내합니다")
        .doesNotContain("온라인 계좌 안내는 아직");
  }

  @Test
  @DisplayName("given 홈 when 그리면 then footer에 확정 출판사 사업자 정보와 확인 링크가 있고 학원 사업자 정보는 없다")
  void footer_showsConfirmedPublisher() {
    // given / when
    String html = render();
    String footer = html.substring(html.indexOf("class=\"public-footer\""));
    // then
    assertThat(footer).contains("상호명: 압구정에듀홀릭출판사", "대표: 박정민", "사업자등록번호: 146-99-01347",
        "통신판매업 신고번호: 제2026-서울송파-1411호", "이메일: woorinaesin@naver.com",
        "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=1469901347");
    assertThat(html).doesNotContain("압구정에듀홀릭학원", "759-91-01977", "7599101977", "압구정로34길");
  }

  @Test
  @DisplayName("given 시험 문제지 페이지 when 그리면 then 같은 출판사 사업자 정보를 쓰고 학원 정보는 없다")
  void examPage_usesSameSellerFragment() {
    // given / when
    String html = render("public/exam");
    // then
    String biz = html.substring(html.indexOf("class=\"exam-bizinfo\""));
    assertThat(biz).contains("압구정에듀홀릭출판사", "146-99-01347", "제2026-서울송파-1411호", "010-8507-1453", "woorinaesin@naver.com");
    assertThat(html).doesNotContain("압구정에듀홀릭학원", "759-91-01977", "02-6324-1453");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 압구정중3 적중 사례가 홍보 영상·바로가기 바로 아래 한 화면에 있고 항목을 누르면 펼쳐지는 구조이며 이미지가 모두 있다")
  void hitCase_sitsRightBelowPromoJump_andExpandsInPlace() {
    // given
    String html = render();
    int jump = html.indexOf("id=\"homeJump\"");
    int hit = html.indexOf("id=\"hitCase\"");
    int ai = html.indexOf("id=\"ai-content\"");
    // when
    String section = html.substring(hit, ai);
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"(/img/exam-cases/[^\"]+)\"").matcher(section);
    java.util.Set<String> images = new java.util.LinkedHashSet<>();
    while (m.find()) images.add(m.group(1));
    // then
    assertThat(hit).isGreaterThan(jump).isLessThan(ai);
    assertThat(html.substring(jump, hit).split("<section", -1)).hasSize(2);
    assertThat(section)
        .contains("93.5", "예상문제 범위 31문항 중 29문항 적중", "배점 기준 90점 중 85점 · 대화문 5문항 제외", "92점")
        .contains("id=\"hitStory\"", "id=\"hitCompare\"", "id=\"hitScope\"", "id=\"hitAll\"")
        .contains("개별 사례는 다른 학교나 시험의 적중률을 보장하지 않습니다.")
        .doesNotContain("100%", "href=\"/exam-cases");
    assertThat(section.split("class=\"hit-q\"", -1)).hasSize(16);
    assertThat(images).hasSize(30);
    for (String path : images) {
      assertThat(getClass().getResource("/static" + path)).as(path).isNotNull();
    }
  }
}
