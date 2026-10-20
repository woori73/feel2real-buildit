package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HelpMarkdownTest {

  @Test
  void toHtml_escapesRawHtml() {
    // given
    String md = "<script>alert(1)</script>";
    // when
    String html = HelpMarkdown.toHtml(md);
    // then
    assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
  }

  @Test
  void toHtml_rendersHeadingListsAndNote() {
    // given
    String md = "## 제목\n1. 하나\n2. 둘\n- 점\n> 주의 **중요**";
    // when
    String html = HelpMarkdown.toHtml(md);
    // then
    assertThat(html).contains("<h2>제목</h2>", "<ol><li>하나</li><li>둘</li></ol>", "<ul><li>점</li></ul>",
        "<p class=\"note\">주의 <strong>중요</strong></p>");
  }

  @Test
  void toHtml_rendersOnlyInternalHelpLinks() {
    // given
    String md = "[반](/help/academycraft/classes) [밖](https://evil.example) [js](javascript:alert(1))";
    // when
    String html = HelpMarkdown.toHtml(md);
    // then
    assertThat(html).contains("<a href=\"/help/academycraft/classes\">반</a>");
    assertThat(html).doesNotContain("href=\"https://evil").doesNotContain("href=\"javascript");
  }
}
