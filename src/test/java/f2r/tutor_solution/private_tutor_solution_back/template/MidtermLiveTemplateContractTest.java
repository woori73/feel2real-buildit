package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 내신 LIVE 공개 visual·팝업·운영자 화면의 정적 계약. */
class MidtermLiveTemplateContractTest {

  private static final Path ROOT =
      Path.of(System.getProperty("lms.sourceRoot", "src/main/resources"));

  private String read(String rel) throws Exception {
    return Files.readString(ROOT.resolve(rel));
  }

  @Test
  void publicPage_usesLatestWikiSourceAsReadOnlySandboxVisual() throws Exception {
    String html = read("static/eduholic-midterm-2026.html");
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    assertThat(html).contains("viewport-fit=cover");
    assertThat(js).contains("/midterm-campaign/wiki-sources");
    assertThat(js).contains("latestSource(sources)");
    assertThat(js).contains("source.previewUrl");
    assertThat(js).contains("source.createdAt");
    assertThat(js).contains("midterm-feature__meta");
    assertThat(js).contains("midterm-feature__visual");
    assertThat(js).contains("midterm-feature__document");
    assertThat(js).contains("frame.setAttribute('sandbox', '')");
    assertThat(js).contains("frame.setAttribute('tabindex', '-1')");
    assertThat(js).contains("frame.setAttribute('aria-hidden', 'true')");
    assertThat(css).contains("overflow: hidden");
    assertThat(css).contains("pointer-events: none");
  }

