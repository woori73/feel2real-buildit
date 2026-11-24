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

  static String render() { return render("public/landing"); }

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
  @DisplayName("given 신구중2 디지털 기말고사 12회 탑재(V1136), when 홈을 그리면 then 학교 고르기 표에 Lesson 7·8 · 12회와 열기 링크가 보인다")
  void givenSinguRoundsUploaded_whenRendered_thenSchoolTableShowsTwelveRounds() {
    // given / when
    String html = render();
    // then
    assertThat(html).contains("<tr><td>신구중학교</td><td>2학년</td><td>Lesson 7·8</td><td>12회</td><td><a class=\"hw-open\" href=\"/naesintest/school/singu-m2\">열기</a></td></tr>")
        .doesNotContain("<tr><td>신구중학교</td><td>2학년</td><td>-</td><td>준비 중</td>");
  }

  @Test
  @DisplayName("given 압구정중1 디지털 기말고사 7회 탑재, when 홈을 그리면 then 학교 고르기 표에 Lesson 3~5 · 7회와 열기 링크가 보인다")
  void givenApgujeongM1RoundsUploaded_whenRendered_thenSchoolTableShowsSevenRounds() {
    // given / when
    String html = render();
    // then
    assertThat(html).contains("<tr><td>압구정중학교</td><td>1학년</td><td>Lesson 3~5</td><td>7회</td><td><a class=\"hw-open\" href=\"/naesintest/school/apgujeong-m1\">열기</a></td></tr>")
        .doesNotContain("<tr><td>압구정중학교</td><td>1학년</td><td>-</td><td>준비 중</td>");
  }

  @Test
  @DisplayName("given 압구정중1 기말 모의고사 7회 업로드 when 홈을 렌더하면 then 공지 맨 앞에 11월 21일 공지가 학교 페이지 링크로 보인다")
  void givenApgujeongM1Upload_whenRendered_thenNoticeLeadsWithSchoolPageLink() {
    // given / when
    String html = render();
    int track = html.indexOf("id=\"homeNoticeTrack\"");
    int notice = html.indexOf("<a href=\"/naesintest/school/apgujeong-m1\"><time datetime=\"2026-11-21\">11월 21일(토)</time> 압구정중1 기말 모의고사 7회 업로드</a>");
    int previous = html.indexOf("<a href=\"/csat/2027-english\"><time datetime=\"2026-11-19\">");
    // then
    assertThat(track).isPositive();
    assertThat(notice).isGreaterThan(track);
    assertThat(previous).isGreaterThan(notice);
    assertThat(html).contains("<span><time datetime=\"2026-11-21\">11월 21일(토)</time> 압구정중1 기말 모의고사 7회 업로드</span>");
  }

  @Test
  @DisplayName("given 2026-11-21 AI Coordinator 개편 순서 when 렌더하면 then Coordinator→문제 풀기(2026-11-23 상단 이동)→하는 일→⓪대시보드→배너·영상→①디지털 기말내신→실증 근거(적중 사례)→②입시상담 AI→③학습 콘텐츠→④부정행위 방지→서비스 도입 문의(2026-11-23 맞춤문제 삭제)→다른 서비스(AI AGENT→전자책→게임→차트)이고 빈 칸은 솔직하게 표시한다")
  void givenContentPriority_whenRendered_thenApprovedSinglePageOrderAndEmptySlotsAreHonest() {
    String html = render();
    String[] ids = {"coordinator", "learn", "learn-hub", "coordinator-work", "academy-dashboard", "pass-offer", "homeHero", "examPromo", "digital-exam",
        "pre-high-english", "dexam-guide", "hit-proof", "hitCase", "exam-blog", "counsel", "/admissions/guide", "learning-content",
        "exam-security", "contact", "other-services", "ai-content",
        "ebook", "review-game", "chart-showcase"};
    int previous = -1;
    for (String id : ids) {
      int position = id.startsWith("/") ? html.indexOf("href=\"" + id + "\"") : html.indexOf("id=\"" + id + "\"");
      assertThat(position).as(id).isGreaterThan(previous);
      previous = position;
    }
    // 2026-11-23 대표 지시: 맞춤문제 영역(#service: 신청·제작 과정·이용료·문의·이용 방법)은 지우고 서비스 도입 문의(#contact) 하나만 둔다.
    assertThat(html).doesNotContain("id=\"service\"", "id=\"request\"", "id=\"svc-exam\"", "id=\"exam-payment\"", "id=\"exam-contact\"", "id=\"guide\"");
    String proof = html.substring(html.indexOf("id=\"hit-proof\""), html.indexOf("id=\"counsel\""));
    // 적중률 문구는 개편 전 홈 내용 그대로 둔다(2026-11-21 사용자 지시). 위치만 옮겼다.
    assertThat(proof).contains("id=\"hitCase\"", "id=\"exam-blog\"", "<p>압구정중3 · 언북중3 · 신구중3</p>")
        .doesNotContain("3개교 4개 시험", "hw-proof-basis");
    // 2026-11-16: 왼쪽 기본 메뉴와 겹치는 영어 교육 카드·대시보드 바로가기 칸은 본문에서 뺐다(메뉴의 대시보드는 /dashboard로 바로 간다).
    assertThat(html).doesNotContain("id=\"edu\"", "id=\"dashboard\"", "href=\"#dashboard\"", "href=\"#edu\"");
    assertThat(html).contains("id=\"homeReviewGame\"", "https://app.nimblefox.ai/g/g1y7QeOYd8", "트레일러 공개 준비 중")
        .contains("data-game-src=\"/games/g1-oct-review/v1/\"", "id=\"homeGameStart\"")
        .doesNotContain("id=\"homeAgentTrailer\"", "g1y7QeOYd8/edit");
    // The game iframe is created only after the user presses 게임 시작 (layout-level noscript tags excluded).
    assertThat(html.substring(html.indexOf("<main"), html.indexOf("</main>"))).doesNotContain("<iframe");
    assertThat(html).contains("href=\"#contact\"", "href=\"/wiki\"", "href=\"#review-game\"", "href=\"/testprep\"", "New iBT")
        .doesNotContain("문법 문제은행");
    assertThat(html).doesNotContain("data-home-panel", "id=\"homeJump\"", "id=\"homeSidebar\"");
    assertThat(render("public/landing", "", "", "", "")).contains("게임 공개 준비 중")
        .doesNotContain("id=\"homeReviewGame\"", "id=\"homeGame\"");
    assertThat(html).contains("/video/landing/f2r-class-ad-20261120.mp4", "id=\"examPromoSound\"");
    assertThat(render("public/landing", "")).doesNotContain("id=\"examPromo\"", "id=\"examPromoVideo\"")
        .doesNotContain("id=\"aiHome\"", "id=\"homeChat\""); // 홈 AI 비서(서수니·세븐)는 2026-11-15에 내렸다
  }

  @Test
  @DisplayName("given 2026-11-21 개편 지시서 when 홈을 렌더하면 then Coordinator 대표 문구·실증 기능 5개·하는 일 4단계·대시보드 예시·부정행위 방지 사실 문구와 시연 문의가 있고 과장·Claude 표기·탐지율이 없다")
  void givenCoordinatorBrief_whenRendered_thenNewSectionsStateFactsOnly() {
    // given
    String html = render();
    // when
    String hero = html.substring(html.indexOf("id=\"coordinator\""), html.indexOf("id=\"coordinator-work\""));
    String work = html.substring(html.indexOf("id=\"coordinator-work\""), html.indexOf("id=\"academy-dashboard\""));
    String dash = html.substring(html.indexOf("id=\"academy-dashboard\""), html.indexOf("id=\"pass-offer\""));
    String learning = html.substring(html.indexOf("id=\"learning-content\""), html.indexOf("id=\"exam-security\""));
    String security = html.substring(html.indexOf("id=\"exam-security\""), html.indexOf("id=\"contact\""));
    String main = html.substring(html.indexOf("<main"), html.indexOf("</main>"));
    // then
    assertThat(hero).contains("F2R AI 코디", "휴대폰 하나로<br>학원 업무를 AI에게", "class=\"hw-coord-logo\" src=\"/img/F2R_logo_pure.svg\"", "href=\"#academy-dashboard\"", "href=\"#digital-exam\"", "href=\"#counsel\"", "href=\"#learning-content\"");
    // 2026-11-21 사용자 지시: 제목 아래 설명 문장(사업계획서 문구)은 홈페이지에 쓰지 않는다.
    assertThat(hero).doesNotContain("hw-coord-lead", "원장이 승인한 것만");
    // 2026-11-21 사용자 지시: 이름은 "F2R 영어"로만 쓰고 "교습소"·"영어 학원"은 쓰지 않는다. 제목에 F2R 로고
    assertThat(hero).doesNotContain("교습소", "영어 학원", "새 학원");
    var entries = org.jsoup.Jsoup.parse(main).select("#study-entry > article");
    // 2026-11-24 대표 지시: 대문 = F2R 사업 분야 4개(교습소 · AI 코디 · 인강·드라마·홍보 영상 제작 · AI 출판·전자책).
    assertThat(entries).hasSize(4);
    assertThat(entries.select("h2").eachText())
        .containsExactly("F2R 영어 교습소", "F2R AI 코디", "인강·드라마·홍보 영상 제작", "AI 출판·전자책");
    assertThat(entries.select("article > a:last-child").eachAttr("href"))
        .containsExactly("https://eduholic.feel2real.co.kr/", "#coordinator", "#contact", "#ebook");
    assertThat(entries.get(0).select("a[href=/learn], a[href=/naesintest]")).hasSize(2);
    assertThat(entries.get(1).text()).contains("말·글·사진으로 명령", "F2R 엔진", "무료 학원 홈페이지 제작", "출결·채점·복식장부", "내신·수능 최신 AI 출제");
    assertThat(entries.get(1).select(".f2r-entry-offer > li")).hasSize(3);
    assertThat(entries.get(2).text()).contains("학원에 코디와 함께 제공", "직보 AI 인강", "홍보 영상", "AI AGENT", "카카오톡 문의");
    assertThat(entries.get(3).text()).contains("출판사", "AI", "새출발기금");
    assertThat(hero).contains("class=\"hw-coord-offer\"", "복식장부", "최신 AI 출제");
    // 2026-11-24 대표 지시: 교습소 이전 줄·주소·지도는 코디 칸에서 빼고 교습소 홈페이지로 옮겼다.
    assertThat(hero).doesNotContain("새 주소로 이전", "class=\"hd-locs\"", "hw-coord-addr");
    assertThat(work.split("class=\"hw-card\"", -1)).hasSize(5);
    assertThat(work).contains("말·사진으로 지시", "출결·채점", "자료 제작·학부모 안내", "원장 승인 후 기록");
    assertThat(dash).contains("원장 로그인 후 사용", "href=\"/dashboard\"", "href=\"/ledger\"", "가명 자료",
        "/img/landing/home-academy-dashboard-sample.webp");
    assertThat(learning).contains("href=\"/testprep\"", "href=\"/wiki\"", "href=\"/csat/2027-english\"");
    // 지시서 6번: ① 디지털 기말내신 영역에 재원생 앱 설치 버튼과 안드로이드·아이폰 안내
    String digital = html.substring(html.indexOf("id=\"digital-exam\""), html.indexOf("id=\"pre-high-english\""));
    assertThat(digital).contains("id=\"student-app\"", "href=\"/naesintest/app\"", "재원생 앱 설치", "안드로이드(크롬)", "아이폰(사파리)");
    assertThat(security).contains("휴대폰 플래시를 끄고 켠 사진 두 장을 비교해 렌즈 의심 위치를 표시합니다(11.21 배포).",
        "종이 시험지 기술", "(연구 중)", "찍은 시험지로 AI가 정답을 내기 어렵게 하는 종이 장치를 연구 중입니다. 실제 인쇄·촬영 측정 전입니다.", "관련 기사·기술 정리",
        "최종 판단은 감독관이 합니다. 신호 차단기는 쓰지 않습니다.", "href=\"#contact\"", "학교·학원 시연 문의")
        .doesNotContain("href=\"/easy/exam-security/lens\"", "탐지율", "%");
    for (String path : new String[] {"/img/landing/home-academy-dashboard-sample.webp", "/img/landing/home-exam-security-lens.webp",
        "/img/landing/home-exam-security-paper.webp"}) {
      assertThat(getClass().getResource("/static" + path)).as(path).isNotNull();
    }
    assertThat(main).doesNotContain("Claude", "혁신", "최고", "완벽 차단");
    // 2026-11-21 사용자 지시: 홈페이지 글에서 '교습소 실증' 표현은 쓰지 않는다(주석 제외).
    assertThat(html.replaceAll("<!--[\\s\\S]*?-->", "")).doesNotContain("교습소 실증", "실증 중", "실증 근거", "실증하는");
    // 2026-11-21 사용자 지시: 화면 글자는 "Coordinator" 대신 "코디"(앵커 #coordinator는 그대로)
    assertThat(html.replaceAll("<!--[\\s\\S]*?-->", "")).doesNotContain("AI Coordinator", "Coordinator가", "Coordinator·");
  }

  @Test
  @DisplayName("given 2026-11-21 상단 펼침 메뉴 when 렌더하면 then 5묶음 버튼(2026-11-23 맞춤문제 묶음 삭제)이 패널과 이어지고, 옮긴 사이드 메뉴의 링크가 모두 상단 메뉴에 있으며, 서비스 지도는 첫 화면 대표 문구 안에 있다")
  void givenTopDropdownNav_whenRendered_thenGroupsCarryEverySideMenuLinkAndMapIsOnFirstScreen() {
    // given
    String html = render();
    // when
    String top = html.substring(html.indexOf("id=\"enNav\""), html.indexOf("</header>", html.indexOf("id=\"enNav\"")));
    String hero = html.substring(html.indexOf("id=\"coordinator\""), html.indexOf("id=\"coordinator-work\""));
    // then
    for (String key : new String[] {"coord", "demo", "learn", "more"}) {
      assertThat(top).as(key).contains("data-dd=\"" + key + "\"", "aria-controls=\"dd-" + key + "\"", "id=\"dd-" + key + "\"");
    }
    assertThat(top.split("class=\"hl-dd-btn\"", -1)).hasSize(5);
    assertThat(top).contains(">AI 코디<", ">시험·입시<", ">학습 콘텐츠<", ">다른 서비스<").doesNotContain("data-dd=\"offer\"", ">맞춤문제<");
    for (String link : new String[] {"#coordinator", "#academy-dashboard", "/dashboard", "/ledger", "#digital-exam", "#pre-high-english",
        "#student-app", "#hitCase", "#counsel", "/testprep", "/wiki", "/csat/2027-english", "/study/",
        "#contact", "#ai-content", "/ai-agent-studio", "#ebook", "#review-game", "#chart-showcase", "/builder"}) {
      assertThat(top).as(link).contains("href=\"" + link + "\"");
    }
    assertThat(html).doesNotContain("class=\"hw-side\"").contains("/js/home_topnav.js");
    assertThat(hero).contains("id=\"service-map\"", "id=\"hwNet\"", "F2R 서비스 지도");
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
    assertThat(css.apply("static/css/home_light.css")).doesNotContain("#contactModal", ".public-contact-option");
    for (String removed : new String[] {"static/css/home_exam.css", "static/css/landing_edunova.css", "static/css/f2r_home_agent.css",
        "static/css/home_workspace.css", "static/css/home_hero.css"}) {
      assertThat(getClass().getClassLoader().getResource(removed)).as(removed).isNull();
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
  @DisplayName("given 홈 when 렌더하면 then 맞춤문제 배너 제목(2026-11-23 삭제)과 문제지·해설지 샘플 섹션은 없으며 적중 사례 다음에 학교별 시험 적중률 블로그가 바로 이어진다")
  void titleLines_andSampleSectionRemoved() {
    // given / when
    String html = render();
    // then
    assertThat(html).doesNotContain("id=\"siHomeTitle\"", "우리 학교 맞춤문제");
    assertThat(html).doesNotContain("id=\"exam-samples\"", "WORKSHEETS &amp; EXPLANATIONS", "문제지와 해설지 살펴보기",
        "공개 샘플은 준비 중", "문제지·해설지 문의");
    String between = html.substring(html.indexOf("id=\"hitCase\""), html.indexOf("id=\"exam-blog\""));
    assertThat(between.split("<section", -1)).hasSize(2);
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 2주·160문항·2만원 맞춤문제 안내는 배너에서도 지웠고(2026-11-23) 영상 아래 반복 문구·버튼이 없다")
  void offer_isShownOnceAtTop() {
    // given / when
    String html = render();
    String offer = html.substring(html.indexOf("id=\"pass-offer\""), html.indexOf("id=\"digital-exam\""));
    String promo = html.substring(html.indexOf("id=\"examPromo\""), html.indexOf("id=\"digital-exam\""));
    // then
    assertThat(offer).doesNotContain("2주 동안 160문항", "주 2회 · 총 4회", "클라우드리뷰 무료신청", "2만원");
    assertThat(promo).doesNotContain("맞춤 문제 문의·신청", "exam-caption\">학교별")
        .contains("id=\"examPromoSummary\" class=\"sr\"");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 레이아웃이 해석되고 맞춤문제(2주 160문항) 안내 없이 서비스 도입 문의 전화·이메일이 있으며 트레일러 아래 AI AGENT 배너(화보)는 없다")
  void hero_showsOfferWithoutAgentBanner() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .doesNotContain("th:block")
        .doesNotContain("th:replace")
        .doesNotContain("/images/ai-agent-banner.png", "/images/ai-agent-banner-m.webp", "en-hero-art", "id=\"enHeroStudio\"")
        .doesNotContain("/images/ai-agent-hero.jpg")
        .doesNotContain("2주 동안 160문항", "/exam-service", "/exam-request")
        .contains("40문항")
        .contains("href=\"tel:02-6324-1453\"")
        .contains("mailto:woorinaesin@naver.com")
        .contains("서비스 도입 문의");
  }

  @Test
  @DisplayName("given 승인 메뉴 when 렌더하면 then 상단 메뉴와 전체 메뉴가 8개 목적지·실제 서비스 링크·공지를 보존하고 측면 메뉴는 없다")
  void menu_topAndFullMenuKeepEveryService() {
    String html = render();
    String top = html.substring(html.indexOf("id=\"enNav\""), html.indexOf("</header>", html.indexOf("id=\"enNav\"")));
    String nav = html.substring(html.indexOf("id=\"homeMenu\""), html.indexOf("</nav>", html.indexOf("id=\"homeMenu\"")));
    assertThat(top).contains("data-home-nav=\"home\"", "Feel to Real", ">서비스 도입 문의<", "전체 메뉴",
        "href=\"#chart-showcase\">빌드잇 차트<", "href=\"/dashboard\">대시보드<",
        "aria-controls=\"homeMenuPanel\"", "aria-expanded=\"false\"").doesNotContain("2주 4회 · 2만원", ">맞춤문제 신청<");
    // 2026-11-21 묶음: Coordinator·대시보드·실증 → 학습 콘텐츠 → 부정행위 방지 → 이용 안내(2026-11-23 맞춤문제 삭제) → 다른 서비스
    String[] keys = {"dashboard", "wiki", "contact", "agent", "game", "chart"};
    int previous = -1;
    for (String key : keys) {
      int position = nav.indexOf("data-home-nav=\"" + key + "\"");
      assertThat(position).as(key).isGreaterThan(previous);
      previous = position;
    }
    assertThat(nav).contains("코디·대시보드·시험·입시", "③ 학습 콘텐츠", "④ 부정행위 방지", "이용 안내", "서비스 도입 문의", "다른 서비스",
        "AI AGENT 제작협업실", "자주 묻는 질문", "환불·청약철회 정책", "New iBT", "href=\"#exam-security\"", "href=\"/csat/2027-english\"");
    assertThat(nav).doesNotContain("지문 복습 문제", "문법 문제은행");
    for (String link : new String[] {"/testprep", "/wiki", "/ai-agent-studio", "/dashboard", "/ledger", "/builder", "/chart"})
      assertThat(html).contains("href=\"" + link + "\"");
    assertThat(html).contains("<div class=\"hl-menu\" id=\"homeMenuPanel\" hidden>", "role=\"dialog\" aria-modal=\"true\"",
        "id=\"homeMenuClose\"", "압구정중3 기출문제 적중률 분석 리포트", "모의고사 위키 지문 복습 문제 업로드",
        "2026 고1 고2 2학기말 대비 실전예상문제 1~5회 업로드", "언북중3 기출문제 적중률 보고서 업로드(95.2%)",
        "id=\"homeNoticePause\"", "/css/home_light.css", "/js/home_workspace.js", "/js/home_hero.js", "/js/home_motion.js");
    assertThat(html).doesNotContain("id=\"homeSidebar\"", "id=\"enDrawer\"", "class=\"en-menu\"", "/css/home_workspace.css");
  }

  @Test
  @DisplayName("given 첫 화면 when 렌더하면 then 배너는 디지털 영어 기말고사 1장(2026-11-23 위키·게임·AI AGENT·전자책·맞춤문제 장과 타일 삭제)·일시정지와 오른쪽 홍보 영상이 있고 실제 화면 이미지가 저장소에 있다")
  void hero_tilesPromoAndRealImages() {
    String html = render();
    String first = html.substring(html.indexOf("id=\"pass-offer\""), html.indexOf("id=\"digital-exam\""));
    String hero = first.substring(first.indexOf("id=\"homeHero\""), first.indexOf("class=\"hl-promo"));
    String promo = first.substring(first.indexOf("class=\"hl-promo"));
    assertThat(hero.split("data-hero-slide", -1)).hasSize(2);
    assertThat(hero).doesNotContain("data-hero-go=", "class=\"hl-tiles\"");
    // 배너: 디지털 영어 기말고사(/naesintest) 한 장
    assertThat(hero).contains("aria-label=\"1 / 1 디지털 영어 기말고사\"", "href=\"/naesintest\"");
    // 2026-11-23 대표 지시: 위키·복습 게임·AI AGENT·전자책 배너 장, 이어서 맞춤문제 장은 지운다(앞의 넷은 각 구역이 아래에 그대로)
    assertThat(hero).doesNotContain("hero-wiki", "hero-game", "hero-agent", "hero-ebook", "새출발 2억2천 탕감", "hero-exam", ">맞춤문제 신청<", "mock_q13.jpg");
    assertThat(hero).contains("data-active=\"0\"", "id=\"heroPause\"", "id=\"heroStatus\"");
    // 2026-11-19 사용자 지시: 영상 아래 카드는 2027 수능 영어 해설 모션만 둔다(맞춤문제 2주 4회 160문항 안내·신청 버튼 삭제).
    assertThat(promo).contains("id=\"examPromo\"", "id=\"examPromoVideo\"", "id=\"csatHome\"", "2027 수능 영어 해설");
    String csatCard = promo.substring(promo.indexOf("id=\"csatHome\""));
    assertThat(csatCard).doesNotContain("서수니 · 세븐", "id=\"homeIntakeCta\"", "2주 4회 160문항", "클라우드리뷰 무료신청");
    for (String path : new String[] {
        "/img/landing/home-3d-target.webp",
        "/img/landing/home-3d-chart.webp", "/img/exam-cases/apgujeong-g3-2026-final/exam_q13.jpg",
        "/img/landing/home-ebook-hero.webp"}) {
      // 그림을 고화질로 바꾸면 캐시를 피하려고 ?v= 를 붙이므로 경로 앞부분까지만 확인한다.
      assertThat(html).as(path).contains("src=\"" + path);
      assertThat(getClass().getResource("/static" + path)).as(path).isNotNull();
    }
    assertThat(html).doesNotContain("wiki-sample.jpg");
  }

  @Test
  @DisplayName("given 배너 아래 when 렌더하면 then 서수니 상담사 사진과 함께 공개 입시상담 AI(/admissions/guide)로 가는 버튼이 있다")
  void admissionsButton_belowHero() {
    String html = render();
    String button = html.substring(html.indexOf("class=\"hl-admissions"), html.indexOf("id=\"contact\""));
    assertThat(html.indexOf("class=\"hl-admissions")).isGreaterThan(html.indexOf("id=\"examPromo\""));
    assertThat(button).contains("href=\"/admissions/guide\"", "home-admissions-suni.webp", "입시상담 AI", "서수니 입시상담사");
    assertThat(getClass().getResource("/static/img/landing/home-admissions-suni.webp")).isNotNull();
  }

  @Test
  @DisplayName("given 입시상담 AI 칸 when 렌더하면 then 「바뀌는 입시」 제목 목록 자리가 칸 안에 있고(처음엔 숨김), 공개 글 목록을 최신 게시일 먼저(같은 날은 회차 순) 읽어 제목을 글 주소로 잇고, 카드 문구는 2028학년도 안내다")
  void admissionsCard_listsChangingAdmissionsArticles() throws Exception {
    // given
    String html = render();
    String card = html.substring(html.indexOf("class=\"hl-admissions"), html.indexOf("id=\"contact\""));
    String js = new String(getClass().getResourceAsStream("/static/js/home_admissions_articles.js").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    // when / then
    assertThat(card).contains("id=\"hlAdmissionsArticles\" hidden", "id=\"hlAdmissionsArticleList\"", "href=\"/admissions/articles\"", "바뀌는 입시");
    assertThat(html).doesNotContain("<a class=\"hl-admissions hl-lift\""); // 칸 전체가 링크면 제목 링크를 안에 둘 수 없다
    assertThat(html).contains("/js/admissions_articles_view.js", "/js/home_admissions_articles.js");
    assertThat(js).contains("/api/admissions/articles", "V.seriesItems(list, 10)", "a.href = it.href", "V.episodeParts(it.episode)");
    assertThat(card).contains("2028학년도 변경된 입시안을 소개하고 상담합니다.").doesNotContain("학년도별로 안내합니다");
  }

  @Test
  @DisplayName("given 화면을 따라 움직이는 바로가기 when 렌더하면 then 서비스 도입 문의 버튼만 있고(2026-11-23 맞춤문제 신청에서 바꿈) 내린 AI 비서 링크는 없다")
  void floatingShortcut_isAssistantAndApply() {
    String html = render();
    String fab = html.substring(html.indexOf("class=\"hl-fab\""), html.indexOf("</div>", html.indexOf("class=\"hl-fab\"")));
    assertThat(fab).contains("href=\"#contact\">서비스 도입 문의<").doesNotContain("href=\"#aiHome\"", "AI 비서", "맞춤문제");
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
  @DisplayName("given 홈 when 읽으면 then 주요 서비스 영역이 있고 맞춤문제 안내(2026-11-23 삭제)와 완료 표현이 없다")
  void intro_and_notice() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .doesNotContain("클라우드리뷰 무료신청", "우리 학교 맞춤문제") // 2026-11-23 맞춤문제 삭제
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
    int exam = html.indexOf("id=\"contact\"");
    int ai = html.indexOf("id=\"ai-content\"");
    assertThat(exam).isPositive();
    assertThat(ai).isGreaterThan(exam);
    assertThat(html).contains("콘텐츠 제작협업실").doesNotContain("EduNova").doesNotContain("EDUNOVA").contains("BuildIt").doesNotContain("GPU 에듀테인먼트").doesNotContain("AI AGENT 소개 보기");
    assertThat(html).doesNotContain("href=\"#\"");
    assertThat(html).contains("href=\"/dashboard\"").contains("href=\"/ledger\"").contains("href=\"/builder\"");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 복구한 적중 성과가 근거 문구·에듀홀릭 블로그 원문 링크와 함께 서비스 도입 문의 앞에 있다")
  void restoredHitProof_linksToBlog() {
    // given / when
    String html = render();
    // then
    assertThat(html)
        .doesNotContain("압구정에듀홀릭 활용 사례", "id=\"exam-hit\"")
        .contains("학교별 시험 적중률 블로그", "href=\"https://blog.naver.com/apgujungeduholic/224463820746\"")
        .contains("개별 사례는 다른 학교나 시험의 적중률을 보장하지 않습니다.")
        .doesNotContain("실제 적중률 100%")
        .contains("href=\"https://blog.naver.com/apgujungeduholic/224435388122\"");
    String[] sections = {"pass-offer", "examPromo", "hitCase", "exam-blog", "contact", "ai-content", "ebook", "review-game", "chart-showcase"};
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
    // 전체 메뉴(PC·휴대폰 공통)와 AI 드라마 제작 카드에서 들어간다. 권한은 서버가 확인한다.
    String menu = html.substring(html.indexOf("id=\"homeMenu\""), html.indexOf("</nav>", html.indexOf("id=\"homeMenu\"")));
    String drama = html.substring(html.indexOf("id=\"ai-content\""), html.indexOf("id=\"review-game\""));
    assertThat(menu).contains("data-home-nav=\"agent\"", "href=\"/ai-agent-studio\"", ">AI AGENT 제작협업실<");
    assertThat(drama).contains("href=\"/ai-agent-studio\" data-studio-link", ">AI AGENT 제작협업실<");
    assertThat(html).doesNotContain("data-studio-entry hidden");
  }

  @Test
  @DisplayName("given 홈 when 읽으면 then 맞춤문제 신청 칸 안에 있던 내신 제작소 링크도 2026-11-23 맞춤문제 삭제와 함께 홈에서 빠졌다")
  void naesinStudioEntry_removedWithCustomProblemCard() {
    // given / when
    String html = render();
    // then
    assertThat(html).doesNotContain("data-home-nav=\"request\"", "href=\"#request\"", "id=\"request\"",
        "<summary>내신 제작소</summary>", "/naesin-production-studio");
  }

  @Test
  @DisplayName("given 2026-11-23 맞춤문제 삭제 when 홈을 그리면 then 이용권(2주·160문항·20,000원) 판매 문구와 옛 문구가 없고 SEO 제목은 교습소·내신 검색어를 담는다")
  void currentOffer_and_renderedEvidence() throws Exception {
    String html = render();
    assertThat(html).doesNotContain("2주 동안 160문항", "20,000원", "총 4회", "회당 <span>40</span>문항",
        "3주", "240문항", "총 6회", "30,000원", "1회 20,000원", "맞춤 문제");
    assertThat(html).contains("<title>F2R AI 코디 · F2R 영어 교습소 | 학원 업무 AI · 압구정중·신구중 영어 내신</title>");
    // 이용권 판(data-offer-version)은 내린 홈 AI 비서 칸에만 있었다. 공개 문구는 위 숫자로 확인한다.
    assertThat(html).contains("id=\"examPromo\"").doesNotContain("src=\"\"");
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
  @DisplayName("given 2026-11-23 맞춤문제 내림 when FAQ를 그리면 then 이용권(20,000원·160문항·총 4회)·의뢰 질문·이용·환불 안내가 없고 서비스 도입 문의로 잇는다")
  void givenCustomProblemTakenDown_whenFaqRendered_thenNoOfferAndLinksToContact() {
    // given / when
    String faq = render("public/faq");
    // then
    assertThat(faq).doesNotContain("20,000원", "160문항", "총 4회", "예상문제는 어떻게 의뢰하나요", "id=\"exam-service-terms\"", "#exam-contact")
        .contains("href=\"/#contact\"", "학원 운영 대시보드는 무엇인가요?", "입시상담 AI는 무엇인가요?", "모의고사위키는 무엇인가요?");
    // 옛 맞춤문제 판매 화면(/exam) 템플릿은 지웠다(주소는 홈 서비스 도입 문의로 보낸다).
    assertThat(getClass().getResource("/templates/public/exam.html")).isNull();
  }


  @Test
  @DisplayName("given 계좌 공개 값이 있어도 when 홈을 그리면 then 맞춤문제 이용료·무통장입금 안내(2026-11-23 삭제)와 계좌는 홈에 나오지 않는다")
  void bankTransfer_isNotShownOnHome() throws Exception {
    // given
    var bank = f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeBankTransfer.CURRENT;
    // when
    String shown = render("public/landing", LandingPageController.DEFAULT_PROMO_PATH,
        LandingPageController.DEFAULT_REVIEW_GAME_URL, "", f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.HomeReviewGameController.PAGE_PATH, bank);
    // then
    assertThat(shown).doesNotContain("id=\"exam-payment\"", "이용료와 무통장입금 안내", bank.accountNumber(), "입금 계좌는 문의해 주시면 안내합니다");
    String source = new String(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/templates/public/landing.html"))
        .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(source).doesNotContain("homeBankTransfer", "examOffer");
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
  @DisplayName("given 옛 맞춤문제 판매 화면 주소(/exam) when 열면 then 홈의 서비스 도입 문의로 보낸다(2026-11-23 맞춤문제 내림)")
  void examPage_redirectsToContact() {
    // given
    var controller = new f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.ExamPageController();
    // when
    String view = controller.exam();
    // then
    assertThat(view).isEqualTo("redirect:/#contact");
  }

  @Test
  @DisplayName("given 홈 when 렌더하면 then 압구정중3 적중 사례가 실증 근거 영역(디지털 기말내신 아래, 입시상담 AI 위)에 있고 항목을 누르면 펼쳐지는 구조이며 이미지가 모두 있다")
  void hitCase_sitsInProofAreaBelowDigitalExam_andExpandsInPlace() {
    // given
    String html = render();
    int drama = html.indexOf("id=\"dexam-guide\"");
    int hit = html.indexOf("id=\"hitCase\"");
    int payment = html.indexOf("id=\"exam-blog\"");
    // when
    String section = html.substring(hit, payment);
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"(/img/exam-cases/[^\"]+)\"").matcher(section);
    java.util.Set<String> images = new java.util.LinkedHashSet<>();
    while (m.find()) images.add(m.group(1));
    // then
    assertThat(hit).isGreaterThan(drama).isLessThan(payment);
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

  @Test
  @DisplayName("given 첫 화면 when 렌더하면 then 디지털 영어 기말고사 섹션이 영어 교육보다 앞에 있고 /naesintest로 이어진다")
  void digitalExamSectionLinksToNaesintest() {
    // given
    String html = render();
    // when
    String sec = html.substring(html.indexOf("id=\"digital-exam\""), html.indexOf("id=\"contact\""));
    // then
    assertThat(html.indexOf("id=\"digital-exam\"")).isLessThan(html.indexOf("id=\"contact\""));
    assertThat(sec).contains("디지털 영어 기말고사", "직보 쇼츠 인강", "40문항 20분", "70문항 35분", "자동 채점", "href=\"/naesintest\"",
        "적중률 근거", "압구정중2 93.3%", "압구정중3 93.5%", "언북중3 95.2%", "신구중3 96.8%");
    assertThat(sec).contains("예비고1 디지털 수능 영어 모의고사", "고1 수능 대비 어휘 모의고사",
        "href=\"/naesintest/pre-high\"").doesNotContain("hwChoices", "hwExamTime");
    int preHighStart = sec.indexOf("id=\"pre-high-english\"");
    String preHigh = sec.substring(preHighStart, sec.indexOf("</section>", preHighStart));
    assertThat(preHigh).contains("<div class=\"hl-head\"><h2 id=\"preHighTitle\">", "hw-paper hw-tbl",
        "href=\"/naesintest/pre-high?part=1\"", "href=\"/naesintest/pre-high?part=2\"",
        "href=\"/naesintest/pre-high?part=3\"", "href=\"/naesintest/pre-high?part=4\"")
        .doesNotContain("독해 6문항에 9분");
  }

  @Test
  @DisplayName("given 첫 화면 when 학교별 적중률 블로그를 보면 then 압구정중2 93.3% 막대와 산출 기준, 정확한 블로그 수치가 보인다")
  void examBlogShowsHitRateBasis() {
    // given
    String html = render();
    // when
    String sec = html.substring(html.indexOf("id=\"exam-blog\""), html.indexOf("</section>", html.indexOf("id=\"exam-blog\"")));
    // then
    assertThat(sec).contains("<span>압구정중2</span>", "--w:93.3%", "<em>93.3%</em>")
        .contains("적중률 = 예상문제와 같거나 비슷하게 나온 문항 ÷ 기준 문항", "기준 문항은 학교마다 다릅니다", "압구정중2 28/30(시험 전체)",
            "압구정중3 29/31(대화문 5문항 뺀 예상문제 범위, 36문항 전체 80.6%)", "언북중3 20/21(대화문 5문항 뺀 예상문제 범위)", "신구중3 30/31(시험 전체)")
        .contains("신구중3 적중률 96.8% 블로그", "압구정중2 2학기 중간 적중률 93.3% 블로그", "언북중3 2학기말 적중률 95.2% 블로그", "--w:95.2%")
        .doesNotContain("신구중3 적중률 96% 블로그");
  }

  @Test
  @DisplayName("given 최근 기사 3건 when 홈을 그리면 then 부정행위 방지 영역에 날짜·매체·원문 링크(새 창)와 기사 전체 보기가 있고, 기사가 없으면 칸이 숨는다")
  void givenLatestSecurityNews_whenRendered_thenNewsListShowsWithOriginalLinks() {
    // given
    var news = java.util.List.of(
        new f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.ExamSecurityLibraryController.Entry(
            "기사", "경비지도사 시험 부정행위 적발", "헤럴드경제", "2026-11-22", "s", null, "https://biz.heraldcorp.com/article/10944902", "원문"));
    // when
    String html = renderWithNews(news);
    String security = html.substring(html.indexOf("id=\"exam-security\""), html.indexOf("id=\"contact\""));
    // then
    assertThat(security).contains("최근 기사", "2026-11-22", "헤럴드경제", "경비지도사 시험 부정행위 적발",
        "href=\"https://biz.heraldcorp.com/article/10944902\" target=\"_blank\" rel=\"noopener\"", "기사 전체 보기", "href=\"/exam-security\"");
    assertThat(render()).doesNotContain("secNewsTitle");
  }

  private static String renderWithNews(Object news) {
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
    context.setVariable("examPromoPath", LandingPageController.DEFAULT_PROMO_PATH);
    context.setVariable("homeReviewGameUrl", LandingPageController.DEFAULT_REVIEW_GAME_URL);
    context.setVariable("homeAgentTrailerPath", LandingPageController.DEFAULT_AGENT_TRAILER_PATH);
    context.setVariable("examSecurityNews", news);
    return engine.process("public/landing", context);
  }
}
