package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * BuildIt 시각 편집기 입력 정제기.
 *
 * <p>Spring에 의존하지 않는 순수 정적 유틸리티이므로 단위 테스트에서
 * 컨테이너 없이 직접 검증할 수 있다.
 *
 * <p>정제 항목:
 * <ul>
 *   <li>HTML: Jsoup 허용 목록 기반 정제 + 인라인 style 위험 패턴 제거</li>
 *   <li>CSS: UTF-8 바이트 기준 크기 검사(경계값 포함) + 위험 패턴 제거.
 *       MySQL TEXT 컬럼은 최대 65,535바이트이므로 65,536바이트부터 거부한다.</li>
 *   <li>projectData(GrapesJS JSON):
 *     <ul>
 *       <li>{@code tagName} 허용 목록 검사 — {@code script} 등으로 위장한
 *           컴포넌트가 재로드 시 실행되는 것을 차단</li>
 *       <li>{@code type} 허용 목록 검사 — 알 수 없는 컴포넌트 타입 차단</li>
 *       <li>{@code components} 문자열 HTML 정제</li>
 *       <li>{@code content} 원시 HTML 정제</li>
 *       <li>{@code script} 속성·{@code on*} 이벤트 속성 제거</li>
 *       <li>{@code attributes}의 URL 속성 스킴 검사</li>
 *       <li>페이지 객체 {@code type}: GrapesJS 기본값 "main"만 보존, 그 외 거부</li>
 *       <li>프레임 문서 껍데기 {@code head}/{@code docEl}: GrapesJS가 생성하는 정해진 빈 모양일
 *           때만 보존하고, 그 외 내용·위치는 거부</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class VisualEditorSanitizer {

  private VisualEditorSanitizer() {
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * GrapesJS 컴포넌트 {@code tagName} 허용 목록.
   * 아래 집합에 없는 태그명으로 저장하면 400으로 거부된다.
   * (Jsoup Safelist가 허용하는 태그와 일치해야 한다.)
   */
  public static final Set<String> ALLOWED_TAG_NAMES = Set.of(
      "a", "b", "blockquote", "br", "caption", "cite", "code",
      "col", "colgroup", "dd", "div", "dl", "dt", "em",
      "h1", "h2", "h3", "h4", "h5", "h6",
      "i", "img", "li", "ol", "p", "pre", "q", "small", "span",
      "strike", "strong", "sub", "sup",
      "table", "tbody", "td", "tfoot", "th", "thead", "tr",
      "u", "ul",
      "section", "header", "footer", "nav", "main", "article",
      "aside", "figure", "figcaption", "time", "mark", "hr");

  /**
   * GrapesJS 컴포넌트 {@code type} 허용 목록.
   * GrapesJS 기본 컴포넌트 타입만 허용한다.
   */
  public static final Set<String> ALLOWED_COMPONENT_TYPES = Set.of(
      "wrapper", "text", "textnode", "image", "video", "map",
      "link", "label", "comment",
      "cell", "row", "table", "thead", "tbody", "tfoot",
      "default");

  /**
   * 컴포넌트에서 제거하는 위험 속성.
   */
  private static final Set<String> BLOCKED_ATTRS = Set.of(
      "script", "onscript", "srcdoc", "formaction", "xlink:href");

  /**
   * URL 스킴 검사를 적용하는 속성.
   */
  private static final Set<String> URL_ATTRS = Set.of(
      "href", "src", "action", "cite", "poster", "background", "data");

  /**
   * 허용 URL 스킴. data: 는 이미지(data:image/*)만 별도 허용한다.
   */
  private static final Set<String> ALLOWED_URL_SCHEMES = Set.of(
      "http", "https", "mailto", "tel");

  /**
   * 상대경로 URL을 검증하기 위한 가상 기준 URI. preserveRelativeLinks(true)여도 Jsoup은
   * 링크를 기준 URI에 대해 해석해 허용 프로토콜(http/https)인지 확인하므로 기준 URI가 필요하다.
   * 출력에는 상대경로가 그대로 남고 이 값은 저장되지 않는다.
   */
  private static final String RELATIVE_LINK_BASE_URI = "https://visual-editor.invalid/";

  private static final Safelist EDITOR_SAFELIST = Safelist.relaxed()
      // 사이트 내부 상대경로(/api/..., /academies/...)는 스킴이 없어 위험하지 않다.
      // 보존하지 않으면 Jsoup이 기준 URI 없이 이를 해석하지 못해 src/href를 지운다.
      // javascript: 등 스킴이 있는 URL은 아래 addProtocols 허용 목록으로 계속 걸러진다.
      .preserveRelativeLinks(true)
      .addTags("section", "header", "footer", "nav", "main", "article",
          "aside", "figure", "figcaption", "time", "mark", "hr")
      .removeTags("script", "iframe", "object", "embed", "form", "svg", "math",
          "input", "button", "select", "textarea", "link", "meta", "base",
          "noscript", "template", "slot")
      .removeAttributes(":all", "onabort", "onblur", "onchange", "onclick",
          "ondblclick", "onerror", "onfocus", "onkeydown", "onkeypress",
          "onkeyup", "onload", "onmousedown", "onmousemove", "onmouseout",
          "onmouseover", "onmouseup", "onreset", "onresize", "onscroll",
          "onselect", "onsubmit", "onunload", "onanimationstart",
          "onanimationend", "ontransitionend", "onpointerdown",
          "onpointerup", "ontouchstart", "ontouchend", "ontouchmove",
          "onwheel", "oncopy", "oncut", "onpaste", "ondrag", "ondrop")
      .addAttributes(":all", "style", "class", "id", "data-preset",
          "data-gjs-type", "data-gjs-draggable", "data-gjs-droppable",
          "data-gjs-resizable", "data-gjs-selectable")
      .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
      .addProtocols("img", "src", "http", "https", "data")
      .removeProtocols("img", "src", "javascript", "vbscript");

  /**
   * HTML을 허용 목록 기준으로 정제한다.
   */
  public static String sanitizeHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String cleaned = Jsoup.clean(html, RELATIVE_LINK_BASE_URI, EDITOR_SAFELIST);
    return sanitizeInlineStyles(cleaned);
  }

  /**
   * 정제된 HTML에 허용 목록 태그가 그대로 보존되는지 확인한다.
   * (ALLOWED_TAG_NAMES와 Safelist 동기화 검증용)
   *
   * <p>테이블 내부 태그는 HTML 파서가 단독으로 제거하므로
   * {@code <table>} 컨텍스트 안에서 검사한다.
   */
  public static boolean isTagPreserved(String tagName) {
    String html = "<" + tagName + ">x</" + tagName + ">";
    if (TABLE_INNER_TAGS.contains(tagName)) {
      html = "<table>" + html + "</table>";
    }
    String cleaned = Jsoup.clean(html, EDITOR_SAFELIST);
    return cleaned.contains("<" + tagName);
  }

  private static final Set<String> TABLE_INNER_TAGS = Set.of(
      "caption", "col", "colgroup", "thead", "tbody", "tfoot", "tr", "td", "th");

  /**
   * 인라인 style 속성의 위험 패턴을 제거한다.
   */
  static String sanitizeInlineStyles(String html) {
    if (html == null || html.isEmpty()) {
      return html;
    }
    return html.replaceAll("(?i)url\\(\\s*['\"]?javascript:", "url(blocked:")
        .replaceAll("(?i)url\\(\\s*['\"]?data:text/html", "url(blocked:")
        .replaceAll("(?i)url\\(\\s*['\"]?vbscript:", "url(blocked:")
        .replaceAll("(?i)expression\\s*\\(", "(blocked:");
  }

  /**
   * CSS를 정제한다.
   *
   * <p>UTF-8 바이트 기준으로 크기를 검사한다.
   * MySQL TEXT 컬럼의 최대치는 65,535바이트이므로,
   * 65,536바이트부터 거부한다(경계값 포함). 초과 시 500이 아니라
   * {@link IllegalArgumentException}(→ HTTP 400)을 던진다.
   */
  public static String sanitizeCss(String css) {
    if (css == null || css.isBlank()) {
      return "";
    }
    int byteSize = css.getBytes(StandardCharsets.UTF_8).length;
    if (byteSize >= VisualEditorService.MAX_CSS_SIZE) {
      throw new IllegalArgumentException(
          "CSS 크기가 제한(64KB)을 초과했습니다. (현재 " + byteSize + "바이트)");
    }
    return sanitizeCssValue(css);
  }

  /**
   * CSS 값에서 위험 패턴을 제거한다. (크기 검사는 하지 않는다.
   * 인라인 style 등 작은 값에 사용)
   */
  public static String sanitizeCssValue(String css) {
    if (css == null || css.isEmpty()) {
      return css;
    }
    String cleaned = css.replaceAll("(?i)@import[^;]+;", "");
    cleaned = cleaned.replaceAll("(?i)expression\\s*\\(", "(blocked:");
    cleaned = cleaned.replaceAll("(?i)behavior\\s*:", "blocked-behavior:");
    cleaned = cleaned.replaceAll("(?i)url\\s*\\(\\s*['\"]?javascript:", "url(blocked:");
    cleaned = cleaned.replaceAll("(?i)url\\(\\s*['\"]?data:text/html", "url(blocked:");
    cleaned = cleaned.replaceAll("(?i)url\\(\\s*['\"]?vbscript:", "url(blocked:");
    return cleaned;
  }

  /**
   * GrapesJS projectData JSON을 구조적으로 검사·정제한다.
   *
   * @return 정제된 JSON 문자열
   * @throws IllegalArgumentException 유효하지 않은 JSON이거나
   *     허용되지 않은 tagName/type/URL이 있을 때
   */
  public static String sanitizeProjectData(String projectData) {
    if (projectData == null || projectData.isBlank() || projectData.equals("{}")) {
      return "{}";
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(projectData);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("프로젝트 데이터가 유효한 JSON이 아닙니다.");
    }
    // GrapesJS가 프레임마다 붙이는 문서 껍데기(head, docEl)는 사용자 콘텐츠가 아니다.
    // 정해진 빈 모양일 때만 떼어 두었다가 정제 후 그대로 복원하고, 그 외에는 거부한다.
    List<PageType> pageTypes = detachPageTypes(root);
    List<FrameShell> shells = detachFrameShells(root);
    JsonNode sanitized = sanitizeNode(root, "projectData");
    reattachFrameShells(sanitized, shells);
    reattachPageTypes(sanitized, pageTypes);
    try {
      return MAPPER.writeValueAsString(sanitized);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("프로젝트 데이터 직렬화에 실패했습니다.");
    }
  }

  /**
   * GrapesJS 0.23.x가 {@code pages[].frames[].component} 아래에 생성하는 문서 껍데기.
   * 실제 출력은 {@code head: {"type":"head"}}, {@code docEl: {"tagName":"html"}} 이다.
   */
  private record FrameShell(int page, int frame, JsonNode head, JsonNode docEl) {
  }

  /** GrapesJS 페이지 객체의 {@code type}(기본 "main"). 컴포넌트 type이 아니라 페이지 종류다. */
  private record PageType(int page, JsonNode type) {
  }

  private static final String MAIN_PAGE_TYPE = "main";

  /**
   * 각 프레임 컴포넌트에서 head/docEl을 떼어 낸다. 정해진 빈 모양이 아니면 거부한다.
   *
   * <p>허용 모양: head는 {@code type}만(값 "head"), docEl은 {@code tagName}만(값 "html").
   * 여기에 components/attributes/content 같은 키가 더해지면(예: head 안의 script)
   * 사용자 콘텐츠가 섞인 것이므로 400으로 거부한다. 이 예외는 프레임 컴포넌트 바로 아래
   * 두 키에만 적용되며, 트리 어디의 {@code type=head}/{@code tagName=html}도 통과시키지 않는다.
   */
  private static List<FrameShell> detachFrameShells(JsonNode root) {
    List<FrameShell> shells = new ArrayList<>();
    JsonNode pages = root.path("pages");
    if (!pages.isArray()) {
      return shells;
    }
    for (int pi = 0; pi < pages.size(); pi++) {
      JsonNode frames = pages.get(pi).path("frames");
      if (!frames.isArray()) {
        continue;
      }
      for (int fi = 0; fi < frames.size(); fi++) {
        JsonNode component = frames.get(fi).path("component");
        if (!component.isObject()) {
          continue;
        }
        ObjectNode obj = (ObjectNode) component;
        String base = "projectData.pages[" + pi + "].frames[" + fi + "].component";
        JsonNode head = obj.remove("head");
        JsonNode docEl = obj.remove("docEl");
        requireShell(head, "type", "head", base + ".head");
        requireShell(docEl, "tagName", "html", base + ".docEl");
        shells.add(new FrameShell(pi, fi, head, docEl));
      }
    }
    return shells;
  }

  /**
   * 각 페이지 객체의 {@code type}을 떼어 낸다. GrapesJS 기본값 "main"만 허용하고 그 외는 거부한다.
   * (컴포넌트 type 허용 목록과 별개이므로 페이지 객체에만 적용한다.)
   */
  private static List<PageType> detachPageTypes(JsonNode root) {
    List<PageType> types = new ArrayList<>();
    JsonNode pages = root.path("pages");
    if (!pages.isArray()) {
      return types;
    }
    for (int pi = 0; pi < pages.size(); pi++) {
      JsonNode page = pages.get(pi);
      if (!page.isObject()) {
        continue;
      }
      JsonNode type = ((ObjectNode) page).remove("type");
      if (type != null && !(type.isTextual()
          && MAIN_PAGE_TYPE.equalsIgnoreCase(type.asText()))) {
        throw new IllegalArgumentException(
            "허용되지 않은 페이지 구조입니다: projectData.pages[" + pi + "].type");
      }
      types.add(new PageType(pi, type));
    }
    return types;
  }

  private static void reattachPageTypes(JsonNode sanitizedRoot, List<PageType> types) {
    for (PageType t : types) {
      JsonNode page = sanitizedRoot.path("pages").path(t.page());
      if (page.isObject() && t.type() != null) {
        ((ObjectNode) page).set("type", t.type());
      }
    }
  }

  private static void requireShell(JsonNode node, String key, String expected, String path) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    boolean ok = node.isObject()
        && node.size() == 1
        && node.path(key).isTextual()
        && expected.equalsIgnoreCase(node.path(key).asText());
    if (!ok) {
      throw new IllegalArgumentException("허용되지 않은 문서 구조입니다: " + path);
    }
  }

  private static void reattachFrameShells(JsonNode sanitizedRoot, List<FrameShell> shells) {
    for (FrameShell shell : shells) {
      JsonNode component = sanitizedRoot.path("pages").path(shell.page())
          .path("frames").path(shell.frame()).path("component");
      if (!component.isObject()) {
        continue;
      }
      ObjectNode obj = (ObjectNode) component;
      if (shell.head() != null && !shell.head().isMissingNode() && !shell.head().isNull()) {
        obj.set("head", shell.head());
      }
      if (shell.docEl() != null && !shell.docEl().isMissingNode() && !shell.docEl().isNull()) {
        obj.set("docEl", shell.docEl());
      }
    }
  }

  private static JsonNode sanitizeNode(JsonNode node, String path) {
    if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node.deepCopy();
      List<String> keys = new ArrayList<>();
      Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
      while (fields.hasNext()) {
        keys.add(fields.next().getKey());
      }
      for (String key : keys) {
        String lowerKey = key.toLowerCase();
        JsonNode value = obj.get(key);

        // script 속성 제거
        if (lowerKey.equals("script") || BLOCKED_ATTRS.contains(lowerKey)) {
          obj.remove(key);
          continue;
        }
        // 이벤트 핸들러 제거
        if (lowerKey.startsWith("on") && lowerKey.length() > 2) {
          obj.remove(key);
          continue;
        }
        // tagName 허용 목록 검사
        if (lowerKey.equals("tagname") && value.isTextual()) {
          String tagName = value.asText().toLowerCase();
          if (!ALLOWED_TAG_NAMES.contains(tagName)) {
            throw new IllegalArgumentException(
                "허용되지 않은 태그입니다: " + path + "." + key + "=" + value.asText());
          }
          continue;
        }
        // type 허용 목록 검사
        if (lowerKey.equals("type") && value.isTextual()) {
          String type = value.asText().toLowerCase();
          if (!ALLOWED_COMPONENT_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                "허용되지 않은 컴포넌트 타입입니다: " + path + "." + key + "=" + value.asText());
          }
          continue;
        }
        // content 원시 HTML 정제
        if (lowerKey.equals("content") && value.isTextual()) {
          obj.put(key, sanitizeHtml(value.asText()));
          continue;
        }
        // components 문자열 HTML 정제
        if (lowerKey.equals("components") && value.isTextual()) {
          obj.put(key, sanitizeHtml(value.asText()));
          continue;
        }
        // attributes 객체 정제
        if (lowerKey.equals("attributes") && value.isObject()) {
          obj.set(key, sanitizeAttributes((ObjectNode) value, path + "." + key));
          continue;
        }
        // style 정제
        if (lowerKey.equals("style")) {
          if (value.isTextual()) {
            obj.put(key, sanitizeCssValue(value.asText()));
            continue;
          } else if (value.isObject()) {
            obj.set(key, sanitizeStyleObject((ObjectNode) value, path + "." + key));
            continue;
          }
        }
        obj.set(key, sanitizeNode(value, path + "." + key));
      }
      return obj;
    } else if (node.isArray()) {
      ArrayNode arr = (ArrayNode) node.deepCopy();
      for (int i = 0; i < arr.size(); i++) {
        arr.set(i, sanitizeNode(arr.get(i), path + "[" + i + "]"));
      }
      return arr;
    }
    return node;
  }

  /**
   * 컴포넌트 attributes 객체를 정제한다.
   * URL 속성은 스킴을 검사하고, style 속성은 CSS 값 정제를 적용한다.
   */
  private static ObjectNode sanitizeAttributes(ObjectNode attrs, String path) {
    ObjectNode cleaned = attrs.deepCopy();
    List<String> keys = new ArrayList<>();
    Iterator<String> names = cleaned.fieldNames();
    while (names.hasNext()) {
      keys.add(names.next());
    }
    for (String key : keys) {
      String lowerKey = key.toLowerCase();
      JsonNode value = cleaned.get(key);
      if (lowerKey.equals("script") || BLOCKED_ATTRS.contains(lowerKey)
          || (lowerKey.startsWith("on") && lowerKey.length() > 2)) {
        cleaned.remove(key);
        continue;
      }
      if (!value.isTextual()) {
        continue;
      }
      String text = value.asText();
      if (lowerKey.equals("style")) {
        cleaned.put(key, sanitizeCssValue(text));
      } else if (URL_ATTRS.contains(lowerKey)) {
        validateUrl(text, path + "." + key);
      }
    }
    return cleaned;
  }

  /**
   * style 객체의 각 값을 정제한다.
   */
  private static ObjectNode sanitizeStyleObject(ObjectNode style, String path) {
    ObjectNode cleaned = style.deepCopy();
    List<String> keys = new ArrayList<>();
    Iterator<String> names = cleaned.fieldNames();
    while (names.hasNext()) {
      keys.add(names.next());
    }
    for (String key : keys) {
      JsonNode value = cleaned.get(key);
      if (value.isTextual()) {
        cleaned.put(key, sanitizeCssValue(value.asText()));
      }
    }
    return cleaned;
  }

  /**
   * URL 스킴을 검사한다.
   *
   * <p>허용: http, https, mailto, tel, data:image/*, 상대경로(/, #, ?, 빈값).
   * Jackson이 유니코드 이스케이프를 이미 디코딩했으므로
   * {@code \\u006aavascript:} 같은 우회는 여기서 차단된다.
   * 제어문자를 제거한 뒤 검사하므로 탭·개행 우회도 차단된다.
   */
  static void validateUrl(String url, String path) {
    if (url == null) {
      return;
    }
    // 제어문자 제거 (javascript: 우회 방지)
    String normalized = url.replaceAll("[\\u0000-\\u0020]", "").toLowerCase();
    if (normalized.isEmpty()
        || normalized.startsWith("#")
        || normalized.startsWith("/")
        || normalized.startsWith("?")) {
      return;
    }
    int colon = normalized.indexOf(':');
    if (colon <= 0) {
      return; // 스킴 없음 → 상대경로로 취급
    }
    String scheme = normalized.substring(0, colon);
    if (scheme.equals("data")) {
      if (!normalized.startsWith("data:image/")) {
        throw new IllegalArgumentException("허용되지 않은 URL입니다: " + path);
      }
      return;
    }
    if (!ALLOWED_URL_SCHEMES.contains(scheme)) {
      throw new IllegalArgumentException("허용되지 않은 URL입니다: " + path);
    }
  }
}
