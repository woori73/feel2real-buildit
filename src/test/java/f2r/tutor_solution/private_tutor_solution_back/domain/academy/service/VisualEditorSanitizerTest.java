package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link VisualEditorSanitizer} 단위 테스트.
 *
 * <p>Spring 컨테이너 없이 실행되므로 CI build 잡에서 MySQL 없이 검증한다.
 */
@DisplayName("VisualEditorSanitizer 단위 테스트")
class VisualEditorSanitizerTest {

  @Test
  @DisplayName("tagName 허용 목록: section은 통과, script는 거부")
  void tagNameAllowlist() {
    String ok = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"section\",\"type\":\"default\"}]}");
    assertThat(ok).contains("section");

    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"script\",\"type\":\"default\"}]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 태그");
  }

  @Test
  @DisplayName("tagName 우회: 대문자 SCRIPT도 거부")
  void tagNameCaseInsensitive() {
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"SCRIPT\"}]}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("type 허용 목록: 알 수 없는 타입은 거부")
  void componentTypeAllowlist() {
    String ok = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"div\",\"type\":\"text\"}]}");
    assertThat(ok).contains("text");

    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"div\",\"type\":\"evil-widget\"}]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 컴포넌트 타입");
  }

  @Test
  @DisplayName("components 문자열 HTML: script가 정제된다")
  void componentsStringHtmlSanitized() {
    String result = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":\"<section><h1>제목</h1><script>alert(1)</script></section>\"}");
    assertThat(result).contains("section");
    assertThat(result).doesNotContain("<script");
  }

  @Test
  @DisplayName("content 원시 HTML: script와 onerror가 정제된다")
  void contentHtmlSanitized() {
    String result = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"content\":\"<p>hi</p><script>alert(1)</script>"
            + "<img src=x onerror=alert(2)>\"}]}");
    assertThat(result).doesNotContain("<script");
    assertThat(result.toLowerCase()).doesNotContain("onerror");
  }

  @Test
  @DisplayName("script 속성은 제거된다")
  void scriptPropRemoved() {
    String result = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"div\",\"script\":\"alert(1)\"}]}");
    assertThat(result).doesNotContain("alert(1)");
  }

  @Test
  @DisplayName("on* 이벤트 속성은 제거된다")
  void eventAttrsRemoved() {
    String result = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"div\",\"attributes\":"
            + "{\"onclick\":\"alert(1)\",\"class\":\"box\"}}]}");
    assertThat(result.toLowerCase()).doesNotContain("onclick");
    assertThat(result).contains("box");
  }

  @Test
  @DisplayName("href javascript: 는 거부된다")
  void hrefJavascriptRejected() {
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"a\",\"attributes\":"
            + "{\"href\":\"javascript:alert(1)\"}}]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은 URL");
  }

  @Test
  @DisplayName("href 유니코드 이스케이프 우회는 거부된다")
  void hrefUnicodeEscapeRejected() {
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"a\",\"attributes\":"
            + "{\"href\":\"\\u006aavascript:alert(1)\"}}]}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("href 제어문자 우회는 거부된다")
  void hrefControlCharRejected() {
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"a\",\"attributes\":"
            + "{\"href\":\"java\\tscript:alert(1)\"}}]}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("정상 URL은 허용된다 (https, 상대경로, 앵커, mailto)")
  void normalUrlsAllowed() {
    String result = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":["
            + "{\"tagName\":\"a\",\"attributes\":{\"href\":\"https://example.com\"}},"
            + "{\"tagName\":\"a\",\"attributes\":{\"href\":\"/about\"}},"
            + "{\"tagName\":\"a\",\"attributes\":{\"href\":\"#top\"}},"
            + "{\"tagName\":\"a\",\"attributes\":{\"href\":\"mailto:a@b.c\"}}"
            + "]}");
    assertThat(result).contains("https://example.com");
  }

  @Test
  @DisplayName("중첩 components도 재귀적으로 정제된다")
  void nestedComponentsSanitized() {
    String result = VisualEditorSanitizer.sanitizeProjectData(
        "{\"components\":[{\"tagName\":\"div\",\"components\":"
            + "[{\"content\":\"<script>alert(1)</script><p>ok</p>\"}]}]}");
    assertThat(result).doesNotContain("<script");
    assertThat(result).contains("ok");
  }

  @Test
  @DisplayName("CSS 경계값: 65,535바이트 허용, 65,536바이트 거부")
  void cssBoundary() {
    String css65535 = "a{color:red;}".repeat(6000).substring(0, 65535);
    assertThat(css65535.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isEqualTo(65535);
    assertThat(VisualEditorSanitizer.sanitizeCss(css65535)).isNotEmpty();

    String css65536 = css65535 + "x";
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeCss(css65536))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64KB");
  }

  @Test
  @DisplayName("CSS 위험 패턴: @import, expression, javascript: URL 제거")
  void cssDangerousPatterns() {
    String css = "@import url('http://evil.com/x.css'); "
        + "p { color: expression(alert(1)); background: url(javascript:alert(2)); }";
    String result = VisualEditorSanitizer.sanitizeCss(css);
    assertThat(result).doesNotContain("@import");
    assertThat(result).doesNotContain("expression(");
    assertThat(result.toLowerCase()).doesNotContain("javascript:");
  }

  @Test
  @DisplayName("P0 시맨틱 태그가 정제 후에도 보존된다")
  void p0SemanticTagsPreserved() {
    String html = "<section><header><h1>제목</h1></header>"
        + "<main><article><p>본문</p></article></main>"
        + "<footer><nav><a href=\"/about\">소개</a></nav></footer></section><hr>";
    String result = VisualEditorSanitizer.sanitizeHtml(html);
    assertThat(result).contains("<section");
    assertThat(result).contains("<header");
    assertThat(result).contains("<footer");
    assertThat(result).contains("<nav");
    assertThat(result).contains("<main");
    assertThat(result).contains("<article");
    assertThat(result).contains("<hr");
  }

  @Test
  @DisplayName("ALLOWED_TAG_NAMES의 모든 태그는 Jsoup 정제에서 보존된다")
  void tagAllowlistInSyncWithSafelist() {
    for (String tag : VisualEditorSanitizer.ALLOWED_TAG_NAMES) {
      assertThat(VisualEditorSanitizer.isTagPreserved(tag))
          .as("tag <%s> should survive Jsoup clean", tag)
          .isTrue();
    }
  }

  @Test
  @DisplayName("한글 콘텐츠가 보존된다")
  void koreanPreserved() {
    String html = "<section><h1>안녕하세요</h1><p>한글 테스트입니다.</p></section>";
    String result = VisualEditorSanitizer.sanitizeHtml(html);
    // Jsoup이 블록 요소에 개행을 넣어 pretty-print하므로 공백 정규화 후 비교
    assertThat(normalize(result)).isEqualTo(normalize(html));
    assertThat(result).contains("안녕하세요");
  }

  private static String normalize(String s) {
    return s.replaceAll("\\s+", "");
  }

  @Test
  @DisplayName("유효하지 않은 JSON은 거부된다")
  void invalidJsonRejected() {
    assertThatThrownBy(() -> VisualEditorSanitizer.sanitizeProjectData("{invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("유효한 JSON");
  }
}
