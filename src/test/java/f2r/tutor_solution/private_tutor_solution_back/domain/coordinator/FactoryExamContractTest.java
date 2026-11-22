package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.coordinator.FactoryExamContract.Exam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("FactoryExamContract — 학생 풀이 페이지 게시 계약(디지털 모의고사)")
class FactoryExamContractTest {

  static final JsonMapper JSON = JsonMapper.builder().build();

  static ObjectNode exam(String code) {
    ObjectNode e = JSON.createObjectNode().put("examCode", code).put("title", "압구정중 2학년 국어 2학기 기말 1회")
        .put("subtitle", "문학 12~45쪽, 72~83쪽 · 1문항 · 4점").put("timeLimitSec", 900);
    ObjectNode q = e.putArray("questions").addObject().put("prompt", "1~2행의 표현 방식과 효과로 알맞은 것은?")
        .put("bodyText", "첫 집이 불을 켜면").put("answerKey", "b").put("points", 4).put("explanation", "정답 ② · 시의 표현 (4점)");
    q.putArray("choices").add("가").add("나").add("다").add("라").add("마");
    return e;
  }

  @Test
  @DisplayName("given 국어 묶음 when parse then 국어 머리 코드·과목 section·정답 대문자·만점을 읽고 학생 페이지는 국어 탭")
  void parse_readsSubjectPackage() {
    // when
    Exam e = FactoryExamContract.parse("KOREAN", exam("TP-NAESIN-KOR-APM2-2602-R01"), p -> false);

    // then
    assertThat(e.examCode()).isEqualTo("TP-NAESIN-KOR-APM2-2602-R01");
    assertThat(e.questions()).hasSize(1);
    assertThat(e.questions().get(0).section()).isEqualTo("KO");
    assertThat(e.questions().get(0).taskType()).isEqualTo("NAESIN_MC5");
    assertThat(e.questions().get(0).answerKey()).isEqualTo("B");
    assertThat(e.maxScore()).isEqualTo(4);
    assertThat(FactoryExamContract.studentUrl("KOREAN")).isEqualTo("/learn?tab=korean");
    assertThat(FactoryExamContract.studentUrl("SCIENCE")).isEqualTo("/learn?tab=science");
  }

  @Test
  @DisplayName("given 다른 과목 머리·영어 내신에 과목 머리·회차 없는 코드 when parse then 구분에 맞는 머리를 알려 주며 거절")
  void parse_rejectsWrongCodeHead() {
    // given & when & then
    assertThatThrownBy(() -> FactoryExamContract.parse("MATH", exam("TP-NAESIN-KOR-APM2-2602-R01"), p -> false))
        .hasMessageContaining("TP-NAESIN-MATH-");
    assertThatThrownBy(() -> FactoryExamContract.parse("NAESIN", exam("TP-NAESIN-SCI-APM2-2602-R01"), p -> false))
        .hasMessageContaining("다른 과목 머리");
    assertThatThrownBy(() -> FactoryExamContract.parse("KOREAN", exam("TP-NAESIN-KOR-APM2"), p -> false))
        .hasMessageContaining("-R");
  }

  @Test
  @DisplayName("given 수능·TOEIC 등 세트형 구분 when parse then 세트 마이그레이션으로 올린다며 거절")
  void parse_rejectsSetTracks() {
    // given & when & then
    for (String track : new String[] {"CSAT", "IBT", "SAT", "TOEIC"}) {
      assertThatThrownBy(() -> FactoryExamContract.parse(track, exam("TP-NAESIN-KOR-APM2-2602-R01"), p -> false))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("세트 마이그레이션");
      assertThat(FactoryExamContract.supports(track)).isFalse();
    }
  }

  @Test
  @DisplayName("given 선택지 4개·정답 F·배점 0·해설 형식 틀림·번호 건너뜀 when parse then 문항 번호를 붙여 모두 한 번에 알려 준다")
  void parse_collectsQuestionErrors() {
    // given
    ObjectNode e = exam("TP-NAESIN-KOR-APM2-2602-R01");
    ObjectNode q = (ObjectNode) e.get("questions").get(0);
    q.put("no", 3).put("answerKey", "F").put("points", 0).put("explanation", "해설만");
    q.putArray("choices").add("가").add("나").add("다").add("라");

    // when & then
    assertThatThrownBy(() -> FactoryExamContract.parse("KOREAN", e, p -> false))
        .hasMessageContaining("1번: no는").hasMessageContaining("1번: choices는 5개")
        .hasMessageContaining("1번: answerKey는 A~E 중 하나").hasMessageContaining("1번: points")
        .hasMessageContaining("1번: explanation");
  }

  @Test
  @DisplayName("given 사이트에 있는 그림·없는 그림·바깥 주소 그림 when parse then 있는 그림만 받는다")
  void parse_checksImages() {
    // given
    ObjectNode ok = exam("TP-NAESIN-KOR-APM2-2602-R01");
    ((ObjectNode) ok.get("questions").get(0)).put("prompt", "[[img:/naesin/k/r01/q01.webp]] 알맞은 것은?");
    ObjectNode missing = exam("TP-NAESIN-KOR-APM2-2602-R01");
    ((ObjectNode) missing.get("questions").get(0)).put("prompt", "[[img:/naesin/k/r01/q09.webp]]");
    ObjectNode outside = exam("TP-NAESIN-KOR-APM2-2602-R01");
    ((ObjectNode) outside.get("questions").get(0)).put("bodyText", "[[img:https://example.com/a.png]]");

    // when & then
    assertThat(FactoryExamContract.parse("KOREAN", ok, "/naesin/k/r01/q01.webp"::equals).questions()).hasSize(1);
    assertThatThrownBy(() -> FactoryExamContract.parse("KOREAN", missing, "/naesin/k/r01/q01.webp"::equals))
        .hasMessageContaining("사이트에 없는 그림");
    assertThatThrownBy(() -> FactoryExamContract.parse("KOREAN", outside, p -> true)).hasMessageContaining("형식만");
  }

  @Test
  @DisplayName("given 시간·문항 수·제목이 범위 밖 when parse then 거절")
  void parse_checksExamFields() {
    // given
    ObjectNode e = exam("TP-NAESIN-KOR-APM2-2602-R01").put("timeLimitSec", 10).put("title", "");
    e.putArray("questions");

    // when & then
    assertThatThrownBy(() -> FactoryExamContract.parse("KOREAN", e, p -> false))
        .hasMessageContaining("timeLimitSec").hasMessageContaining("title").hasMessageContaining("questions는 1~60문항");
  }
}
