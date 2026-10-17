package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 랜딩 v3 개편(docs/design/homepage-v3/HANDOFF.md) 계약.
 *
 * <p>기존 LandingTemplateContractTest는 건드리지 않고, v3에서 확정된 순서·링크·유지 동작만 별도로 고정한다.
 */
class LandingV3TemplateContractTest {

  private static final Path LANDING = Path.of("src/main/resources/templates/public/landing.html");
  private static final Path V3_CSS = Path.of("src/main/resources/static/css/landing_v3.css");
  private static final Path V3_JS = Path.of("src/main/resources/static/js/landing_v3.js");

  @Test
  @DisplayName("given 중복 제거 when 템플릿을 읽으면 then 제작(히어로)→Wiki→오픈 서비스→출시 예정→Chart→FAQ→문의 순서이고 제작 재소개·Report·Shorts 섹션은 없다")
  void sections_follow_confirmed_order() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when
    int hero = html.indexOf("id=\"hero\"");
    int chart = html.indexOf("id=\"chart-showcase\"");
    int wiki = html.indexOf("id=\"wiki\"");
    int services = html.indexOf("id=\"services\"");
    int soon = html.indexOf("id=\"soon\"");
    int faq = html.indexOf("id=\"faq\"");
    int cta = html.indexOf("id=\"cta\"");

