package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 내신 LIVE 공개 페이지·팝업·운영자 화면의 정적 계약.
 *
 * <p>핵심: 학부모 CTA 가 BuildIt Wiki workspace({@code /problem-generator})로 새면 안 된다(§11·§19).
 * 모바일 필수 요소(viewport·safe-area)와 서버 판정 원칙(정적 HTML 에 PRIVATE 파일 직링크 부재)을
 * 못박는다.
 */
class MidtermLiveTemplateContractTest {

  private static final Path ROOT =
      Path.of(System.getProperty("lms.sourceRoot", "src/main/resources"));

  private String read(String rel) throws Exception {
    return Files.readString(ROOT.resolve(rel));
  }

  @Test
  void publicPage_isMobileFirst_andNeverLinksToWikiWorkspace() throws Exception {
    String html = read("static/eduholic-midterm-2026.html");
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    assertThat(html).contains("viewport-fit=cover");
    assertThat(html).contains("name=\"viewport\"");
    // 캠페인 데이터는 전용 공개 API 에서 온다(Wiki workspace 아님).
    assertThat(js).contains("/api/academies/");
    assertThat(js).contains("/midterm-campaign");
    // 렌더러는 공개 DTO 만 소비한다(Wiki 원본을 직접 조회하지 않는다) — PR #220 보안 경계 유지.
    assertThat(js).doesNotContain("/api/analyzes");
    assertThat(js).doesNotContain("wiki-analysis");

    // 어떤 파일에서도 제작/관리 workspace 로 보내지 않는다.
    assertThat(html).doesNotContain("/problem-generator");
    assertThat(js).doesNotContain("/problem-generator");
    assertThat(js).doesNotContain("/wiki/index.html");

    // 모바일 필수: safe-area + sticky CTA + reduced-motion + 횡스크롤(같은 종류 탐색) + 다음 카드 peek.
    assertThat(css).contains("safe-area-inset");
    assertThat(css).contains("mt-sticky");
    assertThat(css).contains("prefers-reduced-motion");
    assertThat(css).contains("scroll-snap-type");
    assertThat(css).contains("--card-w");
  }

  /**
   * 이 페이지는 기존 Eduholic 공개 페이지(academy_public.css)와 같은 브랜드로 보여야 한다.
   * 생성형 AI 랜딩페이지 문법(장식 영어 eyebrow·번호+영어 소제목·"A가 아니라 B"·beige+serif
   * 독립 디자인 시스템)이 남아 있으면 안 된다(작업지시서 §7~§10·§15~§19·§45·§46).
   */
  @Test
  void publicPage_hasNoAiLandingArtifacts_andInheritsBuildItTokens() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");
    String html = read("static/eduholic-midterm-2026.html");

    // beige + serif 독립 디자인 시스템 제거 → BuildIt 토큰 승계.
    assertThat(css).doesNotContain("Playfair");
    assertThat(css).doesNotContain("#f6f4ef");
    assertThat(html).doesNotContain("Playfair");
    assertThat(html).doesNotContain("fonts.googleapis");
    assertThat(css).contains("--ink: #0a0a0c");     // academy_public.css :root 와 동일
    assertThat(css).contains("--accent: #2563eb");
    assertThat(css).contains("--reveal-duration");  // academy_reveal 계열 1회 reveal

    // "01 SOURCE / 02 ANALYZE / 03 BUILD …" + 영어 eyebrow/section label 제거.
    assertThat(js).doesNotContain("SOURCE");
    assertThat(js).doesNotContain("ANALYZE");
    assertThat(js).doesNotContain("VARIATION");
    assertThat(js).doesNotContain("HOW WE PREPARE");
    assertThat(js).doesNotContain("How We Prepare");
    assertThat(js).doesNotContain("VIEW COLLECTION");
    assertThat(js).doesNotContain("MIDTERM LOG");
    assertThat(js).doesNotContain("mt-serif");

    // AI 식 대비 카피("A가 아니라 B") 금지.
    assertThat(js).doesNotContain("것이 아니라");
    assertThat(js).doesNotContain("단순한");
    assertThat(js).doesNotContain("그 이상");
    assertThat(js).doesNotContain("새로운 기준");

