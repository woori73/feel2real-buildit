package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * HTML 학습지·기출문제에서 분석용 텍스트를 뽑는다.
 *
 * <p><b>안전:</b> jsoup은 HTML을 문자열로 파싱할 뿐 스크립트를 실행하지 않고, 외부 리소스를 가져오지도 않는다.
 * 우리는 DOM에서 글자만 꺼내 새 문자열을 만들고 원본 마크업은 어디에도 되돌려 보내지 않는다.
 * 스크립트·스타일·프레임·객체는 읽지 않고, 외부 이미지는 가져오지 않는다(SSRF 방지).
 *
 * <p><b>숨김 내용:</b> 화면에서 가려져 있는 내용({@code hidden}, {@code display:none}, 스타일시트로 숨긴 클래스)은 버리지 않고
 * {@code [숨김]…[/숨김]}으로 표시해 남긴다. "정답 보기" 버튼을 눌러야 보이는 정답·해설이 학습지에 흔하기 때문이다.
 * 분석 모델은 이 표시를 보고 일반 본문과 구분한다.
 *
 * <p><b>구조 보존:</b> 표는 {@code | 셀 | 셀 |} 줄로, 목록은 번호/글머리표로, 문단·제목은 줄바꿈으로 둔다.
 * 문제 번호·선택지(①~⑤)는 원문 글자 그대로 남는다. 밑줄·굵게·취소선과 색으로 구분된 글자(필기·정답·해설)는
 * {@code [색:값]글자[/색]} 같은 표시로 남겨 분석 모델이 구분할 수 있게 한다.
 * 문서에 내장된 이미지(data URI)는 번호를 매겨 돌려주고, 본문에는 {@code {{IMG:n}}} 자리표시자를 둔다.
 */
public final class ExamInsightHtmlExtractor {

  /** 본문에 남기는 이미지 자리표시자. 서비스가 이미지 인식 결과로 바꿔 넣는다. */
  public static final String IMAGE_PLACEHOLDER = "{{IMG:%d}}";

  /** 내장 이미지 한 장. */
  public record EmbeddedImage(int index, String mime, byte[] bytes, String alt) {}

  /** 추출 결과: 본문 텍스트, 내장 이미지, 가져오지 않은 외부 이미지 수. */
  public record Result(String text, List<EmbeddedImage> images, int skippedExternalImages, int skippedEmbeddedImages) {}

  private static final Set<String> DROP_TAGS = Set.of(
      "script", "style", "noscript", "iframe", "frame", "frameset", "object", "embed", "applet", "link", "meta",
      "base", "template", "canvas", "audio", "video", "source", "track", "head", "title", "svg", "math", "dialog");
  private static final Set<String> BLOCK_TAGS = Set.of(
      "p", "div", "section", "article", "header", "footer", "main", "nav", "aside", "blockquote", "pre", "ul", "ol",
      "li", "h1", "h2", "h3", "h4", "h5", "h6", "dl", "dt", "dd", "figure", "figcaption", "fieldset", "form",
      "address", "hr", "details", "summary", "center");
  private static final Pattern STYLE_COLOR =
      Pattern.compile("(?i)(?<![-\\w])color\\s*:\\s*([^;\"]+)");
  private static final Pattern STYLE_BG =
      Pattern.compile("(?i)background(?:-color)?\\s*:\\s*([^;\"]+)");
  private static final Pattern HEX = Pattern.compile("(?i)#([0-9a-f]{3}|[0-9a-f]{6})\\b");
  private static final Pattern RGB =
      Pattern.compile("(?i)rgba?\\(\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})");
  private static final Set<String> NEUTRAL_NAMES = Set.of(
      "black", "white", "gray", "grey", "silver", "dimgray", "dimgrey", "gainsboro", "whitesmoke", "lightgray",
      "lightgrey", "darkgray", "darkgrey", "inherit", "initial", "unset", "transparent", "currentcolor", "none",
      "windowtext", "canvastext");
  private static final Pattern ANNOTATION_CLASS =
      Pattern.compile("(?i)(answer|correct|solution|explain|handwrit|memo|annot|highlight|ans\\b|sol\\b|note)");
  private static final Pattern WS = Pattern.compile("[ \\t\\u00a0\\u3000]+");
  private static final Pattern DATA_IMAGE =
      Pattern.compile("(?is)^data:(image/(?:jpeg|jpg|png|webp));base64,(.*)$");
  private static final Pattern STYLE_RULE = Pattern.compile("([^{}]+)\\{([^}]*)\\}");
  private static final Pattern HIDING =
      Pattern.compile("(?i)(display\\s*:\\s*none|visibility\\s*:\\s*hidden)");
  private static final int MAX_DEPTH = 120;
  private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;

  private final int maxEmbeddedImages;

  public ExamInsightHtmlExtractor(int maxEmbeddedImages) {
    this.maxEmbeddedImages = maxEmbeddedImages;
  }

