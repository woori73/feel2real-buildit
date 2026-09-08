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

    assertThat(js).contains("render(null, [])");                  // 자료·showcase 모두 없음
    assertThat(js).contains("main.appendChild(renderFeature(source))");   // showcase 없을 때 iframe 폴백
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

    // 자동 content 분류 없음 — purpose / 파일명 / 길이로 영상을 추론하지 않는다.
    // (Phase 4c 는 'LESSON_INTERACTION' 을 scene 역할명으로 쓰므로 purpose enum 값 자체만 금지.)
    assertThat(js).doesNotContain("purpose");
    assertThat(js).doesNotContain("=== 'LESSON'");
    assertThat(js).doesNotContain("'DIRECTOR'");
    assertThat(js).doesNotContain("LIVE_VISIBLE");

    // ambient <video> — muted loop playsinline preload=metadata. player chrome / autoplay 속성 없음.
    assertThat(js).contains("video.muted = true");
    assertThat(js).contains("video.loop = true");
    assertThat(js).contains("video.playsInline = true");
    assertThat(js).contains("video.preload = 'metadata'");
    assertThat(js).doesNotContain("video.controls");
    assertThat(js).doesNotContain(".autoplay = true");

    // optional scene — 없으면 그냥 없다. 가짜 / 스톡 / 복제 영상 없음.
    // deterministic 배정: specific 없는 FRAME 에만 ambient 를 순서대로, 반복 없이.
    assertThat(js).contains("ambientQueue.shift()");
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

    // ambient fallback config 는 정적 HTML 에 남아 있다(specific 없는 FRAME 용, Phase 4c §22).
    assertThat(html).contains("data-ambient-video-ids=");
  }

  /** Phase 4c — School / Grade Real Class Video Association: 서버가 판정한 (학교,학년,역할)
   *  연결만 받아 FRAME scene 을 MATERIAL → STUDENT_STUDY → LESSON_INTERACTION → TWIN 으로 확장. */
  @Test
  void liveCollection_schoolGradeVideoAssociationExtendsSceneOrder() throws Exception {
    String html = read("static/eduholic-midterm-2026.html");
    String js = read("static/js/eduholic_midterm_2026.js");

    // 전용 공개 endpoint — 서버가 visible + READY + ownership 을 이미 판정한다.
    assertThat(js).contains("/midterm-campaign/live-videos");
    assertThat(js).contains("function liveVideos(");

    // FRAME 매칭 = school + grade exact (fuzzy 없음, Phase 4a 규칙 유지).
    assertThat(js).contains("=== frame.school");
    assertThat(js).contains("gradeNumber(l.grade) === frame.grade");

    // 역할별 대표 1개, 서버 정렬(sort_order → link_id) 을 그대로 신뢰한다.
    assertThat(js).contains("pickRole(links, 'STUDENT_STUDY')");
    assertThat(js).contains("pickRole(links, 'LESSON_INTERACTION')");

    // resolver 우선순위: specific 있으면 그 FRAME 에서 ambient 는 쓰지 않는다(§10·§22).
    assertThat(js).contains("!studyClip && !interactionClip && ambientVideo");
    assertThat(js).contains("hasSpecific ? null");

    // 세 종류 video scene 은 Phase 4b <video> 빌더 하나로 — player chrome / autoplay 속성 없음.
    assertThat(js).contains("function addVideoScene(");
    assertThat(js).doesNotContain("video.controls");
    assertThat(js).doesNotContain(".autoplay = true");

    // 화면에 역할/카테고리 라벨을 만들지 않는다(§9). 역할명은 내부 상수로만 존재.
    assertThat(js).doesNotContain("학생 학습");
    assertThat(js).doesNotContain("박원장 피드백");
    assertThat(js).doesNotContain("LESSON INTERACTION");
    assertThat(js).doesNotContain("수업 중");

    // Phase 1 CMS / school navigator 를 부활시키지 않는다(§32).
    assertThat(js).doesNotContain("schoolCollections");
    assertThat(js).doesNotContain("briefing");
    assertThat(js).doesNotContain("makingOf");
    assertThat(js).doesNotContain("우리 학교");

    // Phase 4b ambient fallback 유지(§22).
    assertThat(html).contains("data-ambient-video-starts=");
  }

  /** Phase 4d — LIVE Collection Layout + Shopify Product Frame + Video fit.
   *  모든 학교 FRAME 은 동일한 outer geometry. 3 layer packaging. video fit 은 방향 대응. */
  @Test
  void liveCollection_uniformProductFrameAndDirectionAwareVideo() throws Exception {
    String css = read("static/css/eduholic_midterm_2026.css");
    String js = read("static/js/eduholic_midterm_2026.js");
    String html = read("static/eduholic-midterm-2026.html");

    // A — 내용/데이터에 따라 카드 크기가 달라지는 asymmetric span 제거. 균일 2-column grid.
    assertThat(css).doesNotContain("grid-column: span 4");
    assertThat(css).doesNotContain("grid-column: span 6");
    assertThat(css).doesNotContain("grid-row: span 2");
    assertThat(css).doesNotContain("grid-auto-flow: dense");
    assertThat(css).doesNotContain("repeat(6, 1fr)");
    assertThat(css).contains("grid-template-columns: repeat(2, minmax(0, 1fr))");
    // stage 고정 비율 → 모든 stage 가 같은 크기(scene 전환 layout shift 없음).
    assertThat(css).contains("aspect-ratio: var(--stage-ratio)");
    assertThat(css).contains("--stage-ratio:");

    // C/D — 3 layer: collection band → white product frame(visible border·padding) → tinted stage.
    assertThat(css).contains("--live-band:");
    assertThat(css).contains("--stage-bg:");
    assertThat(css).contains("--frame-border: 1px solid");
    assertThat(css).contains("border: var(--frame-border)");
    assertThat(css).contains("background: var(--live-band)");
    assertThat(css).contains("padding: var(--frame-pad)");
    // 새 DOM 계층: frame > stage > scene + info(meta 아래 작은 상품 정보).
    assertThat(js).contains("midterm-live__info");

    // B — VIDEO fit 은 runtime 영상 dimension 으로 판정(videoId 하드코딩 없음). panorama cover 실패 방지.
    assertThat(js).contains("video.videoHeight > video.videoWidth");
    assertThat(js).contains("is-portrait");
    assertThat(js).contains("is-landscape");
    assertThat(css).contains(".midterm-live__scene.is-portrait .midterm-live__video");
    assertThat(css).contains(".midterm-live__scene.is-landscape .midterm-live__video");
    assertThat(css).contains("object-fit: contain");        // 기본 = contain, letterbox = stage bg
    assertThat(js).doesNotContain("videoId ===");
    assertThat(js).doesNotContain("=== 14");
    assertThat(css).doesNotContain("blur(");                // blurred duplicate video background 금지
    assertThat(css).doesNotContain("backdrop-filter");

    // §24 금지 — school navigator / 홈페이지 리디자인 / e-commerce.
    String frontend = html + "\n" + js + "\n" + css;
    assertThat(frontend).doesNotContain("우리 학교");
    assertThat(frontend).doesNotContain("2026 FALL");
    assertThat(frontend).doesNotContain("장바구니");

    // 기존 motion 계약 유지.
    assertThat(css).contains("midterm-product-drift 8s ease-in-out 1 both");
    assertThat(css).doesNotContain("infinite");
  }

  /** Phase 4d §16 — 대표수업(lesson_products.media_video_id / builderDraft)과 LIVE 연결
   *  (midterm_live_video_links)은 독립. LIVE 서비스가 lesson 상태를 읽거나 쓰지 않는다. */
  @Test
  void liveVideoAssociation_isIndependentFromRepresentativeLesson() throws Exception {
    String adminSvc = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/midtermcampaign/service/MidtermLiveVideoAdminService.java"));
    String pubSvc = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/midtermcampaign/service/MidtermLiveVideoService.java"));

    for (String svc : new String[] { adminSvc, pubSvc }) {
      assertThat(svc).doesNotContain("LessonProduct");
      assertThat(svc).doesNotContain("lessonProductRepository");
      assertThat(svc).doesNotContain("builderDraft");
      assertThat(svc).doesNotContain("PageSettings");
      assertThat(svc).doesNotContain("featuredLessonIds");
      assertThat(svc).doesNotContain("mediaVideoId");
    }
  }

  /** Phase 4e — 상단 Showcase = 학교별 시험범위(서버가 Wiki HTML 에서 bounded 추출한 텍스트)
   *  rotation. transform: scale 로 문서를 축소하지 않는다. 학교/학년/시험 identity 항상 표시. */
  @Test
  void showcase_isSchoolRotationFromExtractedWikiText_notScaledDocument() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    // 전용 read-only endpoint 에서 서버가 추출한 scene 만 받는다.
    assertThat(js).contains("/midterm-campaign/showcase");
    assertThat(js).contains("function showcaseScenes(");
    assertThat(js).contains("function renderShowcase(");

    // 학교/학년/시험 identity 는 각 scene 상단에 항상.
    assertThat(js).contains("midterm-showcase__school");
    assertThat(js).contains("midterm-showcase__exam");
    assertThat(js).contains("scene.grammarPoints");
    assertThat(js).contains("scene.sampleSentences");
    assertThat(js).contains("시험범위 자료 준비 중");   // fallback

    // rotation — 하단 LIVE 처럼 offscreen pause / reduced-motion.
    assertThat(js).contains("function wireShowcaseRotation(");
    assertThat(js).contains("IntersectionObserver");
    assertThat(js).contains("reduceMotion");

    // 원본 문서를 iframe 으로 넣거나 scale() 로 축소하지 않는다 — 실제 text layout.
    assertThat(js).doesNotContain("midterm-showcase__document");
    assertThat(css).contains(".midterm-showcase__surface");
    assertThat(css).containsPattern("\\.midterm-showcase__surface\\s*\\{[^}]*height:[^;]*svh");  // responsive height
    assertThat(css).doesNotContainPattern("\\.midterm-showcase[\\s\\S]{0,400}transform:\\s*scale");

    // 추출은 서버(jsoup)에서만 — 프런트가 원본 HTML 을 파싱하지 않는다.
    assertThat(js).doesNotContain("DOMParser");

    // reduced-motion 정책.
    assertThat(css).contains(".midterm-showcase__scene:not(.is-active)");
  }

  /** Phase 4e §4 — 시험범위를 AI/검색/추측으로 만들지 않는다. 서버 추출은 Wiki HTML 파싱만. */
  @Test
  void showcaseExtractor_parsesOnly_noNetworkNoGuess() throws Exception {
    String ext = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/midtermcampaign/service/MidtermShowcaseExtractor.java"));
    assertThat(ext).contains("Jsoup.parse");
    assertThat(ext).doesNotContain("Jsoup.connect");
    assertThat(ext).doesNotContain("HttpClient");
    assertThat(ext).doesNotContain("openai");
    assertThat(ext).doesNotContain("WebSearch");
    String svc = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/midtermcampaign/service/MidtermShowcaseService.java"));
    assertThat(svc).doesNotContain("Jsoup.connect");
    assertThat(svc).contains("gateway.fetchHtml");   // 이미 인가된 Wiki HTML 만
  }

  /** Phase 4e §10~12 — 영상 용도 "용도 지정 안 함"(NONE). NULL 재사용 아님, LIVE 연결 독립. */
  @Test
  void videoPurpose_hasExplicitNone_keptIndependentFromLive() throws Exception {
    String enumSrc = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/entity/AcademyVideoPurpose.java"));
    assertThat(enumSrc).contains("LESSON,");
    assertThat(enumSrc).contains("NONE");
    assertThat(enumSrc.lastIndexOf("NONE")).isGreaterThan(enumSrc.lastIndexOf("LESSON,"));

    String svc = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyVideoService.java"));
    // 빈 값은 여전히 거부(새 null 유입 차단 유지) — NONE 은 명시적 선택이라 통과.
    assertThat(svc).contains("purposeRaw.isBlank()");
    assertThat(svc).contains("DIRECTOR, LESSON, NONE");
    // updatePurpose 는 lesson_products / builderDraft 를 건드리지 않는다.
    assertThat(svc).doesNotContain("lessonProductRepository");

    String videoJs = read("static/js/tutor_academy_videos.js");
    assertThat(videoJs).contains("value=\"NONE\"");
    assertThat(videoJs).contains("용도 지정 안 함");
    assertThat(videoJs).contains("liveUsageLabels");
    assertThat(videoJs).contains("내신 LIVE에서 연결");

    String videoHtml = read("templates/tutor/tutor_academy_videos.html");
    assertThat(videoHtml).contains("value=\"NONE\"");
  }

  /**
   * Bugfix — Phase 4e 가 좁은 그리드 열의 가로 flex 행(.video-card-body)에 긴 한글 안내문과
   * LIVE 배지를 flex 형제로 넣어 카드 텍스트가 글자 단위로 세로로 접혔다. 본문을 세로 스택으로
   * 되돌리고, 안내문 한 줄이 흐를 최소 열 폭을 확보하고, 모바일은 카드 한 장씩 쌓는다.
   */
  @Test
  void videoCard_bodyStacksVertically_noPerCharColumnCollapse() throws Exception {
    String css = read("static/css/tutor_academy_videos.css");

    // 근본 원인 수정: 본문은 더 이상 space-between 가로 행이 아니라 세로 스택이다.
    int bodyIdx = css.indexOf(".video-card-body {");
    assertThat(bodyIdx).isGreaterThan(-1);
    String bodyBlock = css.substring(bodyIdx, css.indexOf('}', bodyIdx));
    assertThat(bodyBlock).contains("flex-direction: column");
    assertThat(bodyBlock).doesNotContain("justify-content: space-between");
    assertThat(bodyBlock).contains("min-width: 0");
    // flex/grid 자식이 min-content 아래로 줄어들 수 있어야 글자 단위 줄바꿈이 사라진다.
    int childIdx = css.indexOf(".video-card-body > *");
    assertThat(childIdx).isGreaterThan(-1);
    assertThat(css.substring(childIdx, css.indexOf('}', childIdx))).contains("min-width: 0");

    // 안내문은 한글 단어를 지키며 정상 문장으로 흐른다(증상만 가리는 word-break:break-all 아님).
    int noteIdx = css.indexOf(".video-card-purpose-note {");
    assertThat(noteIdx).isGreaterThan(-1);
    String noteBlock = css.substring(noteIdx, css.indexOf('}', noteIdx));
    assertThat(noteBlock).contains("white-space: normal");
    assertThat(noteBlock).contains("word-break: keep-all");
    assertThat(noteBlock).doesNotContain("break-all");

    // 셀렉트는 열 폭을 넘지 않는다.
    int selIdx = css.indexOf(".video-card-purpose-select {");
    String selBlock = css.substring(selIdx, css.indexOf('}', selIdx));
    assertThat(selBlock).contains("max-width: 100%");
    assertThat(selBlock).contains("min-width: 0");

    // 안내문 한 줄을 담을 최소 열 폭 확보 + 모바일은 카드 1장씩.
    assertThat(css).contains("minmax(240px, 1fr)");
    assertThat(css).doesNotContain("minmax(160px, 1fr)");
    int mqIdx = css.indexOf("@media (max-width: 520px)");
    assertThat(mqIdx).isGreaterThan(-1);
    assertThat(css.substring(mqIdx, css.indexOf('}', css.indexOf('}', mqIdx) + 1)))
        .contains("grid-template-columns: 1fr");

    // 메타(길이·해상도·Ready)는 한 줄로 감싸는 래퍼가 있다.
    String videoJs = read("static/js/tutor_academy_videos.js");
    assertThat(videoJs).contains("video-card-meta");
    assertThat(css).contains(".video-card-meta {");
  }
}
