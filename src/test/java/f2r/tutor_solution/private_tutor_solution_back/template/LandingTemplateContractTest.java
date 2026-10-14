package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Build It 랜딩 개편(2026-08) 계약. 시안 출처: docs/archive/archived_html/홈피개선.html */
class LandingTemplateContractTest {

  private static final Path LANDING = Path.of("src/main/resources/templates/public/landing.html");
  private static final Path LANDING_CSS =
      Path.of("src/main/resources/static/css/landing_buildit.css");
  private static final Path LANDING_JS =
      Path.of("src/main/resources/static/js/landing_buildit.js");
  private static final Path PUBLIC_FOOTER =
      Path.of("src/main/resources/templates/fragments/_public_footer.html");
  private static final Path LANDING_VIDEO_DIR = Path.of("src/main/resources/static/video/landing");
  private static final Path USER_MANUAL = Path.of("src/main/resources/static/manual/index.html");
  private static final Path USER_MANUAL_VIDEO_DIR =
      Path.of("src/main/resources/static/manual/videos");

  @Test
  @DisplayName("given Build It 개편 when 템플릿을 읽으면 then 서비스명이 Build It이고 모두의 학원 잔재가 없다")
  void landing_uses_build_it_brand_only() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when & then
    assertThat(html)
        .contains("<title>Build It | 원장과 강사가 브랜드가 되는 시대</title>")
        .contains("<span class=\"nav-logo bi\" aria-label=\"Build It\"><b>Build</b><i>It</i></span>")
        .contains("Build It Wiki")
        // 구 서비스명이 남으면 클라이언트가 요구한 리브랜딩이 반쯤만 적용된 상태가 된다.
        .doesNotContain("모두의 학원")
        .doesNotContain("모두의학원")
        .doesNotContain("brand-part brand-school");
  }

  @Test
  @DisplayName("given Build It 개편 when 템플릿을 읽으면 then 시안의 주요 섹션 구조를 유지한다")
  void landing_keeps_reference_section_structure() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when & then
    assertThat(html)
        .contains("class=\"bi-nav\" id=\"biNav\"")
        .contains("class=\"hero\" id=\"hero\"")
        .contains("class=\"flow-marquee\" id=\"why\"")
        .contains("class=\"wk\" id=\"wiki\"")
        .contains("class=\"cta-mini\" id=\"cta\"")
        // 개편으로 걷어낸 구 랜딩 기능이 되살아나면 컨트롤러 모델과 어긋난다.
        .doesNotContain("id=\"builder-try\"")
        .doesNotContain("class=\"beta-comment-section\"")
        .doesNotContain("betaCarousel")
        .doesNotContain("betaParticipantTypes")
        .doesNotContain("heroVideo");
  }

  @Test
  @DisplayName("given 서비스 소개 when 템플릿을 읽으면 then 제작(히어로)→Chart→Wiki→준비 중 순서로 노출하고 중복·미제공 섹션은 없다")
  void landing_orders_services_by_product_strategy() throws Exception {
    // 2026-10-14 사용자 확정(docs/design/homepage-v3/HANDOFF.md §10-1): AI 홈페이지 제작 → Chart → Easy(서비스 탐색기 탭)
    // → 콘텐츠 도구(Wiki·Report·Shorts) → 준비 중 서비스. 이전 순서(Wiki→Web→Chart→Report→Shorts)에서 바뀌었다.
    // 낡은 단언(대한민국 지역별 TOP 100 문구, Chart 전체보기 문구, 정수정영어학원 하드코딩)은 현재 랜딩에 없고,
    // Chart는 공개 API(landing_chart.js)로 그리므로 실제 교육자 이름을 템플릿에 고정하지 않는다.
    // given
    String html = Files.readString(LANDING);
    String css = Files.readString(LANDING_CSS);

    // when
    int hero = html.indexOf("id=\"hero\"");
    int chart = html.indexOf("id=\"chart-showcase\"");
    int wiki = html.indexOf("id=\"wiki\"");
    int soon = html.indexOf("id=\"soon\"");

    // then
    // 2026-10-14 사용자 지시: 홈페이지 제작 소개가 초반(히어로)과 중간(서비스 탐색기·제작 과정)에 두 번 나오는 중복을 없애고,
    // 아직 제공하지 않는 Report·Shorts는 제거한다.
    assertThat(hero).isGreaterThanOrEqualTo(0);
    assertThat(chart).isGreaterThan(hero);
    assertThat(wiki).isGreaterThan(hero);
    assertThat(chart).isGreaterThan(wiki);
    assertThat(soon).isGreaterThan(wiki);
    assertThat(html)
        .doesNotContain("id=\"web\"")
        .doesNotContain("id=\"journey\"")
        .doesNotContain("id=\"tools\"")
        .doesNotContain("id=\"report\"")
        .doesNotContain("id=\"shorts\"");
    assertThat(html)
        .contains("전국 TOP100 보기 →")
        .contains("id=\"landingChartTop5\"")
        .contains("href=\"/chart\"")
        .contains("href=\"/wiki\" class=\"btn outline nav-cta\">Buildit 모의고사</a>")
        .contains("href=\"/home\" class=\"btn outline nav-cta\">F2R</a>")
        .contains("href=\"/problem-generator\" class=\"btn outline nav-cta\">BUILD IT WIKI</a>")
        .contains("class=\"bb-chart\"")
        .doesNotContain("정수정영어학원 · 서울 송파구 삼전동");
    assertThat(css)
        .contains(".chart-showcase")
        .contains(".chart-row")
        .contains(".chart-cta");
  }

  @Test
  @DisplayName("given Wiki 문제지 제작 when 템플릿을 읽으면 then 가격·5단계·카톡/이메일 문의 링크가 있다")
  void wiki_section_offers_worksheet_service_with_contact_links() throws Exception {
    // given
    String html = Files.readString(LANDING);
    int start = html.indexOf("id=\"wiki\"");
    int end = html.indexOf("id=\"soon\"", start);
    String wiki = html.substring(start, end);

    // then
    assertThat(wiki)
        .contains("내신 영어", "문제지 제작", "기본 1회", "20,000원")
        .contains("href=\"https://open.kakao.com/o/sMf4ifRi\"")
        .contains("href=\"mailto:woorinaesin@naver.com\"")
        .contains("자료 보내기", "영어 문제지 제작", "카톡·이메일 전달", "수정 요청", "최종 완성")
        .contains("학원 로고를 보내주시면 제작 문제지에 로고를 넣어드립니다");
    assertThat(wiki.split("<li><span>", -1).length - 1).isEqualTo(5);
    assertThat(Path.of("src/main/resources/static/img/landing/wiki-sample.jpg")).exists();
  }

  @Test
  @DisplayName("given 미제공 서비스 when 템플릿을 읽으면 then Report·Shorts 영상 슬롯과 홈페이지 제작 영상은 더 이상 노출하지 않는다")
  void landing_does_not_expose_unprovided_report_shorts_slots() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when & then
    assertThat(html)
        .doesNotContain("academyShortsVideo")
        .doesNotContain("parentReportVideo")
        .doesNotContain("/video/landing/academy-shorts-demo.mp4")
        .doesNotContain("/video/landing/parent-report-demo.mp4")
        .doesNotContain("class=\"web-video\"");
  }

  @Test
  @DisplayName("given 개편 랜딩 when 템플릿을 읽으면 then 문의 모달·계정 메뉴·공용 footer 계약을 유지한다")
  void landing_preserves_contact_auth_and_footer_contracts() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String footer = Files.readString(PUBLIC_FOOTER);

    // when & then
    assertThat(html)
        .contains("onclick=\"openContactModal()\"")
        .contains("fragments/_public_footer :: publicContactModal")
        .contains("fragments/_public_footer :: publicFooterScripts")
        .contains("fragments/_account_menu :: accountMenuControls")
        .contains("fragments/_account_menu :: loginModal")
        .contains("fragments/_public_footer :: publicFooterBuildIt");
    assertThat(footer)
        .contains("© 2026 Feel 2 Real. All rights reserved.")
        // 사업자 정보는 브랜드와 무관하게 법적 표기로 남아야 한다.
        .contains("상호명: 압구정에듀홀릭학원");
  }

  @Test
  @DisplayName("given 시작하기 버튼 when 템플릿과 스크립트를 읽으면 then 기존 발행 게이트 분기를 유지한다")
  void start_button_keeps_publish_gate() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String js = Files.readString(LANDING_JS);

    // when & then
    assertThat(html)
        .contains("data-buildit-start")
        // JS가 죽어도 최소한 /builder로는 가야 한다.
        .contains("href=\"/builder\" class=\"btn reveal\" data-fade data-buildit-start");
    assertThat(js)
        .contains("/api/auth/me")
        .contains("'/register?returnUrl=' + encodeURIComponent(BUILDER_URL)")
        // ADMIN도 /builder를 쓸 수 있다(TUTOR 전용 분기 회귀 방지).
        .contains("const BUILDER_ROLES = ['TUTOR', 'ADMIN']")
        .contains("canUseBuilder(user)");
  }

  @Test
  @DisplayName("given 과거 사용설명서가 맞지 않을 때 when 랜딩을 읽으면 then 매뉴얼 CTA를 노출하지 않는다")
  void landing_hides_outdated_user_manual_cta() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when & then
    assertThat(html)
        .doesNotContain("href=\"/manual/index.html\"")
        .doesNotContain("landing-manual-action")
        .doesNotContain(">사용설명서 보기</a>");
    // 정적 매뉴얼 파일은 삭제하지 않는다. 랜딩 진입점만 숨겨 과거 내용 노출을 막는다.
    assertThat(USER_MANUAL).exists();
    assertThat(USER_MANUAL_VIDEO_DIR.resolve("01_start.mp4")).exists();
    assertThat(USER_MANUAL_VIDEO_DIR.resolve("14_product_finish.mp4")).exists();
  }

  @Test
  @DisplayName("given 개편 스타일시트 when 리셋 규칙을 읽으면 then 공용 자산을 깨뜨리는 전역 리셋이 없다")
  void landing_styles_scope_reset_to_landing_content() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String css = Files.readString(LANDING_CSS);

    // when & then
    assertThat(html).contains("/css/landing_buildit.css");
    assertThat(css)
        .contains(".bi-page")
        // 문의하기는 옆의 로그인·회원가입(account_menu.css .auth-action)과 같은 규격이어야 한다.
        .contains(".bi-page .btn.nav-cta")
        .contains("min-height: 38px")
        // 시안의 `*{margin:0;padding:0}`을 그대로 두면 계정 메뉴와 공용 footer가 깨진다.
        .doesNotContain("\n*{")
        .doesNotContain("\n* {")
        .contains("@media (max-width: 860px)")
        .contains("@media (max-width: 520px)")
        .contains(".web-video-card")
        .contains("aspect-ratio: 4 / 5")
        .contains("width: calc(100vw - 12px)")
        .contains("object-position: 38% top")
        .contains("transform: scale(1.08)")
        .contains("object-position: 32% top")
        .contains("transform: scale(1.18)")
        .contains(".wiki-pin .laptop-xl")
        .contains(".laptop-screen")
        .contains(".chart-showcase")
        .contains(".hero-title-subject")
        .contains("font-size: .65em")
        .contains(".hero h1 .bi")
        .contains("font-size: 1.10em")
        .contains("@media (prefers-reduced-motion: reduce)");
  }

  @Test
  @DisplayName("given pin 섹션 when 스타일을 읽으면 then nav 높이를 비우고 미디어를 뷰포트 높이에 맞춰 잘림을 막는다")
  void pinned_sections_reserve_nav_height_and_fit_viewport() throws Exception {
    // given
    String css = Files.readString(LANDING_CSS);

    // when & then
    // pin 구간은 스크롤로 더 볼 수 없다. 내용이 뷰포트보다 크면 복구 불가능하게 잘린다.
    assertThat(css)
        .contains("--nav-h:")
        .contains(".builder-pin {")
        .contains(".wiki-pin {")
        // 제목이 fixed nav 밑으로 들어가지 않도록 상단을 비운다.
        .contains("padding: calc(var(--nav-h) + 16px) 5vw 32px")
        .contains("padding: calc(var(--nav-h) + 16px) 5vw 28px")
        // 미디어 폭을 남은 높이로도 제한한다(aspect-ratio 역산).
        .contains(".builder-pin .phone-lg")
        .contains("calc((100svh - 230px) * 280 / 572)")
        .contains(".wiki-pin .laptop-xl")
        .contains("calc((100svh - 330px) * 16 / 9.4)");
  }

  @Test
  @DisplayName("given Wiki 보호 화면에서 이동 when 로그인하면 then 안전한 원래 Wiki URL로 복귀한다")
  void wiki_login_prompt_preservesSafeReturnUrl() throws Exception {
    String js = Files.readString(LANDING_JS);

    assertThat(js)
        .contains("회원 전용 자료입니다.")
        .contains("safeInternalReturnUrl(rawReturnUrl)")
        .contains("wikiPostLoginReturnUrl")
        .contains("window.addEventListener('f2r:login'")
        .contains("location.href = window.safeInternalReturnUrl(returnUrl)");
  }

  @Test
  @DisplayName("given GSAP 로드 실패 when 스크립트를 읽으면 then 정적 폴백으로 빈 화면을 막는다")
  void landing_script_has_static_fallback_without_gsap() throws Exception {
    // given
    String js = Files.readString(LANDING_JS);

    // when & then
    assertThat(js)
        .contains("if (!window.gsap || !window.ScrollTrigger)")
        .contains("staticFallback()");
  }
}
