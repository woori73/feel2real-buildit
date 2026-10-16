package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Documents;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExamInsightPromptBuilderTest {

  static String doc(String title) {
    return title + "\n" + "## 1. a\n" + "x".repeat(80) + "\n## 2. b\n" + "y".repeat(80) + "\n## 3. c\n" + "z".repeat(80);
  }

  static String valid() {
    return ExamInsightPromptBuilder.DOC1_MARKER + "\n" + doc("# 기출문제 분석") + "\n"
        + ExamInsightPromptBuilder.DOC2_MARKER + "\n" + doc("# 기출문제 기반 출제방향");
  }

  @Test
  void parse_두_문서를_나눈다() {
    // given a well formed model output
    String raw = valid();
    // when
    Documents d = ExamInsightPromptBuilder.parse(raw);
    // then
    assertThat(d.analysis()).startsWith("# 기출문제 분석");
    assertThat(d.direction()).startsWith("# 기출문제 기반 출제방향");
  }

  @Test
  void parse_마커가_없으면_실패한다() {
    // given output without markers
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse("그냥 텍스트")).isInstanceOf(ExamInsightException.class);
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(null)).isInstanceOf(ExamInsightException.class);
  }

  @Test
  void parse_순서가_뒤바뀌면_실패한다() {
    // given DOC2 before DOC1
    String raw = ExamInsightPromptBuilder.DOC2_MARKER + doc("a") + ExamInsightPromptBuilder.DOC1_MARKER + doc("b");
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(raw)).isInstanceOf(ExamInsightException.class);
  }

  @Test
  void parse_너무_짧은_문서는_완료로_보지_않는다() {
    // given tiny documents
    String raw = ExamInsightPromptBuilder.DOC1_MARKER + "\n## a\n## b\n## c\n" + ExamInsightPromptBuilder.DOC2_MARKER + "\n## a";
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(raw))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_AI_OUTPUT"));
  }

  @Test
  void wellFormed_소제목이_부족하면_거짓() {
    // given long text with only one heading
    String doc = "## only\n" + "x".repeat(300);
    // when / then
    assertThat(ExamInsightPromptBuilder.wellFormed(doc)).isFalse();
  }

  @Test
  void userPrompt_빈_선택정보는_입력없음으로_표시하고_메모는_한줄로_만든다() {
    // given
    Meta meta = new Meta("", null, "능률", "중간고사");
    // when
    String p = ExamInsightPromptBuilder.userPrompt(
        List.of(new Source("a.pdf", "PAST")), List.of(new Source("b.txt", "SCOPE")), meta, "line1\nline2");
    // then
    assertThat(p).contains("- 학교명: (입력 없음)").contains("- 출판사: 능률").contains("[기출문제 자료]")
        .contains("PAST").contains("[시험범위 자료]").contains("SCOPE").contains("line1 line2");
  }

  @Test
  void 시스템_프롬프트는_자료밖_사실금지와_추정표시와_주입방어를_담는다() {
    // given / when / then
    assertThat(ExamInsightPromptBuilder.SYSTEM_PROMPT).contains("자료에서 확인되지 않음").contains("추정").contains("무시하라");
  }
}
