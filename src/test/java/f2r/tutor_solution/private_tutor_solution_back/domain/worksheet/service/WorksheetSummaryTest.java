package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.Question;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WorksheetSummary — 학습지 목록의 타입·학년·요약")
class WorksheetSummaryTest {

  private static Question q(String source, String setTitle, String type, String level) {
    return new Question(source + ":1", source, "SET", setTitle, 1, type, "", level, "", "", null, List.of(), "①", "");
  }

  static final Question NAESIN_A = q("NAESIN", "압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회", "어법", "상");
  static final Question NAESIN_B = q("NAESIN", "압구정중 1학년 2학기 기말 2회", "독해", "하");
  static final Question GRAMMAR = q("GRAMMAR", "CH 1 · 수일치·시제 일치 · 1부", "어법", null);

  @Test
  @DisplayName("given 내신만·어법만·섞인 문항 when kind then 내신·어법·혼합")
  void kind_bySources() {
    // given & when & then
    assertThat(WorksheetSummary.kind(List.of(NAESIN_A))).isEqualTo("내신");
    assertThat(WorksheetSummary.kind(List.of(GRAMMAR))).isEqualTo("어법");
    assertThat(WorksheetSummary.kind(List.of(NAESIN_A, GRAMMAR))).isEqualTo("혼합");
    assertThat(WorksheetSummary.kind(List.of())).isEmpty();
  }

  @Test
  @DisplayName("given 중2·중1 내신과 어법 문항 when grade then 나온 순서대로 '중2·중1·예비고1'")
  void grade_fromSetTitles() {
    // when & then
    assertThat(WorksheetSummary.grade(List.of(NAESIN_A, NAESIN_B, GRAMMAR, NAESIN_A))).isEqualTo("중2·중1·예비고1");
  }

  @Test
  @DisplayName("given 문항 3개 when scope then 난이도별 수 | 많은 유형 순 | 첫 범위 외 N을 한 줄로")
  void scope_summarizes() {
    // when
    String scope = WorksheetSummary.scope(List.of(NAESIN_A, NAESIN_B, GRAMMAR));

    // then
    assertThat(scope).isEqualTo("하 1·상 1 | 어법 2, 독해 1 | 압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회 외 2");
    assertThat(WorksheetSummary.scope(List.of())).isEmpty();
    assertThat(WorksheetSummary.scope(List.of(GRAMMAR))).isEqualTo("어법 1 | CH 1 · 수일치·시제 일치 · 1부");
    assertThat(WorksheetSummary.clip("가나다라마", 4)).isEqualTo("가나다…");
  }
}
