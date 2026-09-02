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
        .contains("<span class=\"ext\">Web</span>")
        .contains("<span class=\"ext\">Shorts</span>")
        .contains("<span class=\"ext\">Report</span>")
        .contains("<span class=\"ext\">Wiki</span>")
        .contains("숨은 영어교육자를 발굴하고, 성장시키고, 투자로 연결합니다.")
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
        .contains("class=\"builder\" id=\"web\"")
        .contains("class=\"product-solo\" id=\"shorts\"")
        .contains("class=\"product-solo\" id=\"report\"")
        .contains("class=\"wiki\" id=\"wiki\"")
        .contains("class=\"cta-mini\" id=\"cta\"")
        // 개편으로 걷어낸 구 랜딩 기능이 되살아나면 컨트롤러 모델과 어긋난다.
        .doesNotContain("id=\"builder-try\"")
        .doesNotContain("class=\"beta-comment-section\"")
        .doesNotContain("betaCarousel")
        .doesNotContain("betaParticipantTypes")
        .doesNotContain("heroVideo");
  }

  @Test
  @DisplayName("given 서비스 소개 when 템플릿을 읽으면 then Wiki·Web·Chart·Report·Shorts 순서로 노출한다")
  void landing_orders_services_by_product_strategy() throws Exception {
    // given
    String html = Files.readString(LANDING);
    String css = Files.readString(LANDING_CSS);

    // when
    int hero = html.indexOf("id=\"hero\"");
    int wiki = html.indexOf("id=\"wiki\"");
    int web = html.indexOf("id=\"web\"");
    int chart = html.indexOf("id=\"chart-showcase\"");
    int report = html.indexOf("id=\"report\"");
    int shorts = html.indexOf("id=\"shorts\"");

    // then
    assertThat(hero).isGreaterThanOrEqualTo(0);
    assertThat(wiki).isGreaterThan(hero);
    assertThat(web).isGreaterThan(wiki);
    assertThat(chart).isGreaterThan(web);
    assertThat(report).isGreaterThan(chart);
    assertThat(shorts).isGreaterThan(report);
    assertThat(html)
        .contains("대한민국 지역별 영어교육자 TOP 100")
        .contains("BuildIt Chart 전체보기 →")
        .contains("href=\"/chart\"")
        .contains("href=\"/problem-generator\" class=\"btn outline nav-cta\">BUILD IT WIKI</a>")
        .contains("class=\"chart-phone\"")
        .contains("정수정영어학원 · 서울 송파구 삼전동");
    assertThat(css)
        .contains(".chart-showcase")
        .contains(".chart-phone")
        .contains(".chart-row")
        .contains(".chart-cta");
  }

  @Test
  @DisplayName("given Wiki 3단계 when 템플릿을 읽으면 then 기출 분석·변형문제 생성에 각 영상이 붙고 학교별 Wiki는 대시보드다")
  void wiki_steps_bind_their_own_videos() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when & then
    assertThat(html)
        .contains("<span class=\"flow-h-step\" data-step=\"0\">기출 분석</span>")
        .contains("<span class=\"flow-h-step\" data-step=\"1\">변형문제 생성</span>")
        .contains("<span class=\"flow-h-step\" data-step=\"2\">학교별 Wiki</span>")
        .contains("data-screen=\"0\"")
        .contains("/video/landing/digital_twin_ex.mp4")
        .contains("data-screen=\"1\"")
        .contains("/video/landing/how2use_digitaltwin.mp4")
        // 학교별 Wiki는 아직 영상이 없다. 다른 영상을 돌려쓰면 없는 기능을 있는 것처럼 보여준다.
        .contains("data-screen=\"2\"")
        .contains("class=\"mock-dash-title\">학교별 Wiki<")
        .contains("class=\"mws-new\"");

    // 단계별 영상 파일이 실제로 배포 자산에 있어야 한다.
    assertThat(LANDING_VIDEO_DIR.resolve("digital_twin_ex.mp4")).exists();
    assertThat(LANDING_VIDEO_DIR.resolve("how2use_digitaltwin.mp4")).exists();
  }

  @Test
  @DisplayName("given Build It 영상 슬롯 when 템플릿을 읽으면 then Shorts/Report가 각 관리형 source를 쓴다")
  void product_videos_use_their_managed_slots() throws Exception {
    // given
    String html = Files.readString(LANDING);

    // when & then
    assertThat(html)
        // Web 섹션은 매뉴얼의 가로 홈페이지 제작 영상을 정적 경로로 쓴다.
        .contains("/video/landing/buildit-web-builder-highlight.mp4")
        .contains("class=\"web-video\"")
        .contains("muted loop autoplay playsinline preload=\"metadata\"")
        .doesNotContain("id=\"builderVideo\"")
        // Shorts 섹션은 기존 학원 홍보 쇼츠 슬롯을 쓴다.
        .contains("th:if=\"${academyShortsVideo.visible}\"")
        .contains("th:src=\"${academyShortsVideo.sourceUrl}\"")
        .contains("th:unless=\"${academyShortsVideo.visible}\"")
        .contains("/video/landing/academy-shorts-demo.mp4")
        // Report 섹션은 학부모 리포트 슬롯을 쓴다.
        .contains("th:if=\"${parentReportVideo.visible}\"")
        .contains("th:src=\"${parentReportVideo.sourceUrl}\"")
        .contains("th:unless=\"${parentReportVideo.visible}\"")
        .contains("/video/landing/parent-report-demo.mp4");
    assertThat(LANDING_VIDEO_DIR.resolve("academy-shorts-demo.mp4")).exists();
    assertThat(LANDING_VIDEO_DIR.resolve("parent-report-demo.mp4")).exists();
    assertThat(LANDING_VIDEO_DIR.resolve("buildit-web-builder-highlight.mp4")).exists();
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
        .contains("상호명: 압구정에듀홀릭출판사");
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
        .contains("href=\"/builder\" class=\"btn\" data-buildit-start");
    assertThat(js)
        .contains("/api/auth/me")
        .contains("'/register?returnUrl=' + encodeURIComponent(BUILDER_URL)")
        .contains("user.role === 'TUTOR'");
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
