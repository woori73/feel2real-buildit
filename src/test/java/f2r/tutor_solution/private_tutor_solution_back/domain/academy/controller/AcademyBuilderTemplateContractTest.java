package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Academy Builder 템플릿 계약")
class AcademyBuilderTemplateContractTest {

  @Test
  @DisplayName("given 소개 본문 편집 when contenteditable 활성화 then 히어로 본문의 block 흐름을 유지한다")
  void builder_intro_editing_preserves_heading_then_body_flow() throws Exception {
    // given
    String html = Files.readString(Path.of("src/main/resources/templates/tutor/academy_builder.html"));
    String script = Files.readString(Path.of("src/main/resources/static/js/academy_builder.js"));

    // when & then
    // sec-intro의 h4+본문은 히어로 cv-sub와 같은 값을 두 번 보여주던 중복이라 제거했다.
    // 소개 편집은 히어로 본문 하나로 남으므로 block 흐름 보증도 그쪽을 본다.
    assertThat(script)
        .contains("wireTextEditing");
    // hero-quote 마크업 정본은 academy_sections.js에 있다(빌더 ctx가 편집 앵커를 얹는다).
    assertThat(Files.readString(Path.of("src/main/resources/static/js/academy_sections.js")))
        .contains("className: 'hero-quote'")
        .contains("builder-multiline-text");
    assertThat(html)
        .contains("min-width:40px;cursor:text;")
        .doesNotContain("min-width:40px;display:inline-block;cursor:text;");
  }

  @Test
  @DisplayName("given academy builder template when 읽기 then 텍스트 유틸 스크립트를 builder JS보다 먼저 로드한다")
  void builder_template_loads_text_utils_before_builder_js() throws Exception {
    // given
    Path template = Path.of("src/main/resources/templates/tutor/academy_builder.html");

    // when
    String html = Files.readString(template);

    // then — 섹션 마크업 정본(academy_sections.js)이 소비자보다 먼저 로드되어야 한다.
    assertThat(html)
        .containsSubsequence(
            "<script src=\"/js/academy_builder_text_utils.js\"></script>",
            "<script src=\"/js/academy_sections.js?v=20260831-2\"></script>",
            "<script src=\"/js/academy_builder.js?v=20260830-2\"></script>");
  }

  @Test
  @DisplayName("given academy public surfaces when 읽기 then 공통 사업자 footer helper를 renderer보다 먼저 로드한다")
  void public_surfaces_load_business_footer_contract() throws Exception {
    // given
    String publicHome = Files.readString(Path.of("src/main/resources/templates/public/academy_public.html"));
    String blogList = Files.readString(Path.of("src/main/resources/templates/public/academy_blog_posts.html"));
    String blogDetail = Files.readString(Path.of("src/main/resources/templates/public/academy_blog_post.html"));

    // when & then
    assertThat(publicHome)
        .contains("/css/academy_business_footer.css")
        .containsSubsequence("/js/academy_business_info.js", "/js/academy_public.js");
    assertThat(blogList)
        .contains("id=\"academyBusinessFooter\"")
        .contains("/css/academy_business_footer.css")
        .containsSubsequence("/js/academy_business_info.js", "/js/academy_blog_public.js");
    assertThat(blogDetail)
        .contains("id=\"academyBusinessFooter\"")
        .contains("/css/academy_business_footer.css")
        .containsSubsequence("/js/academy_business_info.js", "/js/academy_blog_public.js");
  }

