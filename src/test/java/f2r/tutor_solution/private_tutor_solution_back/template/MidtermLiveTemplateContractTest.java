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

    // Phase 4a: DIGITAL_TWIN scene 은 공개 analyze content(permitAll · script 0 · 학생 PII 없음)를
    // 학교+학년 정확 일치로만 임베드한다. TUTOR/candidate/Python/storageKey/download 는 여전히 금지.
    assertThat(js).contains("/api/analyzes?schoolName=");
    assertThat(js).doesNotContain("/api/admin/analyzes");
    assertThat(js).doesNotContain("wiki-analysis");
    assertThat(js).doesNotContain("storageKey");
    assertThat(js).doesNotContain("download");
    assertThat(js).doesNotContain("signed");
    assertThat(js).doesNotContain("Authorization");
    assertThat(js).doesNotContain("/api/tutor");
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

  /** Phase 4a — School LIVE Mixed Scene Collection: hero 아래 (school, grade) FRAME.
   *  FRAME 외곽 고정, 안에서 MATERIAL / DIGITAL_TWIN scene 이 자동 전환. 페이지 carousel 없음. */
  @Test
  void liveCollection_isSchoolGradeFrameWithSceneCycle() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    // FRAME = wiki-sources 를 (school, grade) 로 groupBy. 자료 목록 UI 가 아니다.
    assertThat(js).contains("function resolveFrames(");
    assertThat(js).contains("school + '|' + grade");
    assertThat(js).contains("midterm-live__collection");
    assertThat(js).contains("midterm-live__stage");
    assertThat(js).contains("midterm-live__scene");

    // MATERIAL scene = server-controlled previewUrl (공유 safeFrame, sandbox / no interaction).
    assertThat(js).contains("function safeFrame(");
    assertThat(js).contains("frame.setAttribute('sandbox', '')");
    assertThat(js).contains("data-kind', 'material'");

    // DIGITAL_TWIN scene = 공개 analyze content, school + grade 정확 일치만. 가짜 fallback 없음.
    assertThat(js).contains("data-kind', 'twin'");
    assertThat(js).contains("gradeNumber(a.grade) === frame.grade");
    assertThat(js).contains("if (!contentUrl) return;");
    assertThat(js).doesNotContain("student");
    assertThat(js).doesNotContain("학생 Digital");

    // FRAME 내부 scene 만 자동 전환 — 페이지 전체 carousel / progress bar / slider library 없음.
    assertThat(js).contains("IntersectionObserver");
    assertThat(js).doesNotContain("setInterval");
    assertThat(js).doesNotContain("scrollBy");
    assertThat(css).doesNotContain("infinite");
    assertThat(css).doesNotContain("progress");
    assertThat(css).contains("scroll-snap-type: x mandatory");   // mobile browse rail
    assertThat(css).contains("midterm-live--n1");
    assertThat(css).contains(".midterm-live__scene.is-active");

    // 0 frame 이면 섹션 자체가 없다 — 더미 카드 없음.
    assertThat(js).contains("if (!frames.length) return;");

    // 뷰포트 밖 FRAME 은 타이머 정지(모바일 성능). reduced-motion 이면 정적.
    assertThat(js).contains("clearTimeout(sceneTimer)");
    assertThat(js).contains("if (reduceMotion");

    // crop role 은 CSS presentation 전용 (DB taxonomy 아님).
    assertThat(css).contains(".midterm-live__scene.is-role-b");
    assertThat(js).doesNotContain("\"grammar\"");
    assertThat(js).doesNotContain("\"analysis\"");
    assertThat(js).doesNotContain("originalUrl");
    assertThat(js).doesNotContain("blob:");
  }

  /** Phase 3A — Knowledge Product Packaging: 자료 = 상품 본체, 바깥 frame = 패키지.
   *  page bg → product surface → clipped media window → 실제 Wiki HTML. */
  @Test
  void knowledgeProduct_wrapsEachDocumentInFixedPackagingSurface() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");
    String html = read("static/eduholic-midterm-2026.html");
    String frontend = html + "\n" + js;

    // hero = product surface, LIVE FRAME = product stage — 같은 packaging grammar(raw iframe 아님).
    assertThat(js).contains("midterm-feature__surface");
    assertThat(js).contains("midterm-live__stage");
    assertThat(css).contains(".midterm-feature__surface");
    assertThat(css).contains(".midterm-live__stage");
    assertThat(css).contains("--surface-a");     // 고정 palette (index % 4) — 색은 taxonomy 아님
    assertThat(css).contains("--window-radius");

    // media 만 움직인다 — surface/frame/stage 애니메이션 없음.
    assertThat(css).contains("@keyframes midterm-product-drift");
    assertThat(css).contains("midterm-product-drift 8s ease-in-out 1 both");
    assertThat(css).doesNotContain("infinite");
    assertThat(css).doesNotContain("midterm-surface-drift");
    assertThat(css).doesNotContain("midterm-stage-drift");
    assertThat(css).doesNotContain("midterm-frame-drift");

    // 화면에 학습 내용/카테고리 label/상품명/뱃지/e-commerce UI 를 생성하지 않는다(§2·§25·§26·§32).
    assertThat(frontend).doesNotContain("가정법");
    assertThat(frontend).doesNotContain("관계대명사");
    assertThat(frontend).doesNotContain("Vocabulary");
    assertThat(frontend).doesNotContain("핵심 정리");
    assertThat(frontend).doesNotContain("학습 포인트");
    assertThat(frontend).doesNotContain("추천 자료");
    assertThat(frontend).doesNotContain("전체 자료 보기");
    assertThat(frontend).doesNotContain("장바구니");
    assertThat(frontend).doesNotContain("quickAdd");
    assertThat(js).doesNotContain("badge");
    assertThat(js).doesNotContain("NEW");
    // scene 카테고리 label 을 화면에 쓰지 않는다(§32) — data 속성/변수명만.
    assertThat(js).doesNotContain("'MATERIAL'");
    assertThat(js).doesNotContain("'STUDENT'");

    // 외부 metadata = 실제 school/grade/date 만.
    assertThat(js).contains("function metadata(source)");
    assertThat(js).doesNotContain("difficulty");

    // reduced-motion: 자료 정지, 자료가 사라지지 않는다.
    assertThat(css).contains("prefers-reduced-motion: reduce");
    assertThat(css).contains("animation: none !important");
  }

  /** Phase 4b — Academy Ambient Video: 기존 public AcademyVideo API 재사용, 운영자 허용 id 만,
   *  특정 학교 귀속 없이 academy-level ambient scene. 없으면 MATERIAL → 분석 흐름 그대로. */
  @Test
  void liveCollection_ambientVideoIsOptionalAcademyLevelScene() throws Exception {
    String html = read("static/eduholic-midterm-2026.html");
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    // B1 — 백엔드 무변경. slug / 허용 id 는 정적 HTML config(신규 DTO / 필드 / 엔드포인트 없음).
    assertThat(html).contains("data-slug=");
    assertThat(html).contains("data-ambient-video-ids=");
    assertThat(js).contains("data-ambient-video-ids");
    assertThat(js).contains("/videos");

    // 공개 목록 응답이 준 videoUrl / thumbnailUrl 만 재사용 — storage key / raw / 파일명 안 씀.
    assertThat(js).contains("ambientVideo.videoUrl");
    assertThat(js).doesNotContain("originalFilename");
    assertThat(js).doesNotContain("rawStorageKey");
    assertThat(js).doesNotContain("normalizedStorageKey");

    // 자동 content 분류 없음 — purpose / 파일명 / 길이로 "수업 영상"을 추론하지 않는다.
    assertThat(js).doesNotContain("purpose");
    assertThat(js).doesNotContain("LESSON");
    assertThat(js).doesNotContain("LIVE_VISIBLE");

    // ambient <video> — muted loop playsinline preload=metadata. player chrome / autoplay 속성 없음.
    assertThat(js).contains("video.muted = true");
    assertThat(js).contains("video.loop = true");
    assertThat(js).contains("video.playsInline = true");
    assertThat(js).contains("video.preload = 'metadata'");
    assertThat(js).doesNotContain("video.controls");
    assertThat(js).doesNotContain(".autoplay = true");

    // optional scene — 없으면 그냥 없다. 가짜 / 스톡 / 복제 영상 없음.
    assertThat(js).contains("videos[index] || null");
    assertThat(js).contains("data-kind', 'video'");
    assertThat(js).doesNotContain("stock");

    // offscreen + inactive pause, autoplay reject 안전(§10·§19·§20·§21).
    assertThat(js).contains("function pauseVideos(");
    assertThat(js).contains("v.pause()");
    assertThat(js).contains("p.catch(function () {})");

    // 영상별 LIVE 시작 구간 = 정적 config("id:sec"). 임의 business logic 하드코딩 아님.
    assertThat(html).contains("data-ambient-video-starts=");
    assertThat(js).contains("data-ambient-video-starts");
    assertThat(js).contains("AMBIENT_STARTS");
    assertThat(js).contains("current.startAt");            // 활성 + 재진입 모두 설정 시작점 사용
    assertThat(js).contains("t >= d");                     // 시작점 >= 길이면 안전 fallback
    assertThat(js).doesNotContain("videoId === 8");
    assertThat(js).doesNotContain("=== 8)");
    assertThat(js).doesNotContain("trimVideo");
    assertThat(js).doesNotContain("timeline");

    // 학교 귀속 조작 / 카테고리 라벨 생성 없음(§8·§12·§25).
    assertThat(js).doesNotContain("수업 영상");
    assertThat(js).doesNotContain("수업 현장");
    assertThat(js).doesNotContain("학생 학습");

    // reduced-motion 정책 유지(§14) — ambient 도 정지, 정책 우회 없음.
    assertThat(css).contains("prefers-reduced-motion: reduce");
    assertThat(css).contains(".midterm-live__video");
    assertThat(css).contains("object-fit: cover");

    // Phase 4c 아티팩트를 미리 만들지 않는다(§15·§16).
    String all = html + "\n" + js + "\n" + css;
    assertThat(all).doesNotContain("midterm_live_scene_video");
    assertThat(all).doesNotContain("scene_video");
    assertThat(all).doesNotContain("STUDENT_STUDY");
    assertThat(all).doesNotContain("LESSON_INTERACTION");
  }
}