  @Test
  void publicPage_keepsSingleRunMotionAndReducedMotionStaticCrop() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    assertThat(js).contains("IntersectionObserver");
    assertThat(js).contains("prefers-reduced-motion: reduce");
    assertThat(css).contains("8s ease-in-out 1 both");
    // One-time document pan: a single keyframe drift by a bounded pixel amount, never a loop.
    assertThat(css).contains("@keyframes midterm-document-drift");
    assertThat(css).contains("calc(-1 * var(--doc-drift))");
    assertThat(css).doesNotContain("infinite");
    assertThat(css).contains("@media (prefers-reduced-motion: reduce)");
    assertThat(css).contains("animation: none !important");
    assertThat(js).doesNotContain("setInterval");
    assertThat(js).doesNotContain("scrollBy");
  }

  @Test
  void publicPage_hasNoForbiddenDashboardOrFileUi() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");
    String html = read("static/eduholic-midterm-2026.html");
    String all = html + "\n" + js + "\n" + css;

    assertThat(all).doesNotContain("우리 학교");
    assertThat(all).doesNotContain("등록된 학교가 없습니다");
    assertThat(all).doesNotContain("Digital Twin");
    assertThat(all).doesNotContain("VOCABULARY");
    assertThat(all).doesNotContain("GRAMMAR");
    assertThat(all).doesNotContain("update log");
    assertThat(all).doesNotContain("progress %");
    assertThat(all).doesNotContain("다운로드");
    assertThat(all).doesNotContain("원문 보기");
    assertThat(all).doesNotContain("HTML 보기");
    assertThat(all).doesNotContain("첨부파일");
    assertThat(all).doesNotContain("storageKey");
    assertThat(all).doesNotContain("원본 URL");
    assertThat(all).doesNotContain("fileUrl");
    assertThat(all).doesNotContain("/problem-generator");
    assertThat(all).doesNotContain("/wiki/index.html");
    assertThat(js).doesNotContain("source.title");
    assertThat(js).doesNotContain("source.kind");
  }

  @Test
  void publicPage_zeroSourcesLeavesOnlyShortSeasonScreen() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");

    assertThat(js).contains("render(null)");
    assertThat(js).contains("if (source) main.appendChild(renderFeature(source))");
    assertThat(js).contains("2026 2학기 중간고사");
    assertThat(js).doesNotContain("placeholder");
    assertThat(js).doesNotContain("fake");
  }

  @Test
  void publicPage_keepsPr228SecurityBoundary() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");

    assertThat(js).doesNotContain("/api/analyzes");
    assertThat(js).doesNotContain("wiki-analysis");
    assertThat(js).doesNotContain("storageKey");
    assertThat(js).doesNotContain("download");
    assertThat(js).doesNotContain("signed");
    assertThat(js).doesNotContain("Authorization");
  }

  @Test
  void publicPage_neverLeaksToWorkspace_andPopupCtaDestinationHeld() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    assertThat(js).doesNotContain("/problem-generator");
    String popup = read("static/js/eduholic_midterm_popup.js");
    assertThat(popup).contains("/eduholic-midterm-2026.html");
    assertThat(popup).doesNotContain("/problem-generator");
  }

  @Test
  void popup_keepsHostGate_usesServerFlag_andHasAccessibleDismiss() throws Exception {
    String js = read("static/js/eduholic_midterm_popup.js");

    assertThat(js).contains("eduholic.feel2real.co.kr");
    assertThat(js).contains("/api/academies/eduholic/midterm-campaign/popup");
    assertThat(js).doesNotContain("/problem-generator");
    assertThat(js).contains("Escape");
    assertThat(js).contains("emp-lock");
    assertThat(js).contains("safe-area-inset-bottom");
    assertThat(js).doesNotContain("모집!!!");
  }

  @Test
  void adminPage_loadsApiFirst_andSitsUnderOwnerGuardedRoute() throws Exception {
    String html = read("templates/lms/lms_midterm_live.html");

    assertThat(html).contains("~{fragments/_lms_shell :: sidebar('midterm-live')}");
    int api = html.indexOf("/js/api.js");
    int page = html.indexOf("/js/lms_midterm_live.js");
    assertThat(api).isGreaterThan(-1);
    assertThat(page).isGreaterThan(api);
  }

  @Test
  void adminScript_neverAssignsNativeReadOnlyDomProperties() throws Exception {
    String js = read("static/js/lms_midterm_live.js");

    assertThat(js).doesNotContain(".control =");
    assertThat(js).doesNotContain(".control=");
    assertThat(js).doesNotContainPattern(
        "\\.(control|form|labels|validity|files|options|elements)\\s*=\\s*[^=]");
    assertThat(js).doesNotContain("Object.assign(");
    assertThat(js).contains("function cbVal(");
    assertThat(js).contains("querySelector('input[type=\"checkbox\"]')");
    assertThat(js).contains("cbVal(popEnabled)");
    assertThat(js).doesNotContain(".control.checked");
  }

  @Test
  void adminScript_keepsPr220ImageSecurity_noRawStorageKeyInPayload() throws Exception {
    String js = read("static/js/lms_midterm_live.js");

    assertThat(js).contains("/popup-visual");
    assertThat(js).doesNotContain("popupVisualKey");
    assertThat(js).doesNotContain("thumbnailKey");
    assertThat(js).doesNotContain("imageKey");
  }

  /** Phase 2 — Shopify Motion "evolving collections": featured hero 아래 최근 자료 collage. */
  @Test
  void recentSection_isMotionCollageFromExposedSourcesOnly() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    // Recent = public wiki-sources 에서 hero(featured)를 뺀 나머지. 순서는 서버 canonical.
    assertThat(js).contains("recentSources(sources, hero)");
    assertThat(js).contains("source.sourceId !== heroId");
    assertThat(js).contains("midterm-recent__collection");
    assertThat(js).contains("midterm-recent__doc");

    // public JS 는 절대 admin/candidate/Python 경로를 직접 호출하지 않는다.
    assertThat(js).doesNotContain("/api/tutor");
    assertThat(js).doesNotContain("wiki-candidates");
    assertThat(js).doesNotContain("live-material-candidates");
    assertThat(js).doesNotContain(":8000");

    // 0건이면 섹션 자체가 없다 — 빈 자리/"준비 중" 더미 카드 없음.
    assertThat(js).contains("if (!sources.length) return;");
    assertThat(js).doesNotContain("준비 중 카드");

    // crop role 은 CSS presentation 전용 (DB/DTO taxonomy 아님).
    assertThat(css).contains(".midterm-recent__item.is-role-b");
    assertThat(js).doesNotContain("\"grammar\"");
    assertThat(js).doesNotContain("\"exercise\"");
    assertThat(js).doesNotContain("\"analysis\"");

    // 모바일 = horizontal browse rail, 무한 carousel 아님. 데스크톱 = 비대칭 collage.
    assertThat(css).contains("scroll-snap-type: x mandatory");
    assertThat(css).contains("midterm-recent--n1");
    assertThat(css).contains("midterm-recent--n2");
    assertThat(css).doesNotContain("infinite");

    // Recent iframe 도 동일 보안 계약(공유 safeFrame). previewUrl 외 파일 정보 미사용.
    assertThat(js).contains("function safeFrame(previewUrl");
    assertThat(js).doesNotContain("originalUrl");
    assertThat(js).doesNotContain("blob:");
  }
}