  public Result extract(byte[] html) throws IOException {
    Document doc = Jsoup.parse(new ByteArrayInputStream(html), null, "");
    Ctx ctx = new Ctx();
    collectHiddenSelectors(doc, ctx);
    StringBuilder out = new StringBuilder();
    render(doc.body(), ctx, out, 0);
    String text = ExamInsightTextExtractor.clean(out.toString())
        .replaceAll("(?m)[ \\t]{2,}", " ").replaceAll("(?m)[ \\t]+$", "");
    return new Result(text, List.copyOf(ctx.images),
        ctx.skippedExternal, ctx.skippedEmbedded);
  }

  // ───────────────────────── 렌더링 ─────────────────────────

  /** 문서 전체가 공유하는 상태. 표 셀을 따로 렌더링해도 이미지 번호가 겹치지 않게 한 곳에서 센다. */
  private static final class Ctx {
    final List<EmbeddedImage> images = new ArrayList<>();
    int skippedExternal;
    int skippedEmbedded;
    final Set<String> hiddenClasses = new java.util.HashSet<>();
    final Set<String> hiddenIds = new java.util.HashSet<>();
  }

  /** {@code <style>}의 단순 규칙({@code .클래스}·{@code #아이디} 선택자)에서 display:none·visibility:hidden으로 숨긴 대상을 모은다. */
  private static void collectHiddenSelectors(Document doc, Ctx ctx) {
    for (Element style : doc.select("style")) {
      Matcher rule = STYLE_RULE.matcher(style.data());
      while (rule.find()) {
        if (!HIDING.matcher(rule.group(2)).find()) continue;
        for (String selector : rule.group(1).split(",")) {
          String sel = selector.trim();
          if (sel.matches("\\.[\\w-]+")) ctx.hiddenClasses.add(sel.substring(1));
          else if (sel.matches("#[\\w-]+")) ctx.hiddenIds.add(sel.substring(1));
        }
      }
    }
  }

  private void render(Node node, Ctx ctx, StringBuilder out, int depth) {
    if (node instanceof Comment) return;
    if (node instanceof TextNode t) {
      appendText(out, t.text());
      return;
    }
    if (!(node instanceof Element el)) return;
    String tag = el.normalName();
    if (DROP_TAGS.contains(tag)) return;
    if (isHidden(el, ctx)) {
      StringBuilder inner = new StringBuilder();
      renderVisible(el, tag, ctx, inner, depth);
      String hiddenText = inner.toString().trim();
      if (!hiddenText.isEmpty()) {
        breakLine(out);
        out.append("[숨김]").append(hiddenText).append("[/숨김]");
        breakLine(out);
      }
      return;
    }
    renderVisible(el, tag, ctx, out, depth);
  }

  private void renderVisible(Element el, String tag, Ctx ctx, StringBuilder out, int depth) {
    if (depth > MAX_DEPTH) {
      appendText(out, el.text());
      return;
    }
    switch (tag) {
      case "br" -> out.append('\n');
      case "img" -> image(el, ctx, out);
      case "table" -> table(el, ctx, out, depth);
      case "input" -> input(el, out);
      case "textarea" -> appendText(out, el.text());
      case "select" -> { /* 선택 목록은 본문이 아니다 */ }
      default -> inlineOrBlock(el, tag, ctx, out, depth);
    }
  }

  private void inlineOrBlock(Element el, String tag, Ctx ctx, StringBuilder out, int depth) {
    boolean block = BLOCK_TAGS.contains(tag);
    if (block) breakLine(out);
    if (tag.equals("li")) {
      Element parent = el.parent();
      boolean ordered = parent != null && parent.normalName().equals("ol");
      out.append(ordered ? (el.elementSiblingIndex() + 1) + ". " : "- ");
    }
    String color = annotationOf(el);
    out.append(color != null ? "[색:" + color + "]" : "").append(markerOpen(tag));
    for (Node child : el.childNodes()) render(child, ctx, out, depth + 1);
    out.append(markerClose(tag)).append(color != null ? "[/색]" : "");
    if (block) breakLine(out);
  }

  /** 표: 한 행을 한 줄로, 셀은 {@code |}로 구분한다. 셀 안의 줄바꿈은 공백 하나로 접는다. */
  private void table(Element table, Ctx ctx, StringBuilder out, int depth) {
    breakLine(out);
    for (Element row : table.select("tr")) {
      if (!row.closest("table").equals(table)) continue; // 중첩 표의 행은 안쪽 표가 처리한다
      StringBuilder line = new StringBuilder("|");
      for (Element cell : row.children()) {
        if (!cell.normalName().equals("td") && !cell.normalName().equals("th")) continue;
        StringBuilder cellOut = new StringBuilder();
        for (Node child : cell.childNodes()) render(child, ctx, cellOut, depth + 1);
        String text = WS.matcher(cellOut.toString().replaceAll("\\s*\\n\\s*", " ")).replaceAll(" ").trim();
        String ann = annotationOf(cell);
        if (ann != null && !text.isEmpty()) text = "[색:" + ann + "]" + text + "[/색]";
        line.append(' ').append(text.replace("|", "/")).append(" |");
      }
      if (line.length() > 1) out.append(line).append('\n');
    }
    breakLine(out);
  }

