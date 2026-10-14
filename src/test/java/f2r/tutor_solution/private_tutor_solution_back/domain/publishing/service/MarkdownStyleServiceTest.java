package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownStyleServiceTest {

  private final MarkdownStyleService service = new MarkdownStyleService();

  @Test
  @DisplayName("H1/H2/H3 마커를 제거하고 제목 스타일을 매핑한다")
  void parseHeadings() {
    String md = "# 대제목\n\n## 중제목\n\n### 소제목\n\n본문입니다.";
    MarkdownStyleService.ParsedMarkdown result = service.parse(md);

    assertThat(result.cleanText()).doesNotContain("#");
    assertThat(result.cleanText()).contains("대제목");
    assertThat(result.headingBlocks()).hasSize(3);
    assertThat(result.headingBlocks().get(0).style()).isEqualTo("ko-heading1");
    assertThat(result.headingBlocks().get(0).original()).isEqualTo("# 대제목");
    assertThat(result.headingBlocks().get(1).style()).isEqualTo("ko-heading2");
    assertThat(result.headingBlocks().get(2).style()).isEqualTo("ko-heading3");
  }

  @Test
  @DisplayName("인용문은 ko-quote 스타일로 매핑한다")
  void parseQuote() {
    String md = "> 인용문입니다.\n\n본문입니다.";
    MarkdownStyleService.ParsedMarkdown result = service.parse(md);

    assertThat(result.cleanText()).doesNotContain(">");
    assertThat(result.headingBlocks()).hasSize(1);
    assertThat(result.headingBlocks().get(0).style()).isEqualTo("ko-quote");
  }

  @Test
  @DisplayName("목록 마커는 불릿으로 변환하고 본문 스타일을 유지한다")
  void parseList() {
    String md = "- 항목 1\n- 항목 2\n\n본문입니다.";
    MarkdownStyleService.ParsedMarkdown result = service.parse(md);

    assertThat(result.cleanText()).contains("• 항목 1");
    // 목록은 headingBlocks에 포함되지 않음 (ko-body 유지)
    assertThat(result.headingBlocks()).isEmpty();
  }

  @Test
  @DisplayName("일반 본문은 그대로 유지한다")
  void parseBody() {
    String md = "일반 본문입니다.\n\n두 번째 문단입니다.";
    MarkdownStyleService.ParsedMarkdown result = service.parse(md);

    assertThat(result.cleanText()).contains("일반 본문입니다.");
    assertThat(result.headingBlocks()).isEmpty();
  }

  @Test
  @DisplayName("빈 입력도 안전하게 처리한다")
  void parseEmpty() {
    MarkdownStyleService.ParsedMarkdown result = service.parse("");
    assertThat(result.cleanText()).isEmpty();
    assertThat(result.headingBlocks()).isEmpty();
  }
}
