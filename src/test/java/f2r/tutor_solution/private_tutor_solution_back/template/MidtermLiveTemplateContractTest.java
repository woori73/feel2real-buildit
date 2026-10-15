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

  /**
   * Bugfix — Phase 4e 회귀: showcase 응답과 live/ambient 응답이 독립 Promise 로 경쟁해,
   * LIVE 가 먼저 append 된 뒤 늦게 온 showcase 가 render() 의 root.innerHTML='' 로 LIVE 를
   * 지워 "학교별 준비 LIVE" 가 통째로 사라졌다. 초기 렌더를 단일 파이프라인으로 고정한다.
   */
  @Test
  void publicPage_initialRenderIsSinglePipeline_liveNotWipedByLateShowcase() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");

    // showcase / ambient / live 세 helper 를 한 Promise.all 로 모아 순서를 고정한다.
    assertThat(js).contains("Promise.all([showcaseScenes(), ambientVideos(), liveVideos(), liveUpdates()])");
    // render() → renderLiveCollection() 을 그 콜백 안에서 정확히 1회씩, 이 순서로 호출.
    assertThat(js).containsOnlyOnce("render(latestSource(sources), r[0])");
    assertThat(js).containsOnlyOnce("renderLiveCollection(sources, r[1], r[2], r[3])");
    int chainIdx = js.indexOf("Promise.all([showcaseScenes(), ambientVideos(), liveVideos(), liveUpdates()])");
    int renderIdx = js.indexOf("render(latestSource(sources), r[0])", chainIdx);
    int liveIdx = js.indexOf("renderLiveCollection(sources, r[1], r[2], r[3])", chainIdx);
    assertThat(renderIdx).isGreaterThan(chainIdx);
    assertThat(liveIdx).isGreaterThan(renderIdx);   // root 를 비우는 render 가 반드시 먼저

    // showcase / live 가 각자 render 를 부르던 옛 경쟁 경로가 남아 있으면 안 된다.
    assertThat(js).doesNotContain("showcaseScenes().then(function (scenes) {");
    assertThat(js).doesNotContain("Promise.all([ambientVideos(), liveVideos()])");
    // timing workaround 금지.
    assertThat(js).doesNotContain("setTimeout(function () { renderLiveCollection");

    // 각 helper 는 실패를 [] 로 흡수해 파이프라인을 막지 않는다(부분 실패 내성).
    assertThat(js).contains("function showcaseScenes()");
    assertThat(js).contains("function ambientVideos()");
    assertThat(js).contains("function liveVideos()");
    assertThat(countOccurrences(js, ".catch(function () { return []; })"))
        .as("showcase/ambient/live/analyzes helpers all swallow failure to []")
        .isGreaterThanOrEqualTo(4);
    // LIVE 렌더가 던져도 이미 그려진 hero/showcase 는 지우지 않는다.
    assertThat(js).contains("try {");
    assertThat(js.indexOf("try {")).isLessThan(js.indexOf("renderLiveCollection(sources, r[1], r[2], r[3])"));
  }

  private static int countOccurrences(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
      n++;
    }
    return n;
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

  /**
   * 홈페이지 「2학기 영어내신」 → "2학기 영어 내신 LIVE" 전환 스크립트(PR #244).
   * eduholic hostname gate, 추가 fetch 없이 wireFeaturedSchoolCards 결과 재사용,
   * 공용 섹션 렌더러 정본·상담 CTA·pill CTA 무변경.
   */
  @Test
  void homeLiveScript_transformsFeaturedSectionWithoutTouchingSharedRenderer() throws Exception {
    String html = read("templates/public/academy_public.html");
    assertThat(html).contains("/js/eduholic_midterm_home_live.js");

    String js = read("static/js/eduholic_midterm_home_live.js");

    assertThat(js).contains("eduholic.feel2real.co.kr");
    assertThat(js).contains("/eduholic-midterm-2026.html");
    assertThat(js).doesNotContain("/problem-generator");
    assertThat(js).doesNotContain("fetchAuth");

    assertThat(js).contains("2학기 영어 내신 LIVE");
    assertThat(js).contains("#sec-featured");

    // 출제 경향 데이터는 이미 렌더된 카드 본문에서 읽는다(카드마다 fetch 금지 = N+1 금지).
    assertThat(js).contains("data-featured-card-body");
    assertThat(js).contains("featured-schoolcard-row");
    // 캠페인 스코프 조회는 정확히 2개(showcase 시험범위 + live-videos). 카드 수와 무관 — N+1 아님.
    assertThat(countOccurrences(js, "fetch(")).isEqualTo(2);
    assertThat(js).contains("/midterm-campaign/live-videos");
    assertThat(js).contains("/midterm-campaign/showcase");
    // §2 — 시험범위 Wiki 와 출제 경향 분석은 서로 다른 소스일 때만 각각 scene 을 만든다.
    assertThat(js).contains("function pickSyllabus(");
    assertThat(js).contains("indexOf('final-') === 0");
    // 카드마다 analyze 재조회 금지(academy_public.js 가 이미 함).
    assertThat(js).doesNotContain("/api/analyzes");

    // 공용 섹션의 다른 부분·상담 신청 버튼·pill CTA·공용 마크업 정본은 건드리지 않는다.
    assertThat(js).doesNotContain("requestConsult");
    assertThat(js).doesNotContain("featured-cta");
    assertThat(js).doesNotContain("academy_sections");

    // 자동 scene 순환 = setTimeout 체인, 화면 밖/reduced-motion 정지.
    assertThat(js).contains("setTimeout");
    assertThat(js).doesNotContain("setInterval");
    assertThat(js).contains("IntersectionObserver");
    assertThat(js).contains("prefers-reduced-motion");
  }

  /**
   * 후속 개편(2026-09) — 첫 화면 왼쪽 = 약한 문구("중간고사 준비 중" / "학교 프린트 내용을
   * 반영했습니다") 대신 브랜드 메시지 + AI Wiki activity. activity 는 Showcase scene 데이터를
   * 재사용하고 독립 school-rotation timer 를 두지 않는다(왼/오 학교 동기화).
   */
  @Test
  void publicPage_leftHeroIsBrandMessagePlusWikiActivity_sharedWithShowcase() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");
    String all = js + "\n" + css;

    // 약한 옛 문구는 primary hero copy 로 존재하지 않는다.
    assertThat(all).doesNotContain("중간고사 준비 중");
    assertThat(all).doesNotContain("학교 프린트 내용을 반영했습니다");

    // 브랜드 메시지 — 현장 경험 × 학교별 시험자료 × AI Wiki.
    assertThat(js).contains("20년 이상 현장을 지켜온 박정민 원장");
    assertThat(js).contains("AI Wiki");
    assertThat(js).contains("시험 준비가 시작됐습니다");
    assertThat(js).contains("mt-kicker");
    assertThat(js).contains("mt-proof");

    // AI Wiki activity 는 별도 fetch 없이 displayScenes 배열을 그대로 받는다.
    assertThat(js).contains("function buildWikiActivity(scenes)");
    assertThat(js).contains("scene.lesson");
    assertThat(js).contains("scene.grammarPoints");
    assertThat(js).contains("scene.sampleSentences");
    // showcase API 호출은 한 곳(showcaseScenes) 뿐 — activity 가 두 번째 호출을 만들지 않는다.
    assertThat(countOccurrences(js, "/midterm-campaign/showcase")).isEqualTo(1);

    // 학교 순환은 wireShowcaseRotation 하나만 — activity 는 showScene(index) 로 따라간다.
    assertThat(js).contains("wireShowcaseRotation(section, built, wikiPanel)");
    assertThat(js).contains("wikiPanel.showScene(index)");
    assertThat(countOccurrences(js, "setTimeout(next, SHOWCASE_MS)")).isEqualTo(2);   // play + next, activity 아님
    assertThat(js).doesNotContain("setInterval");

    // 자료가 없는 항목은 생략, 아무 것도 없으면 정적 "준비 중" 문구(fake 금지).
    assertThat(js).contains("학교별 시험자료 준비 중");
    assertThat(js).doesNotContain("fake");

    // reduced-motion — activity 단계 애니메이션 정지.
    assertThat(css).contains(".mt-wiki__step");
    assertThat(css).contains("prefers-reduced-motion: reduce");
  }

  /**
   * 후속 개편(2026-09) — 모바일 "학교별 준비 LIVE" 가로 스와이프 안내. 데스크톱(2-column grid)에는
   * 표시하지 않는다. 다음 카드 일부가 보이도록 카드 폭 유지, 화살표 motion 은 유한(무한/깜빡임 금지).
   */
  @Test
  void publicPage_liveCollectionHasMobileSwipeAffordance_hiddenOnDesktop() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    assertThat(js).contains("옆으로 밀어 학교별 준비 보기");
    assertThat(js).contains("midterm-live__hint");
    assertThat(js).contains("midterm-live__lead");           // 학교명 부제
    assertThat(js).contains("frames.length > 1");            // 카드 2장 이상일 때만

    // 데스크톱 grid 에서는 hint 를 숨긴다.
    assertThat(css).contains(".midterm-live__hint { display: none; }");

    // 화살표 motion 은 유한 — 무한 반복/깜빡임 없음.
    assertThat(css).contains("midterm-live-hint-nudge");
    assertThat(css).doesNotContain("infinite");
    assertThat(css).contains("animation: none !important");  // reduced-motion 정지

    // 다음 카드 peek 유지 — frame 은 뷰포트 폭보다 좁다(scroll-snap 유지).
    assertThat(css).contains("flex: 0 0 80vw");
    assertThat(css).contains("scroll-snap-align: start");
    assertThat(css).contains("scroll-snap-type: x mandatory");
  }

  /**
   * 긴급 bugfix(2026-09) — Hero/rotation 의 학교 identity 는 캠페인 대상 학교(정적 config,
   * data-campaign-schools) 로 고정한다. Showcase API 가 특정 학교 자료만 반환해도 Hero 가
   * 그 학교로 축소되지 않는다. 자료 없는 학교는 "준비 중" placeholder — 가상 시험범위 생성 없음.
   */
  @Test
  void publicPage_heroAndRotationUseFixedCampaignSchools_notShowcaseApiEcho() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String html = read("static/eduholic-midterm-2026.html");

    // 캠페인 대상 학교 = 정적 config(신규 API/DTO 없음). 세 학교가 고정 순서로 들어 있다.
    assertThat(html).contains("data-campaign-schools=\"압구정중,신구중,신사중\"");
    assertThat(js).contains("data-campaign-schools");
    assertThat(js).contains("var CAMPAIGN_SCHOOLS");

    // Hero 제목은 CAMPAIGN_SCHOOLS 우선 — Showcase scene 에서 join 하지 않는다.
    assertThat(js).contains("function heroSchools(scenes)");
    assertThat(js).contains("CAMPAIGN_SCHOOLS.length ? CAMPAIGN_SCHOOLS.slice() : uniqueSchools(scenes)");
    assertThat(js).contains("heroSchools(scenes)");
    // 옛 방식(uniqueSchools(scenes) 를 곧장 headline 으로) 이 남아 있으면 안 된다.
    assertThat(js).doesNotContain("var schools = uniqueSchools(scenes);");

    // Showcase / LIVE 는 캠페인 학교 순서로 정렬하고, 자료 없는 학교엔 "준비 중" 카드 1장.
    assertThat(js).contains("function buildDisplayScenes(");
    assertThat(js).contains("function withCampaignFrames(");
    assertThat(js).contains("pending: true");
    assertThat(js).contains("CAMPAIGN_SCHOOLS.forEach(function (school)");

    // 실제 FRAME 이 0 이면 "준비 중"만으로 섹션을 만들지 않는다(기존 계약 유지).
    assertThat(js).contains("if (!realFrames.length || !CAMPAIGN_SCHOOLS.length) return realFrames;");
    assertThat(js).contains("if (!frames.length) return;");

    // "준비 중" scene 은 lesson/문법/문장을 만들어내지 않는다.
    assertThat(js).contains("학교별 시험자료 준비 중");
    assertThat(js).doesNotContain("fake");
    assertThat(js).doesNotContain("placeholder");   // §17 — 더미 filler 금지, "준비 중" 실상태만

    // 자료 없는 FRAME 엔 영상/analyze 를 붙이지 않는다.
    assertThat(js).contains("if (frame.pending || frame.metadataOnly) { ambientForFrame = null; }");
    assertThat(js).contains("if (frame.pending || frame.metadataOnly) {"); // 실제 metadata 전용 frame 포함
  }

  @Test
  void publicPage_neverLeaksToWorkspace_andPopupCtaDestinationHeld() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    assertThat(js).doesNotContain("/problem-generator");
    String popup = read("static/js/eduholic_midterm_popup.js");
    assertThat(popup).contains("/eduholic-midterm-2026.html");
    assertThat(popup).doesNotContain("/problem-generator");
  }

  /**
   * 긴급 bugfix(2026-09) — "오늘은 그만 보기" dismiss 날짜는 한국 달력(Asia/Seoul) 기준이어야
   * 한다. toISOString()(UTC) 은 KST 로 자정이 아니라 오전 9시를 경계로 만들어(전날 밤에 닫으면
   * 다음날 오전까지 안 뜸) 운영자가 "팝업이 안 나온다"고 오해하게 된다.
   */
  @Test
  void popup_dismissDateIsSeoulLocal_notUtc() throws Exception {
    String js = read("static/js/eduholic_midterm_popup.js");

    assertThat(js).contains("Asia/Seoul");
    assertThat(js).contains("function seoulDate(");
    assertThat(js).contains("var today = seoulDate();");
    // 날짜 계산에 raw toISOString().slice(0, 10) 을 그대로 쓰지 않는다(폴백 제외).
    assertThat(js).doesNotContain("var today = new Date().toISOString().slice(0, 10);");
    // 여전히 date-keyed 1일 1회, host/pathname gate 유지.
    assertThat(js).contains("eduholic-midterm-2026-dismissed-date");
    assertThat(js).contains("EDUHOLIC_HOST");
    assertThat(js).contains("LANDING_PATH");
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

  /**
   * bugfix(2026-09) — 자료 Showcase 탭에서 운영자가 BuildIt Wiki 연결 자료의 LIVE 공개(exposure)를
   * 직접 켜고 끌 수 있어야 한다. 기존 서버 API 만 사용하고(GET/PATCH wiki-candidates), OFF 자료도
   * 목록에서 숨기지 않는다. Wiki 원본 재등록 폼이 아니다.
   */
  @Test
  void adminScript_hasWikiCandidateExposureToggle_showsOffCandidates() throws Exception {
    String js = read("static/js/lms_midterm_live.js");

    // 기존 owner-guarded API 그대로 사용.
    assertThat(js).contains("BASE + '/wiki-candidates'");
    assertThat(js).contains("/wiki-candidates/' + encodeURIComponent(c.sourceId) + '/exposure'");
    assertThat(js).contains("method: 'PATCH'");

    // 자료 Showcase 탭에 Wiki 연결 자료 섹션이 새로 렌더된다.
    assertThat(js).contains("function renderWikiCandidateSection(");
    assertThat(js).contains("function wikiCandidateCard(");
    assertThat(js).contains("function patchWikiExposure(");
    assertThat(js).contains("BuildIt Wiki 연결 자료");

    // exposure OFF 자료도 항상 목록에 있어야 다시 켤 수 있다 — render 에서 visible 로 거르지 않는다.
    int secStart = js.indexOf("function renderWikiCandidateSection(");
    int secEnd = js.indexOf("function renderMaterials(", secStart);
    assertThat(secStart).isGreaterThan(-1);
    assertThat(js.substring(secStart, secEnd)).doesNotContain("filter(function (c) { return c && c.visible");

    // 실패 시 목록을 서버 상태로 되돌린다(체크박스 원복).
    assertThat(js).contains("renderMaterials();   // 서버 상태로 되돌린다");

    // 가짜 제목을 만들지 않는다 — 내부 식별자만 보조 표기.
    assertThat(js).contains("Wiki 식별자: ' + c.sourceId");

    // 캐시버스팅.
    String html = read("templates/lms/lms_midterm_live.html");
    assertThat(html).contains("/js/lms_midterm_live.js?v=20260916-1");
    assertThat(html).contains("/css/lms_midterm_live.css?v=20260914");
  }

  /**
   * bugfix(2026-09) — 자료(Wiki source)가 없는 캠페인 학교(pending FRAME)도 그 학교의 공개
   * 출제 경향 분석(analyze)이 있으면 DIGITAL_TWIN scene 을 보여준다. grade 미지정이라 학교만 일치.
   * Digital Twin 선택 = "가장 최근 완료된 시험"(학년도>학기>기말/중간). 이번 시험 유형 우선 아님.
   */
  @Test
  void publicPage_pendingFrameShowsDigitalTwinFromAnalyze() throws Exception {
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    // twinContentUrl 이 grade 미지정(pending)을 허용한다.
    assertThat(js).contains("frame.grade == null || gradeNumber(a.grade) === frame.grade");
    // pending scene 을 scenes 배열에 넣고, early return 없이 아래 twinContentUrl 경로로 이어진다.
    assertThat(js).contains("scenes.push({ el: pend, kind: 'pending'");
    int pendIdx = js.indexOf("scenes.push({ el: pend, kind: 'pending'");
    int twinIdx = js.indexOf("twinContentUrl(frame).then(", pendIdx);
    assertThat(twinIdx).isGreaterThan(pendIdx);
    assertThat(js.substring(pendIdx, twinIdx)).doesNotContain("return article;");
    assertThat(js).contains("data-kind', 'pending'");
    // 여전히 실제 analyze contentUrl 만, 없으면 아무 것도 안 그린다.
    assertThat(js).contains("if (!result || !result.url) return;");
    // pending frame 에는 학원 ambient 영상을 붙이지 않는다.
    assertThat(js).contains("if (frame.pending || frame.metadataOnly) { ambientForFrame = null; }");

    // Digital Twin 선택 = 시험 recency 순. "이번 시험 유형(중간) 우선" 로직은 제거됐다.
    assertThat(js).contains("function examRank(a)");
    assertThat(js).doesNotContain("function matchesExam(a)");
    assertThat(js).doesNotContain("campaignExam.indexOf(t) === 0");
    // 학부모 화면 표기 — 기술 용어 없이 "최근 시험 출제 경향 분석" + "1학기 기말고사 분석 기준" 보조.
    assertThat(js).contains("최근 시험 출제 경향 분석");
    assertThat(js).contains("분석 기준");
    assertThat(js).contains("midterm-live__twin-basis");
    assertThat(css).contains(".midterm-live__twin-basis");
  }

  /**
   * bugfix(2026-09) — (학교, 학년) 별 "이번에 준비 중인 실제 시험"을 명시적으로 결정한다.
   * 명시 안 된 조합은 캠페인 exam_type 만 허용(#232 fail-closed — cross-exam wildcard 아님).
   * exam 문자열은 실제 Wiki 자료와 일치해야 노출(불일치 시 "준비 중" 폴백).
   */
  @Test
  void midtermExamScope_isPerSchoolGradeExplicitPolicy_notWildcard() throws Exception {
    String policy = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/"
            + "midtermcampaign/service/MidtermExamScopePolicy.java"));
    String props = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/config/"
            + "MidtermExamScopeProperties.java"));
    String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

    // 정책 = (기본 scope) ∪ (override scope), candidate 는 그 (학교, 학년) 허용 exam 과 정확 일치만.
    assertThat(policy).contains("gateway.listCandidates(tutor, scope)");
    assertThat(policy).contains(".equals(normalizeExam(row.exam()))");
    assertThat(policy).contains("allowedExam(handle, row.school(), gradeNumber(row.grade())");
    assertThat(policy).doesNotContain("wildcard");
    // Wiki(Python) 정규화 규칙과 동일하게 비교(중간→중간고사, 기말→기말고사).
    assertThat(policy).contains("\"중간\"");
    assertThat(policy).contains("\"기말고사\"");
    assertThat(policy).contains("normalizeSchool");   // "압구정중학교"/"압구정중" 동일 취급

    assertThat(props).contains("app.midterm");
    assertThat(props).contains("examScopeOverrides");
    assertThat(yaml).contains("exam-scope-overrides");
    assertThat(yaml).contains("{ school: \"압구정중\", grade: 3, exam: \"기말고사\" }");
    assertThat(yaml).contains("{ school: \"신사중\", grade: 3, exam: \"기말고사\" }");

    // gateway 는 여전히 scope 당 exact 재검증(#232) — 계약을 느슨하게 만들지 않는다.
    String gateway = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/"
            + "midtermcampaign/service/HttpMidtermWikiSourceGateway.java"));
    assertThat(gateway).contains("!scope.exam().equals(exam)");

    // 서비스 3곳이 정책을 통과(직접 gateway.listCandidates(단일 scope) 호출을 남기지 않는다).
    for (String svc : new String[] { "MidtermWikiSourceService", "MidtermShowcaseService",
        "MidtermWikiSourceAdminService" }) {
      String src = Files.readString(Path.of(
          "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/"
              + "midtermcampaign/service/" + svc + ".java"));
      assertThat(src).contains("examScopePolicy.discover(");
    }
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
    assertThat(js).contains("if (!result || !result.url) return;");
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

    // 인라인 <video> — muted playsinline preload=metadata + player controls(autoplay 차단 대비).
    // loop 하지 않는다: 종료(ended)되면 다음 scene 으로 넘어간다. autoplay 속성이 아니라 engine 이
    // play() 를 호출한다(정책 우회 없음 — 차단되면 controls 로 재생).
    assertThat(js).contains("video.muted = true");
    assertThat(js).doesNotContain("video.loop = true");
    assertThat(js).contains("video.playsInline = true");
    assertThat(js).contains("video.preload = 'metadata'");
    assertThat(js).contains("video.controls = true");
    assertThat(js).doesNotContain(".autoplay = true");
    assertThat(js).contains("addEventListener('ended'");

    // optional scene — 없으면 그냥 없다. 가짜 / 스톡 / 복제 영상 없음.
    // deterministic 배정: specific 없는 FRAME 에만 ambient 를 순서대로, 반복 없이.
    assertThat(js).contains("ambientQueue.shift()");
    assertThat(js).contains("data-kind', 'video'");
    assertThat(js).doesNotContain("stock");

    // offscreen + inactive pause, autoplay reject 안전(§10·§19·§20·§21). embed(YouTube/네이버TV)도
    // viewport 밖이면 멈춘다.
    assertThat(js).contains("function stopAllMedia(");
    assertThat(js).contains("v.pause()");
    assertThat(js).contains("pauseVideo()");
    assertThat(js).contains("about:blank");
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

    // link 하나를 종류에 맞는 scene 으로: 직접 업로드 <video> · YouTube 인라인 플레이어 ·
    // 네이버TV tv.naver.com/embed iframe · embed 불가면 작은 링크 카드(거대한 빈 프레임 아님).
    assertThat(js).contains("function addVideoScene(");
    assertThat(js).contains("function addClipScene(");
    assertThat(js).contains("function addYouTubeScene(");
    assertThat(js).contains("function addNaverTvScene(");
    assertThat(js).contains("function addLinkCardScene(");
    // NAVER embed(tv.naver.com/embed · clip-viewer.naver.com/oembed 쇼츠)는 host 를 가리지 않고
    // provider + embedUrl 만으로 인라인 iframe scene 으로 라우팅한다 — validator 가 이미 좁게
    // allowlist 했으므로 프론트는 clip-viewer 를 링크 카드로 강등하지 않는다.
    assertThat(js).contains("link.provider === 'NAVER' && typeof link.embedUrl === 'string' && link.embedUrl");
    assertThat(js).doesNotContain("data-kind', 'external'");   // 옛 거대 CTA 프레임 제거
    assertThat(js).contains("YT.PlayerState.ENDED");           // YouTube 종료 → 다음 scene
    assertThat(js).contains("youtube.com/iframe_api");
    assertThat(js).contains("video.controls = true");
    assertThat(js).doesNotContain(".autoplay = true");

    // 자동 전환: 영상 재생 중에는 고정 시간(4.5초 등)으로 강제로 넘기지 않는다(§3).
    assertThat(js).contains("function armAdvance(");
    assertThat(js).contains("removeEventListener('ended'");

    // reduced-motion: 자동 전환 없이도 dot 으로 직접 scene 을 선택할 수 있어야 한다(§3).
    assertThat(js).contains("__mtSelectScene");
    assertThat(js).contains("document.createElement('button')");

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

  @Test
  void academyHomeLive_mainFeatureUsesExplicitLinkedVideoWithoutCroppingOrAutoplay() throws Exception {
    String js = read("static/js/eduholic_midterm_home_live.js");
    String html = read("templates/public/academy_public.html");

    // 2026-09-29 MAIN VIDEO 교체 확정: academy_videos 표식 선별(pickMainFeatureLink) 폐기 → 정적 자산 고정.
    assertThat(js).doesNotContain("pickMainFeatureLink");
    assertThat(js).contains("MAIN_FEATURE_VIDEO_SRC = '/video/eduholic/shingu2-jikbo-explainer.mp4'");
    assertThat(js).contains("liveVideosBySchool");
    assertThat(js).contains("scene.video.videoUrl");
    assertThat(js).contains("scene.video.thumbnailUrl");
    assertThat(js).doesNotContain("신구중3_2학기_내신대비_라이브_최종_53초.mp4");
    assertThat(js).contains("aspect-ratio:16/9");
    assertThat(js).contains("object-fit:contain");
    assertThat(js).contains("video.controls = true");
    assertThat(js).contains("video.preload = 'metadata'");
    assertThat(js).doesNotContain("video.autoplay");
    assertThat(js).contains("head.insertAdjacentElement('afterend', feature)");
    assertThat(js).contains("학교별 내신 대비");
    assertThat(html).contains("eduholic_midterm_home_live.js?v=20260929-2");

    // 2026-09-29 시험 전 문구 확정 + 표시 순서(제목 → 설명 → AI 발음 안내 → 동영상).
    assertThat(js).contains("MAIN_FEATURE_TITLE = '신구중2 2학기 중간고사(9월30일) 대비 직보 자료'");
    assertThat(js).contains("MAIN_FEATURE_DESC = '내일(9월30일) 시험 전 마지막으로 확인하는 영어 문제풀이 · 핵심 해설'");
    assertThat(js).contains("AI 한국어 선생님이 영어 발음은 살짝 서툴 수 있어요");
    assertThat(js.indexOf("feature.appendChild(copy)")).isLessThan(js.indexOf("feature.appendChild(media)"));
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
