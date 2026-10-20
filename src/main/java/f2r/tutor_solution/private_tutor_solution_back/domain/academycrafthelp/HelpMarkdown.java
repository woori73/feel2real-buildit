package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

/** 도움말 전용 최소 markdown → HTML. 모든 텍스트를 먼저 escape해 본문 HTML 주입을 막는다. */
public final class HelpMarkdown {

  private HelpMarkdown() {}

  public static String toHtml(String md) {
    StringBuilder out = new StringBuilder();
    boolean inUl = false;
    boolean inOl = false;
    for (String line : md.split("\n")) {
      String t = line.strip();
      boolean ul = t.startsWith("- ");
      boolean ol = t.matches("^\\d+\\. .*");
      if (!ul && inUl) { out.append("</ul>"); inUl = false; }
      if (!ol && inOl) { out.append("</ol>"); inOl = false; }
      if (t.isEmpty()) {
        continue;
      }
      if (t.startsWith("## ")) {
        out.append("<h2>").append(inline(t.substring(3))).append("</h2>");
      } else if (t.startsWith("> ")) {
        out.append("<p class=\"note\">").append(inline(t.substring(2))).append("</p>");
      } else if (ul) {
        if (!inUl) { out.append("<ul>"); inUl = true; }
        out.append("<li>").append(inline(t.substring(2))).append("</li>");
      } else if (ol) {
        if (!inOl) { out.append("<ol>"); inOl = true; }
        out.append("<li>").append(inline(t.substring(t.indexOf(". ") + 2))).append("</li>");
      } else {
        out.append("<p>").append(inline(t)).append("</p>");
      }
    }
    if (inUl) out.append("</ul>");
    if (inOl) out.append("</ol>");
    return out.toString();
  }

  static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static String inline(String s) {
    return escape(s)
        .replaceAll("\\[([^\\]]+)\\]\\((/help/academycraft(?:/[a-z0-9-]+)?)\\)", "<a href=\"$2\">$1</a>")
        .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
  }
}
