package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LessonProductSurfaceCompatibilityContractTest {

  private static final Path SCRIPT_DIR = Path.of("src/main/resources/static/js");
  private static final Path TEMPLATE_DIR = Path.of("src/main/resources/templates");

  @Test
  void sharedShowcase_mapsAuthoringFieldsToLessonEditorPreviewStructure() throws Exception {
    String renderer = Files.readString(SCRIPT_DIR.resolve("lesson_product_showcase.js"));
    // 골격 정본은 수업 관리 미리보기(.preview)다 — 히어로(selected-hero) + 상세(detail-section).
    String tabRenderer = between(renderer, "function tab(lesson, showcaseId, index)", "function targetGroupLabel(value)");
    String panelRenderer = between(renderer, "function panel(lesson, showcaseId, index, options)", "  /**\n   * 히어로 대표 미디어.");

    assertThat(renderer)
        .contains("course-nav lesson-product-nav")
        .contains("course-tab lesson-product-tab")
        .contains("lesson-product-collapse-inner")
        .contains("selected-hero lesson-product-selected-hero")
        .contains("detail-section lesson-product-detail-section")
        .contains("lesson.tabGuidanceLabel")
        .contains("lesson.tabShortName")
        .contains("lesson.kicker")
        .contains("lesson.highlight")
        .contains("lesson.learnerNeed")
        .contains("lesson.coreDetailTitle")
        .contains("lesson.explanation1")
        .contains("lesson.explanation2")
        .contains("lesson.explanation3")
        .contains("lesson.theme")
        .doesNotContain(
            "cd-book",
            "applyPublicLesson",
            "lesson.priceSummary",
            "unsplash.com",
            "등록해 주세요",
            "추후 제공");
    assertThat(tabRenderer)
        .contains("textOr(lesson.tabGuidanceLabel, lesson.scheduleSummary, targetGroupLabel(lesson.targetGroup))")
        .doesNotContain("lesson.kicker", "수업 안내");
    assertThat(panelRenderer)
        // 모든 패널은 닫힌 채 렌더된다 — 여는 것은 탭 클릭(toggle)뿐이다.
        .contains("aria-hidden=\"true\" hidden")
        .contains("<div class=\"hero-copy\">")
        .contains("explain-list lesson-product-explain-list")
        .contains("mediaBlock(lesson, config)");
  }

  @Test
  void delegatedToggle_closesOnSameTabAndSwitchesVisiblePanel() throws Exception {
    String renderer = Files.readString(SCRIPT_DIR.resolve("lesson_product_showcase.js"));
    String toggle = between(renderer, "function toggle(button, index)", "function wire()");

    // 닫힘 → 클릭 시 열림, 열린 탭 재클릭 시 닫힘, 다른 탭 클릭은 내용만 교체(#244 기믹).
    assertThat(toggle)
        .contains("lessonPanel.hidden = !active")
        .contains("lessonPanel.setAttribute('aria-hidden', active ? 'false' : 'true')")
        .contains("showcase.dataset.activeIndex = String(index)")
        .contains("showcase.classList.add('is-expanded')")
        .contains("showcase.classList.remove('is-expanded')")
        .contains("showcase.dataset.activeIndex === String(index)")
        .contains("showcase.dataset.activeIndex = ''")
        .doesNotContain("showcase.offsetHeight");
    assertThat(renderer)
        .contains("document.addEventListener('click'")
        .contains("event.target.closest('[data-lesson-showcase-tab]')")
        .doesNotContain("onclick=\"F2RLessonProductShowcase.toggle");
  }

  @Test
  void publicAndBuilder_delegateToSamePreviewOnlyShowcase() throws Exception {
    String publicScript = Files.readString(SCRIPT_DIR.resolve("academy_public.js"));
    String builderScript = Files.readString(SCRIPT_DIR.resolve("academy_builder.js"));
    // 쇼케이스 호출은 공용 모듈의 ctx 2벌이 갖는다. 빈 문구와 이미지 경로가 화면마다 다르다.
    String sections = Files.readString(SCRIPT_DIR.resolve("academy_sections.js"));
    String publicRenderer = between(sections, "function createPublicContext(deps)", "function createBuilderContext(deps)");
    String builderRenderer = between(sections, "function createBuilderContext(deps)", "global.F2RAcademySections");

    assertThat(publicRenderer)
        .contains("resolve(options.showcase).render(items")
        .contains("아직 공개된 수업 상품이 없습니다.")
        .doesNotContain("interactive", "isFull", "lessonMeta", "applyPublicLesson");
    assertThat(builderRenderer)
        .contains("resolve(options.showcase).render(items")
        .contains("아직 등록된 수업이 없습니다.")
        .doesNotContain("interactive", "builderLessonMeta", "applyPublicLesson");
  }

  @Test
  void builderLessonSection_linksToLessonEditorWithManagementButton() throws Exception {
    String builderScript = Files.readString(SCRIPT_DIR.resolve("academy_builder.js"));
    String builderTemplate = Files.readString(TEMPLATE_DIR.resolve("tutor/academy_builder.html"));
    // 마크업 정본은 academy_sections.js의 빌더 ctx가 갖는다(lessonsFooter).
    String lessonSection = Files.readString(SCRIPT_DIR.resolve("academy_sections.js"));

    assertThat(lessonSection)
        .contains("builder-lesson-manage")
        .contains("href=\"/tutor/academy/lessons/editor\"")
        .contains("수업 관리");
    assertThat(builderTemplate)
        .contains(".builder-public-preview .cv-btn-primary.builder-lesson-manage{pointer-events:auto;cursor:pointer;}");
  }

  @Test
  void publicAndBuilderTemplates_loadSharedAssetsBeforeSurfaceScript() throws Exception {
    String publicTemplate = Files.readString(TEMPLATE_DIR.resolve("public/academy_public.html"));
    String builderTemplate = Files.readString(TEMPLATE_DIR.resolve("tutor/academy_builder.html"));

    assertThat(publicTemplate.indexOf("/js/lesson_product_showcase.js"))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(publicTemplate.indexOf("/js/academy_public.js"));
    assertThat(builderTemplate.indexOf("/js/lesson_product_showcase.js"))
        .isGreaterThanOrEqualTo(0)
        .isLessThan(builderTemplate.indexOf("/js/academy_builder.js"));
    assertThat(publicTemplate).contains("/css/lesson_product_showcase.css");
    assertThat(builderTemplate).contains("/css/lesson_product_showcase.css");
  }

  @Test
  void sharedStyles_followLessonEditorPreviewWithoutOpenCloseAnimation() throws Exception {
    String css = Files.readString(Path.of("src/main/resources/static/css/lesson_product_showcase.css"));
    String editorCss = Files.readString(Path.of("src/main/resources/static/css/lesson_product_editor.css"));

    assertThat(css)
        .contains(".course-nav.lesson-product-nav")
        .contains(".course-tab.lesson-product-tab")
        .contains(".course-tab.lesson-product-tab.active")
        .contains(".lesson-product-panel[hidden] { display: none; }")
        .contains(".lesson-product-showcase .selected-hero")
        .contains(".lesson-product-showcase .detail-section")
        .contains(".lesson-product-showcase .explain-card")
        // 접이식 컨테이너는 0fr↔1fr row로만 접는다. 여닫는 애니메이션은 붙이지 않는다.
        .contains(".lesson-product-collapse { display: grid; grid-template-rows: 0fr; }")
        .contains(".lesson-product-showcase.is-expanded .lesson-product-collapse { grid-template-rows: 1fr; }")
        // 편집기 미리보기와 같은 테마 변수를 써야 원장이 고른 배경이 그대로 나온다.
        .contains("--theme-main: #0a1d3d")
        .contains("--title-accent: #8de6eb")
        .doesNotContain("transition: grid-template-rows", "transition: border-radius");
    assertThat(editorCss)
        .contains("--theme-main: #0a1d3d")
        .contains("--title-accent: #8de6eb")
        .contains("--tab-active-bg: linear-gradient(135deg,#2ca8b5,#24d0cf)");
  }

  private String between(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    assertThat(start).as("start marker %s", startMarker).isGreaterThanOrEqualTo(0);
    assertThat(end).as("end marker %s", endMarker).isGreaterThan(start);
    return source.substring(start, end);
  }
}
