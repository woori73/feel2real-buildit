package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Documents;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Meta;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightPromptBuilder.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExamInsightPromptBuilderTest {

  /** 합성 문서: 제목 + ## 1.~## {sections}. 소제목, 각 구간에 본문. */
  static String doc(String title, int sections) {
    StringBuilder sb = new StringBuilder(title).append('\n');
    for (int i = 1; i <= sections; i++) {
      sb.append("## ").append(i).append(". 구간").append(i).append('\n').append("본문 ".repeat(30)).append('\n');
    }
    return sb.toString();
  }

  static String valid() {
    return ExamInsightPromptBuilder.DOC1_MARKER + "\n" + doc("# 기출문제 분석", 7)
        + ExamInsightPromptBuilder.DOC2_MARKER + "\n" + doc("# 기출문제 기반 출제방향", 7)
        + ExamInsightPromptBuilder.END_MARKER;
  }

  @Test
  void parse_완성된_응답은_두_문서로_나뉜다() {
    // given a complete model output
    String raw = valid();
    // when
    Documents d = ExamInsightPromptBuilder.parse(raw);
    // then
    assertThat(d.analysis()).startsWith("# 기출문제 분석").contains("## 7. 구간7");
    assertThat(d.direction()).startsWith("# 기출문제 기반 출제방향").contains("## 7. 구간7");
    assertThat(d.direction()).doesNotContain(ExamInsightPromptBuilder.END_MARKER);
  }

  @Test
  void parse_완료_표시_뒤의_군더더기는_무시한다() {
    // given text after the end marker
    // when
    Documents d = ExamInsightPromptBuilder.parse(valid() + "\n이상입니다.");
    // then
    assertThat(d.direction()).doesNotContain("이상입니다");
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
    String raw = ExamInsightPromptBuilder.DOC2_MARKER + doc("# a", 7) + ExamInsightPromptBuilder.DOC1_MARKER
        + doc("# b", 7) + ExamInsightPromptBuilder.END_MARKER;
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(raw)).isInstanceOf(ExamInsightException.class);
  }

  @Test
  void parse_잘린_응답_완료표시가_없으면_실패한다() {
    // given a response cut off while writing document 2 (no end marker)
    String raw = ExamInsightPromptBuilder.DOC1_MARKER + "\n" + doc("# 기출문제 분석", 7)
        + ExamInsightPromptBuilder.DOC2_MARKER + "\n" + doc("# 기출문제 기반 출제방향", 5);
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(raw))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_AI_OUTPUT"));
  }

  @Test
  void parse_완료표시가_있어도_소제목이_모자란_부분_응답은_실패한다() {
    // given document 2 has only 5 of 7 sections but the model still wrote the end marker
    String raw = ExamInsightPromptBuilder.DOC1_MARKER + "\n" + doc("# 기출문제 분석", 7)
        + ExamInsightPromptBuilder.DOC2_MARKER + "\n" + doc("# 기출문제 기반 출제방향", 5) + ExamInsightPromptBuilder.END_MARKER;
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(raw)).isInstanceOf(ExamInsightException.class);
  }

  @Test
  void parse_문서1이_중간에_끊기고_문서2가_없으면_실패한다() {
    // given only part of document 1
    String raw = ExamInsightPromptBuilder.DOC1_MARKER + "\n" + doc("# 기출문제 분석", 4);
    // when / then
    assertThatThrownBy(() -> ExamInsightPromptBuilder.parse(raw)).isInstanceOf(ExamInsightException.class);
  }

  @Test
  void isComplete_소제목_번호가_건너뛰거나_순서가_틀리면_거짓() {
    // given headings 1,2,4,3,5,6,7 and 1..7 with a duplicate
    String swapped = doc("# t", 7).replace("## 3. 구간3", "## 4. 구간X").replace("## 4. 구간4", "## 3. 구간Y");
    String extra = doc("# t", 7) + "## 8. 구간8\n" + "본문 ".repeat(30);
    // when / then
    assertThat(ExamInsightPromptBuilder.isComplete(swapped)).isFalse();
    assertThat(ExamInsightPromptBuilder.isComplete(extra)).isFalse();
  }

  @Test
  void isComplete_마지막_구간이_비었거나_너무_짧으면_거짓() {
    // given section 7 has no body / a tiny body
    String empty = doc("# t", 6) + "## 7. 마지막\n";
    String tiny = doc("# t", 6) + "## 7. 마지막\n그리고";
    // when / then
    assertThat(ExamInsightPromptBuilder.isComplete(empty)).isFalse();
    assertThat(ExamInsightPromptBuilder.isComplete(tiny)).isFalse();
  }

  @Test
  void isComplete_제목이_없으면_거짓() {
    // given no leading "# " title
    String noTitle = doc("제목 없음", 7);
    // when / then
    assertThat(ExamInsightPromptBuilder.isComplete(noTitle)).isFalse();
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
  void 시스템_프롬프트는_자료밖_사실금지와_추정표시와_주입방어와_완료표시를_담는다() {
    // given / when / then
    assertThat(ExamInsightPromptBuilder.SYSTEM_PROMPT).contains("자료에서 확인되지 않음").contains("추정").contains("무시하라")
        .contains(ExamInsightPromptBuilder.END_MARKER);
  }
}
