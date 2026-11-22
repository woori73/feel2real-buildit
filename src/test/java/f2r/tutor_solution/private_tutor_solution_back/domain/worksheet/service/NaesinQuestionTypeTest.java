package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NaesinQuestionType — 내신 문항의 발문·유형·난이도 읽기")
class NaesinQuestionTypeTest {

  @Test
  @DisplayName("given '정답 ③ · 발문 (3점)' 해설 when stem then 발문만 꺼내고, 형식이 다르면 빈 값")
  void stem_readsHeadLine() {
    // given
    String explanation = "정답 ③ · (A)~(F)중 어법상 올바른 문장을 있는 대로 고른 것은? (3점)\n(C) raiseing → raising";

    // when & then
    assertThat(NaesinQuestionType.stem(explanation)).isEqualTo("(A)~(F)중 어법상 올바른 문장을 있는 대로 고른 것은?");
    assertThat(NaesinQuestionType.stem("해설만 있는 글")).isEmpty();
    assertThat(NaesinQuestionType.stem(null)).isEmpty();
  }

  @Test
  @DisplayName("given 여러 발문 when classify then 서술형 → 어법 → 어휘 → 대화 → 독해 순으로 먼저 맞는 유형")
  void classify_usesFirstMatchingRule() {
    // given & when & then
    assertThat(NaesinQuestionType.classify("우리말에 맞게 영작하시오")).isEqualTo(NaesinQuestionType.WRITING);
    assertThat(NaesinQuestionType.classify("대화의 흐름과 어법상 (A)~(C)에 들어갈 알맞은 것은?")).isEqualTo(NaesinQuestionType.GRAMMAR);
    assertThat(NaesinQuestionType.classify("윗글에서 찾을 수 없는 단어의 뜻은?")).isEqualTo(NaesinQuestionType.VOCABULARY);
    assertThat(NaesinQuestionType.classify("대화의 흐름에 맞게 ⓐ에 들어갈 말은?")).isEqualTo(NaesinQuestionType.DIALOGUE);
    assertThat(NaesinQuestionType.classify("내용 일치")).isEqualTo(NaesinQuestionType.READING);
  }

  @Test
  @DisplayName("given 규칙에 없는 발문·빈 발문 when classify then 기타")
  void classify_fallsBackToOther() {
    // given & when & then
    assertThat(NaesinQuestionType.classify("ⓐ~ⓒ 짝")).isEqualTo(NaesinQuestionType.OTHER);
    assertThat(NaesinQuestionType.classify(" ")).isEqualTo(NaesinQuestionType.OTHER);
    assertThat(NaesinQuestionType.classify(null)).isEqualTo(NaesinQuestionType.OTHER);
  }

  @Test
  @DisplayName("given 배점 when level then 2점 이하 하·3점 중·4점 이상 상, 배점 없으면 null")
  void level_fromPoints() {
    // given & when & then
    assertThat(NaesinQuestionType.level(2)).isEqualTo("하");
    assertThat(NaesinQuestionType.level(3)).isEqualTo("중");
    assertThat(NaesinQuestionType.level(5)).isEqualTo("상");
    assertThat(NaesinQuestionType.level(null)).isNull();
  }
}
