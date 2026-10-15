package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 GrapesJS 0.23.6 출력으로 {@link VisualEditorSanitizer}를 검증한다.
 *
 * <p>fixture({@code visual-editor/grapesjs-project.json})는 {@code e2e/visual-editor}의
 * {@code npm run capture-fixture}가 실제 GrapesJS에서 캡처한 {@code projectData/html/css}다.
 * 손으로 만든 JSON이 아니라 실제 출력을 쓰는 이유: 이전에는 {@code projectData}를
 * {@code "{}"}로만 테스트해서, GrapesJS가 항상 붙이는 {@code head}/{@code docEl}이
 * 정제기에 막혀 모든 저장이 400이 되는 결함을 CI가 잡지 못했다.
 *
 * <p>Spring 컨테이너 없이 실행되므로 CI build 잡에서 MySQL 없이 검증한다.
 */
@DisplayName("VisualEditorSanitizer — 실제 GrapesJS 출력 회귀 테스트")
class VisualEditorRealOutputTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static JsonNode fixture;

  @BeforeAll
  static void loadFixture() throws IOException {
    try (InputStream in = VisualEditorRealOutputTest.class
        .getResourceAsStream("/visual-editor/grapesjs-project.json")) {
      assertThat(in).as("fixture 파일").isNotNull();
      fixture = MAPPER.readTree(in);
    }
  }

  private static String realProjectData() {
    return fixture.get("projectData").toString();
  }

  /** fixture의 첫 프레임 component를 수정한 projectData 문자열을 만든다. */
  private static String withFrameComponent(java.util.function.Consumer<ObjectNode> mutator) {
    JsonNode copy = fixture.get("projectData").deepCopy();
    ObjectNode component = (ObjectNode) copy.get("pages").get(0).get("frames").get(0)
        .get("component");
    mutator.accept(component);
    return copy.toString();
  }

  @Test
  @DisplayName("fixture는 실제 GrapesJS 0.23.6이 만든 head/docEl 껍데기를 포함한다")
  void fixtureContainsRealShell() {
    // given: 캡처된 실제 출력
    JsonNode component = fixture.get("projectData").get("pages").get(0).get("frames")
        .get(0).get("component");

    // then: 문제의 두 키가 실제로 존재한다(fixture가 의미 있는지 보증)
    assertThat(fixture.get("grapesjsVersion").asText()).isEqualTo("0.23.6");
    assertThat(component.get("head").toString()).isEqualTo("{\"type\":\"head\"}");
    assertThat(component.get("docEl").toString()).isEqualTo("{\"tagName\":\"html\"}");
  }

  @Test
  @DisplayName("실제 projectData는 거부되지 않고 구조가 그대로 보존된다")
  void realProjectDataIsAcceptedAndStructurePreserved() throws IOException {
    // given: GrapesJS가 만든 실제 projectData
    String raw = realProjectData();

    // when
    String sanitized = VisualEditorSanitizer.sanitizeProjectData(raw);

    // then: head/docEl 포함 전체 구조가 손실 없이 보존된다
    JsonNode before = MAPPER.readTree(raw);
    JsonNode after = MAPPER.readTree(sanitized);
    assertThat(after.get("pages").size()).isEqualTo(before.get("pages").size());
    JsonNode component = after.get("pages").get(0).get("frames").get(0).get("component");
    assertThat(component.get("head").toString()).isEqualTo("{\"type\":\"head\"}");
    assertThat(component.get("docEl").toString()).isEqualTo("{\"tagName\":\"html\"}");
    assertThat(component.get("components").size())
        .isEqualTo(before.get("pages").get(0).get("frames").get(0).get("component")
            .get("components").size());
    assertThat(after.get("styles").size()).isEqualTo(before.get("styles").size());
    assertThat(after.get("assets").size()).isEqualTo(before.get("assets").size());
    assertThat(after.get("assets").get(0).get("src").asText()).startsWith("data:image/png");
  }

  @Test
  @DisplayName("재불러오기: 정제된 projectData를 다시 정제해도 같다(멱등)")
  void sanitizeIsIdempotentOnRealOutput() throws IOException {
    // given
    String once = VisualEditorSanitizer.sanitizeProjectData(realProjectData());

    // when
    String twice = VisualEditorSanitizer.sanitizeProjectData(once);

    // then
    assertThat(MAPPER.readTree(twice)).isEqualTo(MAPPER.readTree(once));
  }

  @Test
  @DisplayName("head에 script 컴포넌트를 숨기면 거부된다")
  void headWithScriptIsRejected() {
    // given: head 안에 컴포넌트를 심은 변조 payload
    String tampered = withFrameComponent(c -> c.set("head", MAPPER.createObjectNode()
        .put("type", "head")
        .set("components", MAPPER.createArrayNode().add(
            MAPPER.createObjectNode().put("tagName", "script")))));

    // when / then
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(tampered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 문서 구조");
  }

  @Test
  @DisplayName("head/docEl에 정해진 값 외의 type·tagName을 쓰면 거부된다")
  void shellWithUnexpectedValueIsRejected() {
    // given
    String badHead = withFrameComponent(c -> c.set("head",
        MAPPER.createObjectNode().put("type", "iframe")));
    String badDocEl = withFrameComponent(c -> c.set("docEl",
        MAPPER.createObjectNode().put("tagName", "iframe")));
    String docElWithAttrs = withFrameComponent(c -> c.set("docEl",
        MAPPER.createObjectNode().put("tagName", "html")
            .set("attributes", MAPPER.createObjectNode().put("onload", "x()"))));

    // when / then
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(badHead))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(badDocEl))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(docElWithAttrs))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("페이지 type은 GrapesJS 기본값 main만 보존하고 다른 값은 거부한다")
  void pageTypeMainIsPreservedOthersRejected() throws IOException {
    // given: 실제 fixture는 pages[0].type=main 을 포함한다
    assertThat(fixture.get("projectData").get("pages").get(0).get("type").asText())
        .isEqualTo("main");
    JsonNode tamperedPage = fixture.get("projectData").deepCopy();
    ((ObjectNode) tamperedPage.get("pages").get(0)).put("type", "script");

    // when
    String sanitized = VisualEditorSanitizer.sanitizeProjectData(realProjectData());

    // then: main은 보존되고, 변조된 값은 거부된다
    assertThat(MAPPER.readTree(sanitized).get("pages").get(0).get("type").asText())
        .isEqualTo("main");
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(tamperedPage.toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 페이지 구조");
  }

  @Test
  @DisplayName("head/docEl 예외는 프레임 바로 아래에만 적용된다(트리 안의 type=head는 계속 거부)")
  void headTypeInsideTreeIsStillRejected() {
    // given: 일반 컴포넌트 트리 안에 type=head / tagName=html을 숨긴 payload
    String nestedHead = withFrameComponent(c -> ((ObjectNode) c.get("components").get(0))
        .set("components", MAPPER.createArrayNode().add(
            MAPPER.createObjectNode().put("type", "head"))));
    String nestedHtml = withFrameComponent(c -> ((ObjectNode) c.get("components").get(0))
        .set("components", MAPPER.createArrayNode().add(
            MAPPER.createObjectNode().put("tagName", "html"))));

    // when / then
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(nestedHead))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 컴포넌트 타입");
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(nestedHtml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 태그");
  }

  @Test
  @DisplayName("실제 구조에 위험 요소를 섞어도 계속 차단된다(script 태그, on* 속성, javascript: URL)")
  void dangerousContentIsStillBlockedOnRealStructure() throws IOException {
    // given: 실제 구조 + script 컴포넌트
    String withScript = withFrameComponent(c -> ((com.fasterxml.jackson.databind.node.ArrayNode)
        c.get("components")).add(MAPPER.createObjectNode().put("tagName", "script")));
    // 실제 구조 + 이벤트 속성 + javascript: 링크
    String withEvent = withFrameComponent(c -> {
      ObjectNode first = (ObjectNode) c.get("components").get(0);
      ((ObjectNode) first.get("attributes")).put("onclick", "alert(1)");
    });
    String withJsUrl = withFrameComponent(c -> {
      ObjectNode first = (ObjectNode) c.get("components").get(0);
      ((ObjectNode) first.get("attributes")).put("href", "javascript:alert(1)");
    });

    // when / then
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(withScript))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 태그");
    assertThat(VisualEditorSanitizer.sanitizeProjectData(withEvent)).doesNotContain("onclick");
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(withJsUrl))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 URL");
  }

  @Test
  @DisplayName("실제 html: id·data 속성·https/상대경로 링크·이미지가 보존된다")
  void realHtmlKeepsIdsAndLinks() {
    // given
    String html = fixture.get("html").asText();

    // when
    String cleaned = VisualEditorSanitizer.sanitizeHtml(html);

    // then
    assertThat(cleaned).contains("data-preset=\"ENGLISH\"");
    assertThat(cleaned).containsPattern("id=\"i[a-z0-9]+\"");
    assertThat(cleaned).contains("href=\"https://example.com/a?b=1\"");
    assertThat(cleaned).as("상대경로 링크는 사이트 내부 이동이므로 보존").contains("href=\"/academies/test\"");
    assertThat(cleaned).contains("src=\"https://via.placeholder.com/800x400?text=img\"");
    assertThat(cleaned).as("상대경로 이미지 보존").contains("src=\"/api/academies/test-english-academy/logo\"");
    assertThat(cleaned).contains("<table").contains("<th").contains("<li");
  }

  @Test
  @DisplayName("상대경로 예외가 위험 URL 차단을 약화시키지 않는다")
  void relativeLinksDoNotWeakenUrlBlocking() {
    // given
    String html = "<a href=\"javascript:alert(1)\">x</a>"
        + "<a href=\" jAvAsCrIpT:alert(1)\">y</a>"
        + "<img src=\"javascript:alert(1)\">"
        + "<a href=\"/ok\">z</a>";

    // when
    String cleaned = VisualEditorSanitizer.sanitizeHtml(html);

    // then
    assertThat(cleaned).doesNotContainIgnoringCase("javascript:");
    assertThat(cleaned).contains("href=\"/ok\"");
  }

  @Test
  @DisplayName("실제 css: 미디어쿼리·hover 규칙·id 규칙이 보존된다")
  void realCssKeepsRules() {
    // given
    String css = fixture.get("css").asText();

    // when
    String cleaned = VisualEditorSanitizer.sanitizeCss(css);

    // then
    assertThat(cleaned).contains("@media (max-width: 768px)");
    assertThat(cleaned).contains(".cta-btn:hover");
    assertThat(cleaned).containsPattern("#i[a-z0-9]+\\{");
    assertThat(cleaned).contains("grid-template-columns");
  }
}