  private void image(Element img, Ctx ctx, StringBuilder out) {
    String alt = img.attr("alt").trim();
    String src = img.attr("src").trim();
    if (src.regionMatches(true, 0, "data:image/", 0, 11)) {
      Matcher m = DATA_IMAGE.matcher(src);
      if (m.matches() && ctx.images.size() < maxEmbeddedImages) {
        try {
          byte[] bytes = Base64.getMimeDecoder().decode(m.group(2));
          if (bytes.length > 0 && bytes.length <= MAX_IMAGE_BYTES) {
            int idx = ctx.images.size() + 1;
            ctx.images.add(new EmbeddedImage(idx, m.group(1).toLowerCase(Locale.ROOT), bytes, alt));
            out.append(' ').append(String.format(IMAGE_PLACEHOLDER, idx)).append(' ');
            return;
          }
        } catch (IllegalArgumentException ignored) {
          // 잘못된 base64는 처리하지 않은 이미지로 센다
        }
      }
      ctx.skippedEmbedded++;
    } else if (!src.isEmpty()) {
      ctx.skippedExternal++; // http(s)·상대경로·file: 등은 가져오지 않는다
    }
    if (!alt.isEmpty()) out.append(" (그림 설명: ").append(alt).append(") ");
  }

  private static void input(Element in, StringBuilder out) {
    String type = in.attr("type").toLowerCase(Locale.ROOT);
    if (type.equals("checkbox") || type.equals("radio")) {
      out.append(in.hasAttr("checked") ? " [선택됨] " : " [ ] ");
    } else if (type.isEmpty() || type.equals("text")) {
      String v = in.attr("value").trim();
      if (!v.isEmpty()) out.append(" [입력: ").append(v).append("] ");
    }
  }

  // ───────────────────────── 서식 표시 ─────────────────────────

  private static String markerOpen(String tag) {
    return switch (tag) {
      case "u", "ins" -> "__";
      case "b", "strong" -> "**";
      case "del", "s", "strike" -> "~~";
      case "mark" -> "[강조]";
      default -> "";
    };
  }

  private static String markerClose(String tag) {
    return switch (tag) {
      case "u", "ins" -> "__";
      case "b", "strong" -> "**";
      case "del", "s", "strike" -> "~~";
      case "mark" -> "[/강조]";
      default -> "";
    };
  }

  /** 색으로 구분된 글자(회색조가 아닌 글자색·배경색, 필기·정답·해설을 뜻하는 클래스)면 그 값을, 아니면 null을 돌려준다. */
  static String annotationOf(Element el) {
    String style = el.attr("style");
    String font = el.normalName().equals("font") ? el.attr("color") : "";
    for (String candidate : new String[] {font, find(STYLE_COLOR, style), find(STYLE_BG, style)}) {
      if (candidate != null && !candidate.isBlank() && isColored(candidate)) {
        return candidate.trim().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]\\s]+", "");
      }
    }
    String cls = el.className();
    if (!cls.isBlank()) {
      Matcher m = ANNOTATION_CLASS.matcher(cls);
      if (m.find()) return m.group(1).toLowerCase(Locale.ROOT);
    }
    return null;
  }

  private static String find(Pattern p, String s) {
    Matcher m = p.matcher(s);
    return m.find() ? m.group(1).trim() : null;
  }

  static boolean isColored(String value) {
    String v = value.trim().toLowerCase(Locale.ROOT).replace("!important", "").trim();
    if (NEUTRAL_NAMES.contains(v)) return false;
    Matcher hex = HEX.matcher(v);
    if (hex.find()) {
      String h = hex.group(1);
      if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
      return !grayscale(Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16),
          Integer.parseInt(h.substring(4, 6), 16));
    }
    Matcher rgb = RGB.matcher(v);
    if (rgb.find()) {
      return !grayscale(Integer.parseInt(rgb.group(1)), Integer.parseInt(rgb.group(2)), Integer.parseInt(rgb.group(3)));
    }
    return v.matches("[a-z]+"); // red, blue, orange …
  }

  private static boolean grayscale(int r, int g, int b) {
    return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 24;
  }

  private static boolean isHidden(Element el, Ctx ctx) {
    if (el.hasAttr("hidden")) return true;
    String style = el.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
    if (style.contains("display:none") || style.contains("visibility:hidden")) return true;
    if (!el.id().isEmpty() && ctx.hiddenIds.contains(el.id())) return true;
    for (String cls : el.classNames()) {
      if (ctx.hiddenClasses.contains(cls)) return true;
    }
    return false;
  }

  // ───────────────────────── 문자열 도우미 ─────────────────────────

  private static void appendText(StringBuilder out, String raw) {
    String t = WS.matcher(raw.replace('\n', ' ').replace('\r', ' ')).replaceAll(" ");
    if (t.isBlank()) {
      if (out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '\n') out.append(' ');
      return;
    }
    out.append(t);
  }

  private static void breakLine(StringBuilder out) {
    int n = out.length();
    while (n > 0 && out.charAt(n - 1) == ' ') n--;
    out.setLength(n);
    if (n > 0 && out.charAt(n - 1) != '\n') out.append('\n');
  }
}
