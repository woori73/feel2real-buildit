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
  @DisplayName("given 중복 제거 when 템플릿을 읽으면 then 제작(히어로)→Chart→Wiki→준비 중 순서이고 제작 재소개·Report·Shorts 섹션은 없다")
  void sections_follow_confirmed_order() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when
    int hero = html.indexOf("id=\"hero\"");
    int intro = html.indexOf("id=\"intro\"");
    int chart = html.indexOf("id=\"chart-showcase\"");
    int wiki = html.indexOf("id=\"wiki\"");
    int soon = html.indexOf("id=\"soon\"");
    int faq = html.indexOf("id=\"faq\"");
    int cta = html.indexOf("id=\"cta\"");

    // then
    assertThat(hero).isGreaterThanOrEqualTo(0);
    assertThat(intro).isGreaterThan(hero);
    assertThat(chart).isGreaterThan(intro);
    assertThat(wiki).isGreaterThan(chart);
    assertThat(soon).isGreaterThan(wiki);
    assertThat(faq).isGreaterThan(soon);
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
  @DisplayName("given 상단 내비 when 읽으면 then 모의고사·F2R·CHART·WIKI·문의하기와 계정 메뉴를 유지한다")
  void nav_keeps_service_links() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // then
    assertThat(html)
        .contains("href=\"/wiki\" class=\"btn outline nav-cta\">Buildit 모의고사</a>")
        .contains("href=\"/home\" class=\"btn outline nav-cta\">F2R</a>")
        .contains("href=\"/chart\" class=\"btn outline nav-cta\">BUILD IT CHART</a>")
        .contains("href=\"/problem-generator\" class=\"btn outline nav-cta\">BUILD IT WIKI</a>")
        .contains("onclick=\"openContactModal()\"")
        .contains("fragments/_account_menu :: accountMenuControls")
        .contains("fragments/_account_menu :: loginModal");
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
  @DisplayName("given 소개영상·슬로건 when 읽으면 then 삭제하지 않고 유지한다")
  void intro_video_and_slogan_are_kept() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // then
    assertThat(html)
        .contains("숨은 영어교육자를 발굴하고, 성장시키고, 투자로 연결합니다.")
        .contains("data-youtube-id=\"fK_BdoK8O64\"")
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
        .contains("/js/landing_chart.js")
        .doesNotContain("교육자 A")
        .doesNotContain("교육자 B");
  }

  @Test
  @DisplayName("given 준비 중 서비스 when 읽으면 then 링크·입력창 없이 비활성으로 표시한다")
  void soon_services_are_not_clickable() throws Exception {
    // given
    String html = Files.readString(LANDING);
    int start = html.indexOf("id=\"soon\"");
    int end = html.indexOf("id=\"faq\"", start);
    String soon = html.substring(start, end);

    // then
    assertThat(soon).contains("DesignCraft").contains("입시상담 AI").contains("aria-disabled=\"true\"");
    assertThat(soon).doesNotContain("<a ").doesNotContain("<input").doesNotContain("<form");
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
        .contains("#landingChartTop5:not(.is-open) .chart-row:nth-child(n+4)");
    assertThat(js).contains("chartMoreState").contains("initChartMore");
  }

  @Test
  @DisplayName("given 새 첫 화면 구성 when 템플릿을 읽으면 then 전국 무대 → 디지털영어학원 Agent(홈페이지 Agent) → 제작 입력 순서이고 지역 4개·남한 지도를 쓴다")
  void first_screen_starts_with_nation_then_agent_then_homepage_input() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when
    int nation = html.indexOf("id=\"nation\"");
    int agent = html.indexOf("id=\"agent\"");
    int hero = html.indexOf("id=\"hero\"");

    // then
    assertThat(nation).isGreaterThanOrEqualTo(0);
    assertThat(agent).isGreaterThan(nation);
    assertThat(hero).isGreaterThan(agent);
    assertThat(html)
        .contains("전국 무대로 확장하세요!")
        .contains("디지털영어학원 Agent")
        .contains("홈페이지 Agent")
        .contains("사진과 화면은 예시 이미지입니다.");
    assertThat(html.split("data-nation-region=", -1).length - 1).isEqualTo(4);
    assertThat(Files.readString(V3_CSS)).contains("/img/landing/korea-dots.svg");
  }
}
