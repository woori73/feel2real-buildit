package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("FactoryPlan — 제작 요청의 공장 정보(과목·시험 구분 9종, 쪽수 구간, 문항 표시)")
class FactoryPlanTest {

  static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  @DisplayName("given 구분 목록 when 읽기 then 대표가 정한 9종이 순서대로 있고 학교 시험 구분은 5종이다")
  void tracks() {
    // given & when & then
    assertThat(FactoryPlan.TRACK_LABEL.values())
        .containsExactly("내신(영어)", "수능", "iBT", "SAT", "TOEIC", "국어", "수학", "사회", "과학");
    assertThat(FactoryPlan.SCHOOL_TRACKS).containsExactlyInAnyOrder("NAESIN", "KOREAN", "MATH", "SOCIAL", "SCIENCE");
    assertThat(FactoryPlan.ITEM_LABEL.values()).containsExactly("기출 평가 요소 기반", "시험 범위 연계", "개념 연습");
  }

  @Test
  @DisplayName("given 여러 줄 쪽수 구간(순서 섞임·쪽 글자·한 쪽짜리) when ranges then 시작 쪽 순서의 {from,to} 목록")
  void rangesFromLines() {
    // given
    var raw = JSON.createArrayNode().add("92~115").add("12 ~ 45").add("72-83쪽").add("60");

    // when
    List<Map<String, Integer>> out = FactoryPlan.ranges(raw);

    // then
    assertThat(out).containsExactly(Map.of("from", 12, "to", 45), Map.of("from", 60, "to", 60),
        Map.of("from", 72, "to", 83), Map.of("from", 92, "to", 115));
    assertThat(FactoryPlan.rangeLabel(JSON.valueToTree(out))).isEqualTo("12~45쪽, 60쪽, 72~83쪽, 92~115쪽");
  }

  @Test
  @DisplayName("given 한 덩어리 글(줄바꿈·쉼표)·객체 배열·비어 있음 when ranges then 같은 규칙으로 읽고 빈 값은 빈 목록")
  void rangesFromTextAndObjects() {
    // given
    var text = JSON.createObjectNode().put("ranges", "12~45\n72~83, 92~115").get("ranges");
    var objects = JSON.createArrayNode();
    objects.addObject().put("from", 3).put("to", 9);

    // when & then
    assertThat(FactoryPlan.ranges(text)).hasSize(3);
    assertThat(FactoryPlan.ranges(objects)).containsExactly(Map.of("from", 3, "to", 9));
    assertThat(FactoryPlan.ranges(null)).isEmpty();
    assertThat(FactoryPlan.rangeLabel(null)).isEmpty();
  }

  @Test
  @DisplayName("given 거꾸로 된 구간·글자·겹침·0쪽·31개 when ranges then 거절하고 무엇이 틀렸는지 알려 준다")
  void rangesRejectBadInput() {
    // given & when & then
    assertThatThrownBy(() -> FactoryPlan.ranges(JSON.createArrayNode().add("45~12"))).hasMessageContaining("시작이 끝보다");
    assertThatThrownBy(() -> FactoryPlan.ranges(JSON.createArrayNode().add("Lesson 7"))).hasMessageContaining("쪽수로");
    assertThatThrownBy(() -> FactoryPlan.ranges(JSON.createArrayNode().add("12~45").add("45~50"))).hasMessageContaining("겹칩니다");
    assertThatThrownBy(() -> FactoryPlan.ranges(JSON.createArrayNode().add("0~3"))).isInstanceOf(IllegalArgumentException.class);
    var many = JSON.createArrayNode();
    for (int i = 1; i <= 31; i++) many.add(String.valueOf(i * 2));
    assertThatThrownBy(() -> FactoryPlan.ranges(many)).hasMessageContaining("30개");
  }

  @Test
  @DisplayName("given track 없음 when section then null(예전 방식 요청), 그 plan은 내신(영어)·학교 시험으로 본다")
  void sectionAbsent() {
    // given
    ObjectNode a = JSON.createObjectNode().put("type", "EXPECTED");

    // when & then
    assertThat(FactoryPlan.section(a)).isNull();
    assertThat(FactoryPlan.track(JSON.createObjectNode())).isEqualTo("NAESIN");
    assertThat(FactoryPlan.isSchool(JSON.createObjectNode())).isTrue();
  }

  @Test
  @DisplayName("given 소문자 구분·겹친 문항 표시 when section then 대문자로 맞추고 한 번씩만 담는다, 모르는 표시는 거절")
  void sectionNormalizes() {
    // given
    ObjectNode a = JSON.createObjectNode().put("track", "sat").put("pastExamRef", " 공개 기출 ");
    a.putArray("itemLabels").add("concept").add("CONCEPT").add("SCOPE_LINKED");

    // when
    Map<String, Object> f = FactoryPlan.section(a);

    // then
    assertThat(f).containsEntry("track", "SAT").containsEntry("pastExamRef", "공개 기출").containsEntry("scopeRef", "")
        .containsEntry("itemLabels", List.of("CONCEPT", "SCOPE_LINKED")).doesNotContainKey("ranges");
    assertThat(FactoryPlan.isSchool(JSON.valueToTree(Map.of("factory", f)))).isFalse();
    assertThat(FactoryPlan.itemLabels(JSON.valueToTree(Map.of("factory", f)))).containsExactly("개념 연습", "시험 범위 연계");
    a.putArray("itemLabels").add("COPY");
    assertThatThrownBy(() -> FactoryPlan.section(a)).hasMessageContaining("itemLabels");
  }
}