  @Test
  @DisplayName("given academy builder when 읽기 then footer 7개 값이 다른 섹션과 같은 inline text UX로 배선된다")
  void builder_contains_business_footer_edit_contract() throws Exception {
    // given
    String html = Files.readString(Path.of("src/main/resources/templates/tutor/academy_builder.html"));
    String script = Files.readString(Path.of("src/main/resources/static/js/academy_builder.js"));
    String css = Files.readString(Path.of("src/main/resources/static/css/academy_business_footer.css"));

    // when & then
    assertThat(html)
        .doesNotContain("id=\"builderBusinessInfoModal\"")
        .doesNotContain("builderBusinessInfoUrlInput")
        .containsSubsequence(
            "/js/academy_business_info.js",
            "/js/academy_business_info_editor.js",
            "/js/academy_builder.js");
    assertThat(script)
        .contains("wireBusinessInfoEditing")
        .contains("data-business-info-footer")
        .contains("data-business-info-input")
        .contains("data-business-info-placeholder")
        .contains("contenteditable=\"")
        .contains("builder-footer-controls")
        .contains("data-business-info-action=\"apply\"")
        .doesNotContain("data-business-info-action=\"cancel\"")
        .doesNotContain("renderBusinessInfoForm")
        .contains("BUSINESS_INFO_EDITOR.idle()")
        .contains("BUSINESS_INFO_EDITOR.begin(raw)")
        .contains("BUSINESS_INFO_EDITOR.withFieldValue")
        .contains("BUSINESS_INFO_EDITOR.revertField")
        .contains("BUSINESS_INFO.formatRegistrationNumber")
        .contains("BUSINESS_INFO_EDITOR.beginSaving")
        .contains("aria-busy")
        .contains("BUSINESS_INFO_EDITOR.outsideDecision")
        .contains("outsideDecision === 'apply-allow'")
        .contains("clearActiveSectionSelection")
        .contains("document.addEventListener('click', coordinateBusinessInfoSelection, true)")
        .contains("event.stopImmediatePropagation()")
        .doesNotContain("변경 사항을 적용하거나 취소한 뒤")
        .contains("persistPendingBusinessInfo")
        .contains("BUSINESS_INFO_EDITOR.persistPending")
        .contains("writeRequest: (nextRequest) => writeJsonRequired(PENDING_KEY, nextRequest)")
        .contains("mergePendingBusinessInfoIntoPreview")
        .contains("businessInfo: businessInfo")
        .contains("builder-section-style-selected builder-footer-section-editing")
        .doesNotContain("businessInfoUrl:")
        .doesNotContain("openBusinessInfoModal")
        .contains("/api/tutor/academy/page-settings");
    assertThat(css)
        .contains(".builder-public-preview .academy-business-footer.builder-footer-section-editing")
        .doesNotContain(".academy-business-footer.is-builder-selected")
        .doesNotContain("outline:4px solid #facc15")
        .contains(".academy-business-footer__editable-value")
        .contains(".academy-business-footer__editable-value:empty::before")
        .doesNotContain(".academy-business-footer__fields");
  }

  @Test
  @DisplayName("given academy public renderers when 읽기 then 홈과 블로그가 같은 businessInfo footer를 렌더한다")
  void academy_public_renderers_use_business_info_footer() throws Exception {
    // given
    String homeScript = Files.readString(Path.of("src/main/resources/static/js/academy_public.js"));
    String blogScript = Files.readString(Path.of("src/main/resources/static/js/academy_blog_public.js"));

    // when & then
    assertThat(homeScript)
        .contains("window.F2RAcademyBusinessInfo")
        .contains("BUSINESS_INFO.renderRows")
        .contains("academy.pageSettings && academy.pageSettings.businessInfo");
    assertThat(blogScript)
        .contains("window.F2RAcademyBusinessInfo")
        .contains("renderAcademyBusinessFooter")
        .contains("BUSINESS_INFO.renderRows")
        .contains("academy.pageSettings && academy.pageSettings.businessInfo");
  }

  @Test
  @DisplayName("given academy builder template when 읽기 then inline 텍스트 편집 컨트롤과 SSR JSON marker가 있다")
  void builder_template_contains_text_edit_contract_markers() throws Exception {
    // given
    Path template = Path.of("src/main/resources/templates/tutor/academy_builder.html");

    // when
    String html = Files.readString(template);

    // then
    assertThat(html)
        .contains("id=\"builderLogoModal\"")
        .contains("id=\"builderLogoInput\"")
        .contains("id=\"builderHeroVideoModal\"")
        .contains("id=\"builderHeroVideoInput\"")
        .contains("id=\"builderPublicHandleModal\"")
        .contains("id=\"builderPublicHandleInput\"")
        .contains("id=\"builderPublicHandleStatus\"")
        .contains("id=\"builderTargetGroupsModal\"")
        .contains("id=\"builderSubjectsModal\"")
        .contains("id=\"builderRegionsModal\"")
        .doesNotContain("실행취소")
        .doesNotContain("다시실행")
        .doesNotContain("블록 검색")
        .doesNotContain("id=\"rightPanel\"")
        .doesNotContain("스타일 컨트롤러")
        .doesNotContain("id=\"sectionVisibilityBar\"")
        .doesNotContain("id=\"builderTextEditBar\"")
        .contains("data-builder-panel-tab=\"blocks\"")
        .contains("data-builder-panel-tab=\"layout\"")
        .contains("data-section-order-list")
        .doesNotContain("function switchTab")
        .doesNotContain("> 미리보기")
        .doesNotContain("data-text-edit=\"name\"")
        .doesNotContain("data-basic-edit=\"targetGroups\"")
        .contains("data-academy-json=${academyJson}")
        .doesNotContain("id=\"builderTextModal\"")
        .doesNotContain("id=\"builderTextApplyBtn\"");
  }