    // 스크롤 진입 1회 reveal — 무거운 라이브러리/자동 캐러셀 없이 IntersectionObserver + reduced-motion.
    assertThat(js).contains("IntersectionObserver");
    assertThat(js).contains("prefers-reduced-motion");
    assertThat(js).doesNotContain("gsap");
    assertThat(js).doesNotContain("setInterval");   // 자동 캐러셀 없음
  }

  /**
   * PR #225 regression preserved (별도 cherry-pick 아님 — #225 는 이미 main 에 merge 됨).
   * 하단 sticky CTA 가 죽은 버튼이 아니다: 학교 컬렉션이 0개여도 안내 문구가 있는
   * 섹션(id="mt-collections")이 항상 존재하고, CTA 는 그 섹션으로 이동한다.
   * keyboard(Enter/Space) 와 실제 {@code <a href>}, 상세→홈 복귀, reduced-motion 도 유지.
   * 재설계 후 DOM 이 바뀌었으므로 문구/클래스는 새 구조에 맞추되 동작 계약은 그대로 승계한다.
   */
  @Test
  void publicPage_stickyCtaAlwaysResolves_evenWithZeroSchoolCollections() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    // 학교 컬렉션 섹션은 항상 렌더 + 안정적인 앵커 id.
    assertThat(js).contains("renderCollections(c.schoolCollections || [])");
    assertThat(js).contains("s.id = 'mt-collections'");
    assertThat(js).contains("#mt-collections");
    // 예전 죽은 목적지(#collections, 존재하지 않는 id)는 제거됐다.
    assertThat(js).doesNotContain("a.href = '#collections'");
    assertThat(js).doesNotContain("querySelector('.mt-collections')");

    // 0개일 때 dead-end 가 아니라 짧은 안내 + 대체 동선(상담). 대형 홍보 section 이 아니다(§13).
    assertThat(js).contains("if (!collections.length)");
    assertThat(js).contains("아직 등록된 학교가 없습니다");
    assertThat(js).contains("mt-collections-empty");
    assertThat(js).contains("mt-empty-cta");
    assertThat(css).contains(".mt-collections-empty");

    // CTA 는 실제 <a href="#mt-collections"> + 스크롤 이동 + 키보드(Enter/Space).
    assertThat(js).contains("a.href = '#mt-collections'");
    assertThat(js).contains("function goToCollections()");
    assertThat(js).contains("scrollIntoView");
    assertThat(js).contains("addEventListener('keydown'");
    assertThat(js).contains("'Spacebar'");
    // 상세 화면(#school-*)에서도 홈으로 복귀 후 스크롤 + reduced-motion 존중.
    assertThat(js).contains("#school-");
    assertThat(js).contains("prefers-reduced-motion");

    // 팝업 CTA 목적지는 유지.
    assertThat(js).doesNotContain("/problem-generator");
    String popup = read("static/js/eduholic_midterm_popup.js");
    assertThat(popup).contains("/eduholic-midterm-2026.html");
  }

  @Test
  void popup_keepsHostGate_usesServerFlag_andHasAccessibleDismiss() throws Exception {
    String js = read("static/js/eduholic_midterm_popup.js");

    assertThat(js).contains("eduholic.feel2real.co.kr");            // 타 Academy 미노출
    assertThat(js).contains("/api/academies/eduholic/midterm-campaign/popup"); // 서버 판정
    assertThat(js).doesNotContain("/problem-generator");
    assertThat(js).contains("Escape");                              // ESC 닫기
    assertThat(js).contains("emp-lock");                            // body scroll lock
    assertThat(js).contains("safe-area-inset-bottom");
    // 과거 하드코딩 학교 목록/전단지 문구가 남아 있지 않다.
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

  /**
   * 회귀: 운영자 관리 스크립트가 DOM 요소의 native read-only 프로퍼티에 값을 대입하지 않는다.
   * {@code <label>.control = ...} 는 getter-only 라 대입 시 "Cannot set property control of
   * #<HTMLLabelElement>" 로 렌더 전체가 중단됐다(Production /lms/eduholic/midterm-live 장애).
   */
  @Test
  void adminScript_neverAssignsNativeReadOnlyDomProperties() throws Exception {
    String js = read("static/js/lms_midterm_live.js");

    // 이 장애의 직접 원인 — 어떤 형태로도 다시 나타나면 실패.
    assertThat(js).doesNotContain(".control =");
    assertThat(js).doesNotContain(".control=");
    // 같은 계열(대입 시 예외 또는 무시되는 native 접근자)도 함께 봉쇄.
    assertThat(js).doesNotContainPattern("\\.(control|form|labels|validity|files|options|elements)\\s*=\\s*[^=]");
    assertThat(js).doesNotContain("Object.assign(");

    // 체크박스 상태는 요소에 프로퍼티를 붙이지 않고 helper 로 읽는다.
    assertThat(js).contains("function cbVal(");
    assertThat(js).contains("querySelector('input[type=\"checkbox\"]')");
    assertThat(js).contains("cbVal(popEnabled)");
    // 옛 패턴이 남아있지 않다.
    assertThat(js).doesNotContain(".control.checked");
  }

  @Test
  void adminScript_keepsPr220ImageSecurity_noRawStorageKeyInPayload() throws Exception {
    String js = read("static/js/lms_midterm_live.js");

    // PR #220 보안 수정 유지: 이미지는 전용 multipart endpoint 로만, key 를 payload 에 싣지 않는다.
    assertThat(js).contains("/popup-visual");
    assertThat(js).doesNotContain("popupVisualKey");
    assertThat(js).doesNotContain("thumbnailKey");
    assertThat(js).doesNotContain("imageKey");
  }
}