    // then
    // 2026-10-15 사용자 확정: 디지털영어학원 Agent → 내신 영어 문제지 제작 → 오픈 서비스 전체 → BUILD IT CHART
    assertThat(hero).isGreaterThanOrEqualTo(0);
    assertThat(wiki).isGreaterThan(hero);
    assertThat(services).isGreaterThan(wiki);
    assertThat(soon).isGreaterThan(services);
    assertThat(chart).isGreaterThan(soon);
    assertThat(faq).isGreaterThan(chart);
    assertThat(cta).isGreaterThan(faq);
    assertThat(html)
        .doesNotContain("id=\"web\"")
        .doesNotContain("id=\"journey\"")
        .doesNotContain("id=\"tools\"")
        .doesNotContain("id=\"report\"")
        .doesNotContain("id=\"shorts\"");
  }

  @Test
  @DisplayName("given 시작하기 게이트 when 히어로를 읽으면 then 기존 data-buildit 훅과 예시 칩을 유지한다")
  void hero_keeps_start_gate_hooks() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // then
    assertThat(html)
        .contains("<form class=\"v3-ask\" data-buildit-form")
        .contains("data-buildit-prompt")
        .contains("data-buildit-start")
        .contains("class=\"hero\" id=\"hero\"")
        .doesNotContain("action=\"/builder/entry\"");
  }

  @Test
  @DisplayName("given 상단 내비 when 읽으면 then 내신위키·모의고사위키·기출분석·CHART·TestPrep 5개가 라벨과 목적지가 일치하고 F2R은 없으며 문의하기·계정 메뉴를 유지한다")
  void nav_keeps_service_links() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String nav = html.substring(html.indexOf("<nav class=\"bi-nav\""), html.indexOf("id=\"biMenu\""));

    // then
    assertThat(nav)
        .contains("href=\"/problem-generator\" class=\"btn outline nav-cta\">내신위키</a>")
        .contains("href=\"/wiki\" class=\"btn outline nav-cta\">모의고사위키</a>")
        .contains("href=\"/exam-analysis\" class=\"btn outline nav-cta\">기출분석</a>")
        .contains("href=\"/chart\" class=\"btn outline nav-cta\">CHART</a>")
        .contains("href=\"/testprep\" class=\"btn outline nav-cta\">TestPrep</a>")
        .doesNotContain(">F2R</a>")
        .doesNotContain("/builder")
        .contains("onclick=\"openContactModal()\"")
        .contains("fragments/_account_menu :: accountMenuControls");
    assertThat(html).contains("fragments/_account_menu :: loginModal");
  }

  @Test
  @DisplayName("given 첫 화면 서비스 카드 when 읽으면 then 8장이 확정 순서·링크로 실제 a[href]이고 준비 중 카드 2장만 소개 보기·가이드 보기로 구분된다")
  void first_screen_has_eight_service_tiles_with_confirmed_links() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String tiles = html.substring(html.indexOf("id=\"svcTiles\""), html.indexOf("id=\"nation\""));
    String[][] expected = {
        {"builder", "/builder", "AI 홈페이지 제작", "강사·원장용", "만들어 보기"},
        {"problem", "/exam-analysis", "내신영어 기출분석 + 출제방향 제작", "로그인 후 이용", "분석 시작"},
        {"wiki", "/wiki", "모의고사위키", "무료 열람", "위키 열기"},
        {"chart", "/chart", "BUILD IT CHART", "무료", "랭킹 보기"},
        {"testprep", "/testprep", "iBT TestPrep", "응시는 로그인", "연습 시작"},
        {"ledger", "/ledger", "이지장부", "원장용", "장부 열기"},
        {"designcraft", "/designcraft", "DesignCraft", "준비 중", "소개 보기"},
        {"admissions", "/admissions/guide", "입시상담", "준비 중", "가이드 보기"}};

    // when
    int count = tiles.split("data-svc-tile=", -1).length - 1;

    // then
    assertThat(count).isEqualTo(8);
    int last = -1;
    for (String[] t : expected) {
      int at = tiles.indexOf("href=\"" + t[1] + "\" data-svc-tile=\"" + t[0] + "\"");
      assertThat(at).as(t[0]).isGreaterThan(last);
      last = at;
      String card = tiles.substring(at, tiles.indexOf("</a></li>", at));
      assertThat(card).as(t[0]).contains(t[2], t[3], t[4]);
    }
    assertThat(tiles.split("svc-tile-soon", -1).length - 1).isEqualTo(2);
    assertThat(tiles.split("aria-describedby=\"svcSoonNote\"", -1).length - 1).isEqualTo(2);
    assertThat(tiles).contains("id=\"svcSoonNote\"").doesNotContain("이용 가능").doesNotContain("<button");
  }

  @Test
  @DisplayName("given 첫 화면 개편 when 읽으면 then 입력 폼은 문서에 1개이고 첫 화면(#homeTop)이 #hero보다 앞에 있으며 소식 패널은 정적 HTML이다")
  void first_screen_owns_the_single_homepage_form_and_static_news() throws Exception {
    // given
    String html = Files.readString(LANDING);
    int homeTop = html.indexOf("id=\"homeTop\"");
    int hero = html.indexOf("id=\"hero\"");
    String news = html.substring(html.indexOf("class=\"home-news\""), html.indexOf("id=\"svcTiles\""));

    // then
    assertThat(html.split("<form class=\"v3-ask\"", -1).length - 1).isEqualTo(1);
    assertThat(html.split("data-buildit-prompt", -1).length - 1).isEqualTo(1);
    assertThat(homeTop).isGreaterThan(0).isLessThan(hero);
    assertThat(html.indexOf("<form class=\"v3-ask\"")).isGreaterThan(homeTop).isLessThan(hero);
    assertThat(news).contains("href=\"/ledger\"", "href=\"/designcraft\"", "href=\"/testprep\"", "href=\"/wiki\"", "href=\"/chart\"", "href=\"/exam\"")
        .doesNotContain("th:each").doesNotContain("th:text").doesNotContain("/api/");
  }

  @Test
  @DisplayName("given 모바일 드로어 when 읽으면 then 서비스 8행과 회원가입·문의하기가 있고 로그인 버튼은 중복 배치하지 않는다")
  void mobile_drawer_lists_eight_services_without_duplicate_login_hook() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String menu = html.substring(html.indexOf("id=\"biMenu\""), html.indexOf("id=\"homeFirst\""));

    // then
    for (String href : new String[] {"/problem-generator", "/wiki", "/exam-analysis", "/chart", "/testprep", "/builder", "/ledger", "/designcraft", "/admissions/guide"}) {
      assertThat(menu).as(href).contains("<a href=\"" + href + "\">");
    }
    assertThat(menu).contains("href=\"/register?returnUrl=%2F\"", "onclick=\"openContactModal()\"")
        .doesNotContain("data-auth-login").doesNotContain("/home\"").doesNotContain("/signin");
    assertThat(menu.split("bi-menu-badge", -1).length - 1).isEqualTo(2);
  }

  @Test
  @DisplayName("given 첫 화면 개편 when 읽으면 then 존재하지 않는 경로(/report·/shorts·/consult·/agent·/signin·/lms)를 링크하지 않는다")
  void first_screen_does_not_link_unverified_paths() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String first = html.substring(html.indexOf("id=\"homeFirst\""), html.indexOf("id=\"nation\""));

    // then
    assertThat(first).doesNotContain("href=\"/report", "href=\"/shorts", "href=\"/consult", "href=\"/agent", "href=\"/signin", "href=\"/lms", "href=\"/easy", "href=\"/login");
  }

  @Test
  @DisplayName("given 링크 정정 사항 when 읽으면 then 존재하지 않는 경로와 Easy 직접 링크를 쓰지 않는다")
  void no_dead_links_and_no_easy_link() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // then
    assertThat(html)
        .doesNotContain("/report-demo")
        .doesNotContain("/shorts-demo")
        .doesNotContain("href=\"/login\"")
        .doesNotContain("href=\"/easy");
  }

  @Test
  @DisplayName("given 브랜드 소개 삭제 지시 when 읽으면 then 소개 영상·슬로건 섹션은 없고 글자 흐름 띠만 남는다")
  void brand_intro_is_removed_but_flow_marquee_stays() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // then
    assertThat(html)
        .doesNotContain("id=\"intro\"")
        .doesNotContain("data-youtube-id=\"fK_BdoK8O64\"")
        .contains("class=\"flow-marquee\" id=\"why\"");
  }

  @Test
  @DisplayName("given Chart 실데이터 when 읽으면 then 공개 TOP5 컨테이너와 스크립트를 유지하고 가짜 교육자를 넣지 않는다")
  void chart_keeps_real_top5_widget() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // then
    assertThat(html)
        .contains("id=\"landingChartTop5\"")
        .contains("id=\"landingChartDate\"")
        .contains("id=\"chartRegionPicker\"")
        .contains("BUILDIT CHART 100")
        .contains("/js/landing_chart.js")
        .doesNotContain("교육자 A")
        .doesNotContain("교육자 B");
  }

  @Test
  @DisplayName("given 출시 예정 서비스 when 읽으면 then 오픈 서비스와 분리되어 링크·입력창 없이 비활성으로 표시한다")
  void soon_services_are_not_clickable() throws Exception {
    // given
    String html = Files.readString(LANDING);
    int start = html.indexOf("id=\"soon\"");
    int end = html.indexOf("id=\"chart-showcase\"", start);
    String soon = html.substring(start, end);

    // then
    // iBT TestPrep은 오픈 서비스(#services)로 옮겼고, 출시 예정에는 미배포 4종만 남는다(이지장부는 /ledger로 제공 중).
    assertThat(soon).contains("출시 예정", "DesignCraft", "입시상담 AI", "AcademyCraft", "StudyCraft").contains("aria-disabled=\"true\"");
    assertThat(soon).doesNotContain("iBT TestPrep", "v3-badge-live");
    assertThat(soon.split("v3-badge-soon", -1).length - 1).isEqualTo(4);
    // AcademyCraft(미배포)·StudyCraft(운영 접근 미확인)는 이용 링크를 두지 않고, 옛 명칭 LMS 카드는 없다.
    assertThat(soon).doesNotContain("href=\"/lms", "href=\"/conversation", "href=\"/tutor/conversation", "<h3>LMS");
    assertThat(soon.split("곧 준비중", -1).length - 1).isGreaterThanOrEqualTo(4);
    assertThat(soon).contains("원고 입력 → 자동 편집 → PDF 제작", "학생·강사 관리 → 출결·성적·숙제 → 상담 관리", "AI 대화형 영어학습 → 학습 콘텐츠");
    // DesignCraft만 "곧 준비중" 뱃지를 유지하면서 공개 소개(/designcraft)로 연결한다. 나머지 3종은 링크 없음.
    assertThat(soon.split("<a ", -1).length - 1).isEqualTo(1);
    assertThat(soon).contains("id=\"designcraft-entry\" href=\"/designcraft\"").doesNotContain("<input").doesNotContain("<form");
    assertThat(soon.split("aria-disabled=\"true\"", -1).length - 1).isEqualTo(3);
    // 이지장부는 /ledger로 이미 제공 중(첫 화면 카드 6번)이라 출시 예정에 두지 않는다.
    assertThat(soon).doesNotContain("Easy 장부", "이지장부");
  }

  @Test
  @DisplayName("given 홈페이지 when 템플릿을 읽으면 then 디지털영어학원과 무관한 NewStart·위메이드·충기 크리에이터는 어디에도 노출하지 않는다")
  void landing_never_exposes_non_education_services() throws Exception {
    // 2026-10-15 사용자 확정: 세 서비스는 상단 바로가기·오픈 서비스·출시 예정 어디에도 넣지 않는다(카드·설명·입장 버튼·링크 금지).
    // 서비스 자체의 URL·인증·데이터는 그대로이며 홈페이지에서만 제외한다.
    // given
    String html = Files.readString(LANDING).toLowerCase();

    // then
    assertThat(html)
        .doesNotContain("newstart", "뉴스타트")
        .doesNotContain("wemade", "위메이드")
        .doesNotContain("choonggi", "충기");
    assertThat(html)
        .doesNotContain("href=\"/newstart")
        .doesNotContain("href=\"/wemade")
        .doesNotContain("href=\"/choonggi");
  }

  @Test
  @DisplayName("given 오픈 서비스 영역 when 읽으면 then 최상단 앵커가 각 설명 섹션을 가리키고 설명마다 주요 기능·사용 방법·서비스 입장하기가 있다")
  void open_services_have_anchor_nav_and_enter_buttons() throws Exception {
    // given
    String html = Files.readString(LANDING);
    int start = html.indexOf("id=\"services\"");
    int end = html.indexOf("id=\"soon\"", start);
    String services = html.substring(start, end);
    String[][] expected = {
        {"svc-builder", "AI 홈페이지 제작", "/builder"},
        {"svc-problem", "내신영어 기출분석 + 출제방향 제작", "/exam-analysis"},
        {"svc-wiki", "모의고사위키", "/wiki"},
        {"svc-testprep", "iBT TestPrep", "/testprep"}};

    // then
    int navAt = services.indexOf("class=\"svc-anchors\"");
    int listAt = services.indexOf("class=\"svc-list\"");
    assertThat(navAt).isGreaterThan(0).isLessThan(listAt);
    assertThat(services).contains("<a href=\"#chart-showcase\">BUILD IT CHART</a>");
    for (String[] svc : expected) {
      assertThat(services).as(svc[0]).contains("<a href=\"#" + svc[0] + "\">" + svc[1] + "</a>");
      int cardAt = services.indexOf("id=\"" + svc[0] + "\"");
      int nextAt = services.indexOf("<article", cardAt + 1);
      String card = services.substring(cardAt, nextAt < 0 ? services.length() : nextAt);
      assertThat(card).as(svc[0]).contains("주요 기능", "사용 방법", "서비스 입장하기", "href=\"" + svc[2] + "\"");
    }
    assertThat(services).doesNotContain("곧 준비중").doesNotContain("aria-disabled");
  }

  @Test
  @DisplayName("given 폰트 when 읽으면 then Pretendard를 우선하고 대체 폰트 스택을 갖는다")
  void font_stack_prefers_pretendard_with_fallbacks() throws Exception {
    // given
    String css = Files.readString(V3_CSS);
    String html = Files.readString(LANDING);

    // then
    assertThat(css).contains("\"Pretendard Variable\", Pretendard").contains("\"Noto Sans KR\"").contains("\"Malgun Gothic\"");
    assertThat(html).contains("pretendard").contains("/css/landing_v3.css").contains("/js/landing_v3.js");
  }

  @Test
  @DisplayName("given v3 스타일 when 읽으면 then 전역 리셋 없이 .bi-page 안으로 한정하고 reduced-motion을 지원한다")
  void v3_css_is_scoped_and_accessible() throws Exception {
    // given
    String css = Files.readString(V3_CSS);

    // then
    assertThat(css).contains(".bi-page {").contains("prefers-reduced-motion: reduce");
    assertThat(css).doesNotContain("\n* {").doesNotContain("\n*{");
  }

  @Test
  @DisplayName("given 모바일 압축 when 템플릿·스타일을 읽으면 then Chart 실데이터 훅을 유지하고 3개 우선+펼치기 장치를 둔다")
  void mobile_chart_shows_three_rows_with_expander() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String css = Files.readString(V3_CSS);
    String js = Files.readString(V3_JS);

    // then
    assertThat(html).contains("id=\"landingChartTop5\"").contains("id=\"landingChartDate\"");
    assertThat(html).contains("id=\"landingChartMore\"").contains("aria-controls=\"landingChartTop5\"");
    assertThat(css).contains("@media (max-width: 720px)")
        .contains("#landingChartTop5:not(.is-open) .bb-row:nth-child(n+4)");
    assertThat(js).contains("chartMoreState").contains("initChartMore");
  }

  @Test
  @DisplayName("given 새 첫 화면 구성 when 템플릿을 읽으면 then 서비스 카드·제작 입력이 먼저 오고 전국 무대 → 디지털영어학원 Agent(홈페이지 Agent) → 제작 상세 순서이며 지역 4개·남한 지도를 유지한다")
  void first_screen_puts_services_first_then_nation_agent_hero_detail() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when
    int tiles = html.indexOf("id=\"svcTiles\"");
    int nation = html.indexOf("id=\"nation\"");
    int agent = html.indexOf("id=\"agent\"");
    int hero = html.indexOf("id=\"hero\"");

    // then
    assertThat(tiles).isGreaterThan(0).isLessThan(nation);
    assertThat(agent).isGreaterThan(nation);
    assertThat(hero).isGreaterThan(agent);
    assertThat(html)
        .contains("전국 무대로 확장하세요!")
        .contains("디지털영어학원 Agent")
        .contains("홈페이지 Agent")
        .contains("href=\"#svcTiles\">첫 화면 서비스 목록으로</a>");
    assertThat(html.split("data-nation-region=", -1).length - 1).isEqualTo(4);
    assertThat(html).contains("/img/landing/korea-dots.svg");
  }

  @Test
  @DisplayName("given FAQ·마지막 안내 when 읽으면 then 준비 중 4종·TestPrep 안내와 문제지 안내가 일치하고 금지 문구 없이 '만들기'로 시작한다")
  void faq_and_bottom_cta_follow_confirmed_copy() throws Exception {
    // given
    String html = Files.readString(LANDING);
    int faq = html.indexOf("id=\"faq\"");
    String tail = html.substring(faq);

    // then
    assertThat(tail)
        .contains("내신 영어 문제지는 어떻게 받나요?", "기본 1회 20,000원")
        .contains("DesignCraft, 입시상담 AI, AcademyCraft, StudyCraft는 곧 준비중", "iBT TestPrep은 첫 화면을 누구나 볼 수 있고")
        .contains("홈페이지 제작으로 디지털 영어 학원을 시작하세요!")
        .contains("href=\"/builder\" class=\"btn reveal\" data-fade data-buildit-start")
        .doesNotContain("AI 홈페이지 만들기")
        .doesNotContain("관리자 전용 베타")
        .doesNotContain("이제, 당신의 브랜드를 만들 시간입니다");
  }

  @Test
  @DisplayName("given Agent 컨셉 when 읽으면 then 제목 옆 디지털영어학원 Agent와 섹션별 Agent 이름·병아리 캐릭터가 있고 예시 이미지 문구는 없다")
  void agents_have_names_and_chick_characters_in_each_section() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String css = Files.readString(V3_CSS);

    // then
    assertThat(html.indexOf("디지털영어학원 Agent")).isLessThan(html.indexOf("class=\"nation-stage\""));
    assertThat(html).contains("홈페이지 Agent", "내신 문제 제작 Agent", "Chart Agent");
    assertThat(html.split("<svg class=\"chick ", -1).length - 1).isGreaterThanOrEqualTo(8);
    assertThat(html).doesNotContain("사진과 화면은 예시 이미지입니다.");
    assertThat(css).contains("@keyframes ck-bob", "@keyframes ck-blink", "prefers-reduced-motion");
  }
}