  @Test
  @DisplayName("given builder js when 읽기 then 섹션 단위 텍스트 적용 UX marker가 있다")
  void builder_js_contains_section_scoped_text_edit_markers() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("window.F2RBuilderTextUtils")
        .contains("TEXT_UTILS.extractEditableText")
        .contains("function applySectionTextChanges(section, options)")
        .contains("block-controls builder-section-controls")
        .doesNotContain("sectionControlButton('fa-palette', '스타일'")
        .contains("sectionControlButton('fa-check', '적용'")
        .contains("sectionControlButton('fa-trash', '삭제', 'danger'")
        .contains("activeTextEditSection")
        .contains("applyActiveTextSectionBeforeSwitch")
        .contains("reason: 'section-switch'")
        .contains("suppressNoChangeToast: true")
        .contains("if (!enterSectionTextEditMode(section)) return")
        .doesNotContain("openSectionStylePanel")
        .contains("persistTextChangesAfterApply(opts)")
        .contains("setTextTargetsEditable(pageContent, false)")
        .contains("setTextTargetsEditable(section, true)")
        .contains("renderSectionTextApply(section)")
        .contains("TEXT_UTILS.isSingleLineTextTarget(target)")
        .contains("builder-multiline-text");
  }

  @Test
  @DisplayName("given builder js when 읽기 then 섹션 컨트롤은 실제 삭제/적용 동작만 갖는다")
  void builder_js_positions_section_controls_like_demo_block_controls() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains(".builder-section-controls.block-controls")
        .contains("top:-14px")
        .contains("right:8px")
        .contains("z-index:2000")
        .contains("pointer-events:auto")
        .contains("border:1px solid rgba(255,255,255,.2)")
        .contains("font-size:.65rem")
        .contains("padding:3px 7px")
        .contains("border-radius:5px")
        .contains(".block-ctrl-btn.danger:hover{background:#dc2626")
        .contains("builder-section-style-selected{position:relative!important;z-index:900!important;overflow:visible!important")
        .contains("hideSelectedSection(section)")
        .contains("toggleSectionVisibility(sectionId, toggleButton)")
        .doesNotContain("openSelectedSectionStylePanel")
        .doesNotContain("isRightPanelOpen")
        .doesNotContain("window.openRightPanel")
        .doesNotContain("reason: 'section-delete'");
  }

  @Test
  @DisplayName("given builder js when 읽기 then blur 저장과 기존 Enter-to-blur marker가 없다")
  void builder_js_removes_old_blur_commit_and_enter_to_blur_markers() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .doesNotContain("pageContent.addEventListener('blur'")
        .doesNotContain("pageContent.addEventListener(\"blur\"")
        .doesNotContain("commitInlineText(target);")
        .doesNotContain("event.key === 'Enter' && (target.dataset.builderText === 'name' || target.dataset.builderDraft)")
        .doesNotContain("event.key === \"Enter\" && (target.dataset.builderText === \"name\" || target.dataset.builderDraft)");
  }

  @Test
  @DisplayName("given builder js when 읽기 then Academy 수정 요청은 builderDraft를 보존하며 sectionVisibility만 명시 갱신한다")
  void builder_js_preserves_builder_draft_when_updating_section_visibility() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("function academyUpdateRequest(academy)")
        .contains("const pageSettings = Object.assign({}, academy.pageSettings || {})")
        .contains("pageSettings.sectionVisibility = normalizeSectionVisibility(")
        .contains("pageSettings.sectionOrder = sectionOrderFrom(academy)")
        .contains("pageSettings");
  }

  @Test
  @DisplayName("given builder js when 읽기 then 로고/영상/기본정보의 비텍스트 편집 흐름 marker도 유지된다")
  void builder_js_keeps_non_text_flow_markers() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("wireHeroVideoEditing")
        .contains("wireLogoEditing")
        .contains("wireBasicDataEditing")
        .contains("data-builder-logo")
        .contains("builderLogoModal")
        .contains("data-hero-video-edit")
        .contains("heroYoutubeUrl")
        .contains("wirePublicHandleEditing")
        .contains("builderPublicHandleModal")
        .contains("checkPublicHandleAvailabilityFromButton")
        .contains("builderPublicHandleCheckBtn")
        .contains("data-social-url-edit")
        .contains("/api/tutor/academy/handle-available?handle=")
        .contains("wireBasicDataEditing")
        .contains("data-builder-basic=\"subjects\"")
        .doesNotContain("data-builder-basic=\"regions\"")
        .contains("data-featured-lessons-edit")
        .contains("featuredLessonIds")
        .contains("persistPendingBasicData");
  }

  @Test
  @DisplayName("given builder js when 읽기 then 신규 발행 전 섹션 토글을 pending payload에 저장한다")
  void builder_js_persists_pending_section_visibility() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("persistPendingSectionVisibility")
        .contains("request.pageSettings = Object.assign")
        .contains("writeJson(PENDING_KEY, request)")
        .contains("builder-public-preview")
        // sec-social 마크업 정본은 academy_sections.js로 옮겼다(별도 테스트에서 단언).
        // 빌더는 자기 ctx를 얹어 호출만 한다.
        .contains("SECTIONS.renderExternalServices(sectionView(academy), BUILDER_CTX)")
        .doesNotContain("srcdoc")
        .doesNotContain("builder-public-preview-iframe")
        .contains("const publishRequest = publishRequestFromPending() || academyUpdateRequest(currentPreview) || request")
        .contains("const PENDING_LOGO_KEY = 'pendingAcademyLogo'")
        .contains("form.append('academy', new Blob")
        .contains("form.append('logo', draftLogo.blob")
        .contains("data-intro-feature-list")
        .contains("renderLessons(academy)")
        .doesNotContain("로고 이미지는 발행 후 빌더에서 수정할 수 있습니다")
        .contains("persistPendingTextChanges")
        .contains("scrollPreviewToSection")
        .contains("data-builder-text")
        .contains("commitInlineText")
        .contains("wireHeroVideoEditing")
        .contains("wireLogoEditing")
        .contains("data-builder-logo")
        .contains("builderLogoModal")
        .contains("heroYoutubeUrl")
        .contains("wirePublicHandleEditing")
        .contains("builderPublicHandleModal")
        .contains("youtubeEmbedUrl")
        .contains("data-hero-video-edit")
        .contains("wireBasicDataEditing")
        .contains("persistPendingBasicData")
        .contains("wireBasicDataEditing")
        .contains("data-builder-basic=\"subjects\"")
        .doesNotContain("data-builder-basic=\"regions\"")
        .contains("wireFeaturedLessonsEditing")
        .contains("builderFeaturedLessonsModal")
        // 배지 마크업 정본은 academy_sections.js로 옮겼다(아래 모듈 테스트에서 단언).
        // 빌더에는 편집 배선만 남는다.
        .contains("wireBasicDataEditing")
        .contains("targetGroups")
        .contains("subjectIds")
        .contains("regions");
  }

  @Test
  @DisplayName("given 섹션 정본 모듈 when 읽기 then sec-social 마크업이 한 벌만 있고 두 화면 차이는 ctx가 흡수한다")
  void academy_sections_module_owns_sec_social_markup() throws Exception {
    // given — 두 렌더러가 같은 섹션을 각자 하드코딩하던 것을 공용 모듈 한 벌로 합쳤다.
    Path sections = Path.of("src/main/resources/static/js/academy_sections.js");
    Path publicJs = Path.of("src/main/resources/static/js/academy_public.js");
    Path builderJs = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String module = Files.readString(sections);
    String publicScript = Files.readString(publicJs);
    String builderScript = Files.readString(builderJs);

    // then — 마크업과 표시 판정은 모듈이 소유한다
    assertThat(module)
        .contains("<section id=\"sec-social\"")
        .contains("social-card-header")
        .contains("view.sectionVisible('sec-social')")
        // 카드 래퍼는 화면마다 태그가 다르다. 공개는 링크, 빌더는 편집 진입점이라 div다.
        .contains("wrapSocialCard")
        .contains("target=\"_blank\" rel=\"noopener\"")
        .contains("data-social-url-edit");

    // then — 소비자 쪽에는 sec-social 마크업이 남아 있지 않다(중복 부활 방지)
    assertThat(publicScript).doesNotContain("<section id=\"sec-social\"");
    assertThat(builderScript).doesNotContain("<section id=\"sec-social\"");

    // then — 공개 페이지에는 빌더 편집 앵커가 새지 않는다
    assertThat(publicScript).doesNotContain("data-social-url-edit");

    // then — 히어로 마크업도 모듈이 소유한다. 편집 앵커는 빌더 ctx에만 있다.
    assertThat(module)
        .contains("<section class=\"hero\"")
        .contains("hero-grid")
        .contains("tablet-screen")
        .contains("cv-hero-target-badges")
        .contains("cv-hero-subject-badges")
        // 배지 편집 앵커는 ctx가 필드명으로 조립한다(리터럴이 아니다).
        .contains("badgeRowAttrs('targetGroups')")
        .contains("badgeRowAttrs('subjects')")
        .contains("data-builder-basic=")
        .contains("data-builder-text=\"name\"")
        .contains("data-builder-draft=")
        .contains("scrollToId('sec-lessons')");
    assertThat(publicScript)
        .doesNotContain("<section class=\"hero\"")
        .doesNotContain("data-builder-basic=")
        .doesNotContain("data-builder-text=");
  }

  @Test
  @DisplayName("given public page js when 읽기 then 외부연동은 URL 유무가 아니라 sec-social 표시 상태로 렌더된다")
  void academy_public_js_renders_external_services_by_section_visibility_only() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_public.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("renderExternalServices(academy)")
        .contains("SECTIONS.renderLessons(sectionView(academy), PUBLIC_CTX)")
        // V8 시안에서 히어로 CTA('수업상품 보기')가 빠지고 대표 수업 패널이 그 통로를 맡는다.
        // sec-social 마크업 정본은 academy_sections.js로 옮겼다(아래에서 따로 단언).
        // 공개 페이지는 자기 ctx를 얹어 호출만 한다.
        .contains("SECTIONS.renderExternalServices(sectionView(academy), PUBLIC_CTX)")
        .contains("SECTIONS.renderHero(sectionView(academy), PUBLIC_CTX)")
        .contains("renderLogoSymbol(academy)")
        .doesNotContain("!pageSettings.blogUrl && !pageSettings.storeUrl")
        .doesNotContain("네이버 블로그 ↗")
        .doesNotContain("네이버 지도 ↗")
        .doesNotContain("const extBtns")
        .doesNotContain("const secondary = 'cv-btn-ghost'")
        .doesNotContain("builderPreview")
        // 카드는 배너 한 장이다. 썸네일 프리뷰와 중복 액션 버튼(지도 보기/예약·문의가 같은 URL)을 지웠고,
        // 히어로 보조 CTA('학원 소개 보기')도 함께 빠져 ghost 버튼 클래스가 고아가 됐다.
        .doesNotContain("blogPreviewThumbnail")
        .doesNotContain("mapPreviewThumbnail")
        .doesNotContain("예약·문의")
        .doesNotContain("cv-cta-ghost");
  }

  @Test
  @DisplayName("given public academy template when 읽기 then FontAwesome 없이 자체 자산만 로드한다")
  void academy_public_template_drops_fontawesome() throws Exception {
    // given — Build It 시안은 외부 아이콘 없이 이모지/CSS로만 그린다.
    Path template = Path.of("src/main/resources/templates/public/academy_public.html");

    // when
    String html = Files.readString(template);

    // then
    assertThat(html)
        .doesNotContain("font-awesome")
        .contains("/css/academy_public.css")
        .contains("/js/academy_public.js");

    // then — 링크만 지우고 fa- 클래스를 남기면 발행 페이지에 깨진 아이콘 자리가 남는다.
    // 공개 경로로 렌더되는 모든 소스가 FontAwesome-free여야 한다.
    for (String file : new String[] {
        "src/main/resources/static/js/academy_sections.js",
        "src/main/resources/static/js/academy_public.js",
        "src/main/resources/static/js/lesson_product_showcase.js",
        "src/main/resources/static/css/academy_public.css"
    }) {
      assertThat(Files.readString(Path.of(file)))
          .as("%s", file)
          .doesNotContain("fa-solid")
          .doesNotContain("font-awesome");
    }
  }

  @Test
  @DisplayName("given builder template when 읽기 then 추가예정 섹션은 모두 OFF이며 조작할 수 없다")
  void builder_template_disables_future_sections() throws Exception {
    // given
    Path template = Path.of("src/main/resources/templates/tutor/academy_builder.html");

    // when
    String html = Files.readString(template);

    // then
    assertThat(html)
        // Academy 데이터 섹션 순서: hero → featured → AI Native 교육 과정 → intro → principal → lessons → social
        .containsSubsequence(
            "data-section-id=\"sec-hero\"",
            "data-section-toggle=\"sec-featured\"",
            "data-section-toggle=\"sec-digitaltwin\"",
            "data-section-toggle=\"sec-intro\"",
            "data-section-toggle=\"sec-principal\"",
            "data-section-toggle=\"sec-lessons\"",
            "data-section-toggle=\"sec-social\"")
        .contains("수업상품<br/>강의 목록")
        // 원장소개(sec-principal)는 is-data-backed 데이터 섹션 카드다
        .contains("is-data-backed is-section-toggle\" data-section-id=\"sec-principal\"")
        // Education Storefront 신규 섹션은 데이터 섹션 카드이며 기본 OFF(opt-in)로 렌더된다
        .contains("is-storefront-optin is-toggle-off\" data-section-id=\"sec-featured\"")
        .contains("is-storefront-optin is-toggle-off\" data-section-id=\"sec-digitaltwin\"")
        .contains("data-section-id=\"sec-curriculum\"")
        .contains("커리큘럼<br/>관리")
        .contains("data-section-id=\"sec-effect\"")
        .contains("data-section-id=\"sec-market\"")
        .contains("data-section-id=\"sec-payment\"")
        .doesNotContain("data-section-toggle=\"sec-curriculum\"")
        .doesNotContain("data-section-toggle=\"sec-effect\"")
        .doesNotContain("data-section-toggle=\"sec-market\"")
        .doesNotContain("data-section-toggle=\"sec-payment\"")
        .contains("is-future-disabled is-toggle-off")
        .contains("aria-disabled=\"true\"")
        .contains("section-toggle-state\">OFF")
        .contains("pointer-events:none")
        .doesNotContain("상담 신청 폼 저장 기능은 추가 예정입니다")
        .doesNotContain("이미지/영상/FAQ 자유 콘텐츠 블록은 추가 예정입니다");
  }

  @Test
  @DisplayName("given builder/public js when 읽기 then 원장 소개 섹션을 실제 렌더한다(placeholder 아님)")
  void builder_and_public_js_render_principal_section() throws Exception {
    // given
    Path builderJs = Path.of("src/main/resources/static/js/academy_builder.js");
    Path publicJs = Path.of("src/main/resources/static/js/academy_public.js");

    // when
    String builder = Files.readString(builderJs);
    String publicScript = Files.readString(publicJs);

    // then: 두 렌더러 모두 renderPrincipal + YouTube 영상 + 가변 약력 배열을 구현
    assertThat(builder)
        .contains("function renderPrincipal(academy)")
        .contains("wirePrincipalVideoEditing")
        .doesNotContain("대표 원장 프로필과 소개 영상이 준비 중입니다");
    // 마크업 정본은 academy_sections.js가 갖는다.
    assertThat(Files.readString(Path.of("src/main/resources/static/js/academy_sections.js")))
        .contains("principalYoutubeUrl")
        .contains("principalCareer")
        .contains("data-principal-career-item");
    assertThat(publicScript)
        .contains("function renderPrincipal(academy)");
  }

  @Test
  @DisplayName("given builder template when 읽기 then 수강 후기(sec-reviews)는 비활성 future 카드다")
  void builder_template_has_disabled_reviews_block() throws Exception {
    // given
    Path template = Path.of("src/main/resources/templates/tutor/academy_builder.html");

    // when
    String html = Files.readString(template);

    // then
    assertThat(html)
        .contains("data-section-id=\"sec-reviews\"")
        .contains("is-coming-soon is-future-disabled is-toggle-off")
        .doesNotContain("data-section-toggle=\"sec-reviews\"")
        .doesNotContain("data-section-key=\"sec-reviews\"")
        .contains("수강<br/>후기");
  }

  @Test
  @DisplayName("given public page js when 읽기 then 미래 섹션은 렌더 경로와 더미 데이터에서 제거된다")
  void public_js_removes_future_renderers() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_public.js");

    // when
    String content = Files.readString(js);

    assertThat(content)
        .contains("FUTURE_SECTION_IDS.has(id)")
        .doesNotContain("function renderReviews")
        .doesNotContain("DUMMY_REVIEWS")
        .doesNotContain("review-dev-badge");
  }

  @Test
  @DisplayName("given builder js when 읽기 then 미래 섹션 렌더러는 제거되고 순서 호환 키만 유지된다")
  void builder_js_removes_future_renderers() throws Exception {
    // given: 빌더는 academy_public.js와 별개의 자체 renderSectionById를 갖는다.
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String content = Files.readString(js);

    assertThat(content)
        .contains("FUTURE_SECTION_IDS")
        .contains("'sec-reviews'")
        .doesNotContain("function renderReviews")
        .doesNotContain("DUMMY_REVIEWS")
        .doesNotContain("review-dev-badge");
  }

  @Test
  @DisplayName("given public css when 읽기 then 제거된 후기 캐러셀 스타일이 없다")
  void public_css_removes_reviews_carousel_styles() throws Exception {
    // given
    Path css = Path.of("src/main/resources/static/css/academy_public.css");

    // when
    String content = Files.readString(css);

    // then
    assertThat(content)
        .doesNotContain(".reviews-track")
        .doesNotContain("@keyframes reviewsScroll")
        .doesNotContain(".review-card")
        .doesNotContain(".review-dev-badge");
  }


  @Test
  @DisplayName("given builder section renderer when 읽기 then 섹션 이름이 중복돼도 canonical key로 토글/스크롤한다")
  void builder_section_renderer_uses_canonical_section_key() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("const MANAGED_SECTION_IDS")
        .contains("const MANAGED_SECTIONS = new Set(MANAGED_SECTION_IDS)")
        .contains("button.dataset.sectionKey = sectionId")
        .contains("toggleSectionVisibility(button.dataset.sectionKey, button)")
        .contains("pageContent.querySelector('[data-builder-section-key=\"' + cssAttrEscape(canonical) + '\"]')")
        .contains("canonicalSectionId(sectionId)");
  }


  @Test
  @DisplayName("given builder preview section click when 읽기 then 스타일 패널 없이 실제 편집 섹션만 선택한다")
  void builder_js_selects_edit_section_without_style_panel() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("wirePreviewSectionSelection()")
        .contains("pageContent.addEventListener('click'")
        .contains("isActualEditTarget(event.target)")
        .contains("event.target.closest('[data-builder-section-key]')")
        .contains("enterSectionTextEditMode(section)")
        .contains("builder-section-style-selected")
        .contains("[data-builder-logo]")
        .contains("[data-builder-basic]")
        .contains("[data-hero-video-edit]")
        // GNB/빈 영역 클릭 시 선택 해제(섹션 컨트롤이 sticky nav에 남는 버그 방지)
        .contains("clearActiveSectionSelection()")
        .contains("function clearActiveSectionSelection()")
        .doesNotContain("openRightPanel")
        .doesNotContain("rightPanel");
  }

  @Test
  @DisplayName("given builder js when 읽기 then GNB 학원명은 히어로와 별개로 편집되고 텍스트 유틸 미로드 시 즉시 실패한다")
  void builder_js_edits_gnb_title_independently_and_fails_fast_without_text_utils() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/academy_builder.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        // GNB 로고 타이틀: 학원명을 비추는 대신 headerTitle draft로 따로 편집한다.
        // name에 직접 묶으면 히어로 제목까지 같이 바뀌므로 그 배선은 금지다.
        .contains("<b class=\"eh-logo-title\" data-builder-draft=\"headerTitle\"")
        .doesNotContain("<b class=\"eh-logo-title\" data-builder-text=\"name\"")
        .doesNotContain("data-builder-text-mirror")
        // 히어로의 편집 가능한 학원명은 그대로 유지 (sec-intro의 학원명 반복은 과목·특징으로 교체됨)
        .contains("[data-builder-text=\"' + field + '\"]")
        .doesNotContain("<h3 data-builder-text=\"name\"")
        // 스크립트 로드 순서 회귀는 초기화 시점에 바로 터진다
        .contains("if (!window.F2RBuilderTextUtils)")
        .contains(
            "throw new Error('F2RBuilderTextUtils must be loaded before academy_builder.js')");
  }

  @Test
  @DisplayName("given 3-B Horizontal Content Collection when 자산/배선을 확인 then 공통 shell 한 벌 + opt-in draft 키만 쓴다")
  void horizontal_content_collection_shares_one_shell_and_is_opt_in() throws Exception {
    // given
    String publicHtml = Files.readString(Path.of("src/main/resources/templates/public/academy_public.html"));
    String builderHtml = Files.readString(Path.of("src/main/resources/templates/tutor/academy_builder.html"));
    String sections = Files.readString(Path.of("src/main/resources/static/js/academy_sections.js"));
    String builderJs = Files.readString(Path.of("src/main/resources/static/js/academy_builder.js"));
    String service = Files.readString(Path.of(
        "src/main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java"));

    // when & then — 배선 모듈은 공개 템플릿에만, 정본 모듈보다 먼저 로드된다.
    assertThat(publicHtml)
        .contains("/css/academy_collection.css")
        .containsSubsequence(
            "/js/academy_collection.js", "/js/academy_sections.js", "/js/academy_public.js");
    assertThat(builderHtml).contains("/css/academy_collection.css");

    // then — 공통 shell 한 벌 + Program/Video 는 그 위의 card type(별도 캐러셀 아님).
    assertThat(sections)
        .contains("function renderCollectionShell(")
        .contains("function programCardHtml(")
        .contains("function videoCardHtml(")
        .contains("isHorizontalCollection(view, 'lessonsLayout')")
        .contains("isHorizontalCollection(view, 'videosLayout')");

    // then — opt-in draft 키만 서버 화이트리스트에 추가(sectionVisibility/sectionOrder 는 불변).
    assertThat(service).contains("\"lessonsLayout\"").contains("\"videosLayout\"");

    // then — 빌더는 섹션 편집 컨트롤에서 레이아웃을 토글한다.
    assertThat(builderJs)
        .contains("COLLECTION_LAYOUT_KEYS")
        .contains("toggleCollectionLayout")
        .contains("appendCollectionLayoutControl(controls, section)");

    // then — 자동 캐러셀/등장 애니메이션 없음(Story 3-A 와 분리).
    String collectionJs = Files.readString(Path.of("src/main/resources/static/js/academy_collection.js"));
    assertThat(collectionJs).doesNotContain("setInterval").contains("track.scrollBy(");
  }

  @Test
  @DisplayName("given landing publish js when 읽기 then 주력 과목 복수 subjectIds를 수집한다")
  void landing_publish_js_collects_multiple_subject_ids() throws Exception {
    // given
    Path js = Path.of("src/main/resources/static/js/landing_publish.js");

    // when
    String script = Files.readString(js);

    // then
    assertThat(script)
        .contains("selectedSubjectLabels()")
        .contains("const subjectEntries = selectedSubjects")
        .contains("subjectIds: subjectEntries.map")
        .contains("subjects: subjectEntries.map");
  }

}
