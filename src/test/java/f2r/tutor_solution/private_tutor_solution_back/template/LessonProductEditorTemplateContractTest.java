package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class LessonProductEditorTemplateContractTest {

  private static final Path TEMPLATE =
      Path.of("src/main/resources/templates/tutor/lesson_product_editor.html");
  private static final Path CSS =
      Path.of("src/main/resources/static/css/lesson_product_editor.css");
  private static final Path CONTROLLER =
      Path.of("src/main/resources/static/js/lesson_product_editor.js");

  @Test
  void editorUsesReferenceSplitShellWithOneFormInsideTheSidebar() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when
    Element app = document.selectFirst("body > .app");
    Element editor = document.selectFirst("body > .app > aside.editor");

    // then
    assertThat(app).isNotNull();
    assertThat(editor).isNotNull();
    assertThat(document.select("form")).hasSize(1);
    assertThat(editor.select("form#lessonProductForm")).hasSize(1);
  }

  @Test
  void previewKeepsCourseTabsOutsideTheHideableContent() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when
    Element shell = document.selectFirst("body > .app > main.preview-wrap > article.preview");
    Element tabs = document.selectFirst("article.preview > nav#courseTabs.course-nav");
    Element content = document.selectFirst("article.preview > #lessonPreview");
    Element hero = document.selectFirst("#lessonPreview > section#selectedHero.selected-hero");

    // then
    assertThat(shell).isNotNull();
    assertThat(tabs).isNotNull();
    assertThat(tabs.attr("aria-label")).isEqualTo("수업상품 선택");
    assertThat(content).isNotNull();
    assertThat(tabs.closest("#lessonPreview")).isNull();
    assertThat(hero).isNotNull();
    assertThat(tabs.elementSiblingIndex()).isLessThan(content.elementSiblingIndex());
  }

  @Test
  void addCourseControlIsAlwaysAppendedLastWithoutAnItemCountLimit() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));
    String controller = Files.readString(CONTROLLER);

    // when
    Element add = document.selectFirst("#courseTabs > #addCourseTab[data-add-course=true]:last-child");

    // then
    assertThat(document.select("#addCourseTab")).hasSize(1);
    assertThat(add).isNotNull();
    // 다른 탭과 같은 small(안내) + strong(제목) 2줄 구조를 쓴다.
    assertThat(add.children()).hasSize(2);
    assertThat(add.selectFirst("small").text()).isEqualTo("새 수업");
    assertThat(add.selectFirst("strong").text()).isEqualTo("+ 추가하기");
    assertThat(document.select("aside.editor #addCourseTab, aside.editor [data-add-course=true]")).isEmpty();
    String renderTabs = functionBody(controller, "renderTabs");
    assertThat(renderTabs)
        .contains("tabs.appendChild(addButton);")
        .doesNotContain("items.length < 5", "items.length >= 5", "addButton.hidden");
  }

  @Test
  void sixOrMoreTabsSwitchToOneRowHorizontalOverflowInsteadOfCappingProducts()
      throws Exception {
    // given
    String controller = Files.readString(CONTROLLER);
    String css = compact(Files.readString(CSS));

    // when
    String renderTabs = functionBody(controller, "renderTabs");
    String overflowRule = cssRule(css, ".course-nav.is-overflowing");

    // then
    assertThat(renderTabs)
        .contains("tabs.classList.toggle('is-overflowing'")
        .contains("editorState.items.length + 1 > 5");
    assertThat(overflowRule)
        .contains("overflow-x:auto")
        .contains("grid-auto-flow:column")
        .contains("grid-auto-columns:minmax(");
  }

  @Test
  void overflowingTabsRevealTheActiveTabAfterRenderWithoutScrollingNormalTabs()
      throws Exception {
    // given
    String controller = Files.readString(CONTROLLER);

    // when
    String renderTabs = compact(functionBody(controller, "renderTabs"));

    // then
    assertThat(renderTabs)
        .contains("button.classList.add('is-active')")
        .contains("if(tabs.classList.contains('is-overflowing')){")
        .contains("tabs.querySelector('.course-tab.is-active')?.scrollIntoView({block:'nearest',inline:'nearest'});");
    assertThat(renderTabs.indexOf("scrollIntoView"))
        .isGreaterThan(renderTabs.indexOf("if(tabs.classList.contains('is-overflowing')){"));
  }

  @Test
  void previewKeepsTheReferenceMediaOtAndThreeExplanationPlaceholders() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when
    Element hero = document.selectFirst("#lessonPreview > .selected-hero");
    Element detail = document.selectFirst("#lessonPreview > .detail-section");

    // then
    assertThat(hero).isNotNull();
    assertThat(hero.selectFirst(".course-media-area .course-media-placeholder")).isNotNull();
    assertThat(detail).isNotNull();
    assertThat(detail.selectFirst(".ot-panel .ot-placeholder")).isNotNull();
    assertThat(detail.select(".explain-list > .explain-card[data-preview-field]")).hasSize(3);
    assertThat(detail.select("[data-preview-field=explanation1]")).hasSize(1);
    assertThat(detail.select("[data-preview-field=explanation2]")).hasSize(1);
    assertThat(detail.select("[data-preview-field=explanation3]")).hasSize(1);
    assertThat(detail.select(".explain-list ul, .explain-list ol, .explain-list li")).isEmpty();
  }

  @Test
  void previewMediaSlotsOpenTheModalWithIndependentTargets() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));
    String controller = Files.readString(CONTROLLER);

    // when
    Element heroMedia = document.selectFirst("button#courseMediaArea[type=button]");
    Element otMedia = document.selectFirst("button#otMediaArea[type=button]");
    Element sidebar = document.selectFirst("aside.editor");
    Element modalClear = document.selectFirst(
        "dialog#mediaModal button#clearSelectedMediaButton[type=button]");

    // then
    assertThat(heroMedia).isNotNull();
    assertThat(otMedia).isNotNull();
    assertThat(sidebar.select(
        "#mediaSummary, #otMediaSummary, #openMediaModal, #openOtMediaModal, #clearMediaButton, #clearOtMediaButton"))
        .isEmpty();
    assertThat(modalClear).isNotNull();
    assertThat(modalClear.hasAttr("hidden")).isTrue();
    assertThat(heroMedia.attr("aria-label")).contains("등록");
    assertThat(otMedia.attr("aria-label")).contains("등록");
    assertThat(controller)
        .contains("mediaArea?.addEventListener('click', () => openMediaPicker('hero'))")
        .contains("otMediaArea?.addEventListener('click', () => openMediaPicker('ot'))")
        .contains("clearSelectedMediaButton?.addEventListener('click', clearSelectedMedia)")
        .contains("setOtVideo(Number(button.dataset.videoId))");
  }

  @Test
  void explanationCardsDoNotInjectDecorativeBullets() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    assertThat(css).doesNotContain(".explain-card::before");
  }

  @Test
  void detailHeadingUsesTealForBothLinesAndMapsTheSecondLineToTabShortName() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));
    String css = compact(Files.readString(CSS));

    // when
    Element heading = document.selectFirst("#lessonPreview .detail-heading > h2");

    // then
    assertThat(heading).isNotNull();
    assertThat(heading.select("> span[data-preview-field=coreDetailTitle]")).hasSize(1);
    assertThat(heading.select("> span[data-preview-field=tabShortName]")).hasSize(1);
    assertThat(heading.select("> span[data-preview-field=name]")).isEmpty();
    assertThat(css).contains(".detail-headingh2span{color:var(--detail-accent)");
  }

  @Test
  void previewOmitsTheNonReferenceMetadataStrip() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when & then
    assertThat(document.select("#lessonPreview .lesson-meta-strip")).isEmpty();
  }

  @Test
  void sidebarOmitsNonReferenceTopChrome() throws Exception {
    // given
    String html = Files.readString(TEMPLATE);
    Document document = Jsoup.parse(html);

    // when
    Element editor = document.selectFirst("aside.editor");

    // then
    assertThat(editor).isNotNull();
    assertThat(editor.select(
        ".editor-header-nav, .back-link, .account-menu, .editor-eyebrow, .panel-eyebrow"
    )).isEmpty();
    assertThat(html)
        .doesNotContain("_account_menu")
        .doesNotContain("back-link")
        .doesNotContain("editor-eyebrow")
        .doesNotContain("panel-eyebrow");
  }

  @Test
  void successfulSaveAlertsThenReturnsToAcademyManagement() throws Exception {
    // given
    String controller = Files.readString(CONTROLLER);

    // when
    String saveCollection = functionBody(controller, "saveCollection");

    // then
    assertThat(saveCollection)
        .contains("alert('수업상품을 저장했습니다.');")
        .contains("location.assign('/tutor/academy');");
    assertThat(saveCollection.indexOf("alert('수업상품을 저장했습니다.');"))
        .isLessThan(saveCollection.indexOf("location.assign('/tutor/academy');"));
    assertThat(saveCollection.indexOf("reconcileSaveSuccess"))
        .isLessThan(saveCollection.indexOf("alert('수업상품을 저장했습니다.');"));
  }

  @Test
  void editorKeepsAccessibleCollectionControlsAndFieldLimits() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when & then
    assertThat(document.selectFirst("#deleteLessonButton")).isNotNull();
    assertThat(document.selectFirst("#saveLessonsButton[type=submit]")).isNotNull();
    assertThat(document.selectFirst("#editorStatus[role=status][aria-live=polite]")).isNotNull();
    assertThat(document.selectFirst("#editorErrors[role=alert][aria-live=assertive]")).isNotNull();
    assertThat(document.selectFirst("#name").attr("maxlength")).isEqualTo("100");
    assertThat(document.selectFirst("#schoolName").attr("maxlength")).isEqualTo("100");
    assertThat(document.selectFirst("#gradeLabel").attr("maxlength")).isEqualTo("50");
    assertThat(document.selectFirst("#priceAmount").attr("min")).isEqualTo("1");
    assertThat(document.selectFirst("#tabGuidanceLabel").attr("maxlength")).isEqualTo("120");
    assertThat(document.selectFirst("#tabShortName").attr("maxlength")).isEqualTo("80");
    assertThat(document.selectFirst("#kicker").attr("maxlength")).isEqualTo("200");
    assertThat(document.selectFirst("#highlight").attr("maxlength")).isEqualTo("300");
    assertThat(document.selectFirst("#learnerNeed").attr("maxlength")).isEqualTo("300");
    assertThat(document.selectFirst("#coreDetailTitle").attr("maxlength")).isEqualTo("200");
    assertThat(document.selectFirst("#explanation1").attr("maxlength")).isEqualTo("500");
    assertThat(document.selectFirst("#explanation2").attr("maxlength")).isEqualTo("500");
    assertThat(document.selectFirst("#explanation3").attr("maxlength")).isEqualTo("500");
    assertThat(document.select("label[for=theme] .required-mark")).hasSize(1);
    assertThat(document.select("label[for=name] .required-mark")).hasSize(1);
    assertThat(document.select("label .required-mark")).hasSize(2);
  }

  @Test
  void editorCollectsManageMetadataAndKeepsUnownedLegacyFieldsOut() throws Exception {
    // given — LMS 강좌관리 표에 연결할 학교·학년·가격은 편집기가 직접 받는다.
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when & then
    for (String owned : new String[] {
        "name", "description", "tabGuidanceLabel", "tabShortName", "kicker", "highlight",
        "learnerNeed", "coreDetailTitle", "explanation1", "explanation2", "explanation3",
        "theme", "schoolName", "gradeLabel", "priceAmount", "priceUnit"}) {
      assertThat(document.selectFirst("#" + owned))
          .withFailMessage("편집기가 소유한 필드 %s 가 폼에 있어야 한다", owned)
          .isNotNull();
    }

    assertThat(document.select("#priceUnit option").eachAttr("value"))
        .containsExactly("", "MONTH", "COURSE", "NEGOTIABLE");

    // 정원·일정·분류·공개 상태는 편집기에서 받지 않고 서버로 null이 나간다.
    for (String removed : new String[] {
        "lessonType", "targetGroup", "status", "priceSummary",
        "maxCapacity", "scheduleType", "weeklyCount", "startDate", "endDate",
        "durationWeeks", "durationMonths", "scheduleSummary"}) {
      assertThat(document.selectFirst("#" + removed))
          .withFailMessage("편집기가 받지 않는 필드 %s 가 폼에 남아 있다", removed)
          .isNull();
    }
    assertThat(document.select("[name=weekdays]")).isEmpty();
  }

  @Test
  void disabledAiControlRemainsVisibleWithoutAnActivationHandler() throws Exception {
    // given
    String html = Files.readString(TEMPLATE);
    Document document = Jsoup.parse(html);

    // when
    Element ai = document.selectFirst("button#aiAssistButton");
    String controller = Files.readString(CONTROLLER);

    // then
    assertThat(ai).isNotNull();
    assertThat(ai.hasAttr("disabled")).isTrue();
    assertThat(ai.attr("aria-disabled")).isEqualTo("true");
    assertThat(ai.hasAttr("onclick")).isFalse();
    assertThat(controller).doesNotContain("aiAssistButton", "aiAssist", "aiButton");
  }

  @Test
  void editorLoadsStateBeforeControllerAndExcludesSampleAndJsonSurfaces()
      throws Exception {
    // given
    String html = Files.readString(TEMPLATE);

    // when
    int stateScript = html.indexOf("/js/lesson_product_editor_state.js");
    int editorScript = html.indexOf("/js/lesson_product_editor.js");

    // then
    assertThat(stateScript).isGreaterThan(0);
    assertThat(editorScript).isGreaterThan(stateScript);
    // 파일 입력 금지 조항은 대표 미디어 업로드가 구현되면서 해제됐다. 아래
    // heroMediaUsesHiddenFieldsInsideTheSingleForm이 그 자리를 대신 지킨다.
    assertThat(html.toLowerCase())
        .doesNotContain("application/json")
        .doesNotContain("samplebutton")
        .doesNotContain("sample-select")
        .doesNotContain("jsonbutton")
        .doesNotContain("onclick=\"");
  }

  @Test
  void heroMediaUsesHiddenFieldsInsideTheSingleForm() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when
    Element form = document.selectFirst("form#lessonProductForm");
    Element imageKey = document.selectFirst("input#mediaImageKey");
    Element videoId = document.selectFirst("input#mediaVideoId");
    Element otVideoId = document.selectFirst("input#otVideoId");

    // then
    // hidden input이 폼 안에 있어야 readForm()이 집어간다. 밖에 있으면 dirty 추적과
    // 저장 요청 양쪽에서 조용히 누락된다.
    assertThat(imageKey).isNotNull();
    assertThat(videoId).isNotNull();
    assertThat(otVideoId).isNotNull();
    assertThat(imageKey.attr("type")).isEqualTo("hidden");
    assertThat(videoId.attr("type")).isEqualTo("hidden");
    assertThat(otVideoId.attr("type")).isEqualTo("hidden");
    assertThat(form.select("input#mediaImageKey")).hasSize(1);
    assertThat(form.select("input#mediaVideoId")).hasSize(1);
    assertThat(form.select("input#otVideoId")).hasSize(1);

    // 파일 선택은 모달 안에서만 일어난다. 폼 안에 두면 컬렉션 저장 요청에 파일이 딸려간다.
    assertThat(form.select("input[type=file]")).isEmpty();
    assertThat(document.select("dialog#mediaModal input[type=file]")).hasSize(2);
  }

  @Test
  void tabsRenderOnlyAppliedPreviewCopyWithoutStatusBadges() throws Exception {
    // given
    String controller = Files.readString(CONTROLLER);
    Document document = Jsoup.parse(Files.readString(TEMPLATE));

    // when
    String renderTabs = functionBody(controller, "renderTabs");

    // then
    assertThat(renderTabs)
        .contains("item.appliedPreview")
        .contains("tabGuidanceLabel")
        .contains("tabShortName")
        .doesNotContain("view.draft", "item.draft", "course-tab-state");
    assertThat(controller).doesNotContain("className = 'course-tab-state'");
    assertThat(document.select(".course-tab-state")).isEmpty();
  }

  @Test
  void draftInputImmediatelyUpdatesTabCopyAndPreview() throws Exception {
    // given — 예시 HTML처럼 입력 즉시 우측 미리보기와 탭 문구가 따라간다.
    String controller = Files.readString(CONTROLLER);

    // when
    String captureDraft = functionBody(controller, "captureDraft");

    // then
    assertThat(captureDraft)
        .contains("updateActiveDraft", "applyActivePreview", "renderTabs()", "renderPreview()");
    assertThat(captureDraft.indexOf("applyActivePreview"))
        .isGreaterThan(captureDraft.indexOf("updateActiveDraft"));
    // 별도의 "미리보기 반영" 단계는 더 이상 없다.
    assertThat(controller).doesNotContain("function applyPreview(");
  }

  @Test
  void deleteControlRemovesSelectedLessonWithConfirmation() throws Exception {
    // given
    String controller = Files.readString(CONTROLLER);

    // when
    String body = functionBody(controller, "deleteActiveLesson");

    // then
    assertThat(body)
        .contains("window.confirm")
        .contains("/api/tutor/academy/lessons/")
        .contains("method: 'DELETE'")
        .contains("stateApi.removeItem");
  }

  @Test
  void backgroundStyleIsAcademyWideNotPerLesson() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(TEMPLATE));
    String controller = Files.readString(CONTROLLER);

    // when & then
    // 공통 디자인 섹션이 수업상품 입력보다 위에 온다.
    String html = Files.readString(TEMPLATE);
    assertThat(html.indexOf("공통 디자인")).isLessThan(html.indexOf("수업상품 입력"));
    assertThat(document.selectFirst("#theme")).isNotNull();
    assertThat(functionBody(controller, "syncCommonTheme")).contains("setCommonTheme");
  }

  @Test
  void missingActiveItemHidesOnlyPreviewContentAndLeavesNavigationAvailable() throws Exception {
    // given
    String controller = Files.readString(CONTROLLER);

    // when
    String renderPreview = functionBody(controller, "renderPreview");

    // then
    assertThat(renderPreview).contains("preview.hidden = true");
    assertThat(renderPreview).doesNotContain("tabs.hidden", "addButton.hidden");
  }

  @Test
  void desktopCssMatchesTheReferenceEditorPreviewAndTabDimensions() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    assertThat(css)
        .contains("--tab-active-bg:linear-gradient(135deg,#2ca8b5,#24d0cf)")
        .contains("grid-template-columns:430pxminmax(0,1fr)")
        .contains("max-width:1160px")
        .contains("grid-template-columns:repeat(5,minmax(0,1fr))")
        .contains("gap:0")
        .contains("min-height:118px")
        .contains("background:var(--tab-active-bg)");
  }

  @Test
  void addTabUsesTheSameInactiveTabVisualsWithoutASeparateAccentDesign() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when
    String typography = cssRule(css, ".course-tabstrong,.course-tab-add");
    String disabled = cssRule(css, ".course-tab-add:disabled");
    String cssWithoutAllowedAddRules = css.replace(typography, "").replace(disabled, "");

    // then
    assertThat(typography)
        .contains("font-size:clamp(17px,1.75vw,22px)")
        .contains("font-weight:800")
        .contains("line-height:1.2")
        .contains("letter-spacing:-.03em")
        .doesNotContain("background:", "color:", "border:");
    assertThat(css)
        .doesNotContain("--tab-add")
        .contains(".course-tab.is-active");
    assertThat(cssWithoutAllowedAddRules).doesNotContain(".course-tab-add");
  }

  @Test
  void heroCssKeepsReferenceNavyScaleAndFallback() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when
    int fallback = css.indexOf("background-color:var(--theme-main)");
    int gradient = css.indexOf("background:radial-gradient", fallback);

    // then
    assertThat(css)
        .contains("--theme-main:#0a1d3d")
        .contains(".selected-hero{")
        .contains("min-height:430px")
        .contains("background-color:var(--theme-main)");
    assertThat(fallback).isGreaterThanOrEqualTo(0);
    assertThat(gradient).isGreaterThan(fallback);
  }

  @Test
  void tabsUseThreeColumnsOnTabletAndTwoOnMobile() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    assertThat(css)
        .contains("@media(max-width:1050px)")
        .contains("grid-template-columns:repeat(3,1fr)")
        .contains("@media(max-width:760px)")
        .contains("grid-template-columns:1fr1fr");
  }

  @Test
  void editorCssKeepsVisibleFocus() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    assertThat(css)
        .contains("outline:3pxsolid#2563eb")
        .contains("outline-offset:2px");
  }

  @Test
  void tabFocusRingIsInsetSoThePreviewDoesNotClipIt() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    assertThat(css)
        .contains(".course-tab:focus-visible{")
        .contains("outline-offset:-4px");
  }

  @Test
  void mediaPlaceholderKeepsFullOpacityAndHighContrastText() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when
    String placeholderRule = cssRule(css, ".course-media-placeholder");

    // then
    assertThat(placeholderRule)
        .contains("color:var(--hero-text)")
        .contains("opacity:1");
  }

  @Test
  void addTabHasAnExplicitDisabledAppearance() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when
    String disabledRule = cssRule(css, ".course-tab-add:disabled");

    // then
    assertThat(disabledRule)
        .contains("cursor:not-allowed")
        .contains("opacity:");
  }

  @Test
  void intermediateDesktopRangeAvoidsThe1051PixelLayoutCliff() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    String bridge = cssBlock(css, "@media(min-width:1051px)and(max-width:1320px)");

    // then
    assertThat(bridge)
        .contains("grid-template-columns:clamp(340px,36vw,430px)minmax(0,1fr)")
        .contains(".selected-hero{grid-template-columns:1fr");
  }

  @Test
  void allThreeExplanationCardsUseAccentEmphasis() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when
    String emphasizedRule = cssRule(css, ".explain-card");

    // then
    assertThat(emphasizedRule)
        .contains("border-left:4pxsolidvar(--detail-accent)")
        .contains("color:var(--detail-accent)");
    assertThat(css).doesNotContain(".explain-card:nth-child(-n+2)");
  }

  @Test
  void lightThemesDeclareDistinctHeroTitleColorsAndTheTitleUsesThem() throws Exception {
    // given
    String css = compact(Files.readString(CSS));

    // when & then
    assertThat(cssRule(css, ".hero-title")).contains("color:var(--hero-title)");
    for (String theme : new String[] {"ORANGE", "SKY", "PINK", "GREEN"}) {
      String themeRule = cssRule(css, ".preview[data-theme=\"" + theme + "\"]");
      String title = cssProperty(themeRule, "--hero-title");
      String body = cssProperty(themeRule, "--hero-text");
      assertThat(title).as("%s hero title token", theme).isNotBlank().isNotEqualTo(body);
    }
  }

  private static String compact(String css) {
    return css.replaceAll("\\s+", "");
  }

  private static String cssRule(String compactCss, String selector) {
    int start = compactCss.indexOf(selector + "{");
    assertThat(start).as("CSS rule %s", selector).isGreaterThanOrEqualTo(0);
    int end = compactCss.indexOf('}', start);
    assertThat(end).as("closing brace for CSS rule %s", selector).isGreaterThan(start);
    return compactCss.substring(start, end + 1);
  }

  private static String cssBlock(String compactCss, String marker) {
    int start = compactCss.indexOf(marker + "{");
    assertThat(start).as("CSS block %s", marker).isGreaterThanOrEqualTo(0);
    int openingBrace = compactCss.indexOf('{', start);
    int depth = 0;
    for (int index = openingBrace; index < compactCss.length(); index++) {
      char current = compactCss.charAt(index);
      if (current == '{') depth++;
      if (current == '}' && --depth == 0) return compactCss.substring(start, index + 1);
    }
    throw new AssertionError("Unclosed CSS block " + marker);
  }

  private static String cssProperty(String rule, String property) {
    int start = rule.indexOf(property + ":");
    if (start < 0) return "";
    int valueStart = start + property.length() + 1;
    int end = rule.indexOf(';', valueStart);
    return end < 0 ? "" : rule.substring(valueStart, end);
  }

  private static String functionBody(String source, String functionName) {
    int declaration = source.indexOf("function " + functionName + "(");
    assertThat(declaration).as("function %s", functionName).isGreaterThanOrEqualTo(0);
    int openingBrace = source.indexOf('{', declaration);
    int depth = 0;
    for (int index = openingBrace; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') depth++;
      if (current == '}' && --depth == 0) return source.substring(declaration, index + 1);
    }
    throw new AssertionError("Unclosed function " + functionName);
  }
}
