package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProposalVerifier — 실제 GrapesJS projectData 기준, fail-closed")
class ProposalVerifierTest {

  private static ProposalChange text(String id, String after) {
    return new ProposalChange(id, "text", after, null, null);
  }

  private static ProposalChange style(String id, Map<String, String> s, String media) {
    return new ProposalChange(id, "style", null, s, media);
  }

  @Test
  @DisplayName("given 텍스트가 반영된 projectData when 검증 then 통과")
  void text_matches() {
    ObjectNode pd = ProposalFixtures.projectData();
    ProposalFixtures.setTitleText(pd, "새 제목");

    assertThat(ProposalVerifier.verify(ProposalFixtures.json(pd),
        List.of(text(ProposalFixtures.TITLE_ID, "새 제목")))).isEmpty();
  }

  @Test
  @DisplayName("given 공백만 다른 텍스트 when 검증 then 공백 정규화 후 통과")
  void text_whitespaceNormalized() {
    ObjectNode pd = ProposalFixtures.projectData();
    ProposalFixtures.setTitleText(pd, "새   제목\n");

    assertThat(ProposalVerifier.verify(ProposalFixtures.json(pd),
        List.of(text(ProposalFixtures.TITLE_ID, "새 제목")))).isEmpty();
  }

  @Test
  @DisplayName("given 텍스트가 다름 when 검증 then TEXT_MISMATCH")
  void text_mismatch() {
    String pd = ProposalFixtures.json(ProposalFixtures.projectData());

    assertThat(ProposalVerifier.verify(pd, List.of(text(ProposalFixtures.TITLE_ID, "다른 제목"))))
        .containsExactly("TEXT_MISMATCH:" + ProposalFixtures.TITLE_ID);
  }

  @Test
  @DisplayName("given 중첩 인라인 요소가 있는 텍스트 when 검증 then 모든 하위 textnode를 이어 비교한다")
  void text_nestedChildren() {
    ObjectNode pd = ProposalFixtures.projectData();
    ObjectNode h1 = ProposalFixtures.component(pd, ProposalFixtures.TITLE_ID);
    h1.putArray("components");
    ((com.fasterxml.jackson.databind.node.ArrayNode) h1.get("components"))
        .addObject().put("type", "textnode").put("content", "새 ");
    ((com.fasterxml.jackson.databind.node.ArrayNode) h1.get("components"))
        .addObject().put("tagName", "b").putArray("components")
        .addObject().put("type", "textnode").put("content", "제목");

    assertThat(ProposalVerifier.verify(ProposalFixtures.json(pd),
        List.of(text(ProposalFixtures.TITLE_ID, "새 제목")))).isEmpty();
  }

  @Test
  @DisplayName("given 이미지 src when 검증 then 일치 통과, 다르면 IMAGE_MISMATCH")
  void image() {
    ObjectNode pd = ProposalFixtures.projectData();
    ProposalFixtures.component(pd, ProposalFixtures.IMAGE_ID).with("attributes")
        .put("src", "https://example.com/a.png");
    String json = ProposalFixtures.json(pd);

    assertThat(ProposalVerifier.verify(json, List.of(new ProposalChange(
        ProposalFixtures.IMAGE_ID, "image", "https://example.com/a.png", null, null)))).isEmpty();
    assertThat(ProposalVerifier.verify(json, List.of(new ProposalChange(
        ProposalFixtures.IMAGE_ID, "image", "https://example.com/b.png", null, null))))
        .containsExactly("IMAGE_MISMATCH:" + ProposalFixtures.IMAGE_ID);
  }

  @Test
  @DisplayName("given 기본·모바일 스타일 규칙 when 검증 then 일치 통과, 값/미디어 다르면 STYLE_MISMATCH")
  void style_baseAndMobile() {
    ObjectNode pd = ProposalFixtures.projectData();
    ProposalFixtures.setStyle(pd, ProposalFixtures.TITLE_ID, null, "color", "red");
    ProposalFixtures.setStyle(pd, ProposalFixtures.TITLE_ID, ProposalChange.MOBILE_MEDIA, "font-size", "20px");
    String json = ProposalFixtures.json(pd);
    String id = ProposalFixtures.TITLE_ID;

    assertThat(ProposalVerifier.verify(json, List.of(style(id, Map.of("color", "red"), null)))).isEmpty();
    assertThat(ProposalVerifier.verify(json,
        List.of(style(id, Map.of("font-size", "20px"), ProposalChange.MOBILE_MEDIA)))).isEmpty();
    assertThat(ProposalVerifier.verify(json, List.of(style(id, Map.of("color", "blue"), null))))
        .containsExactly("STYLE_MISMATCH:" + id);
    // 모바일 값을 기본 화면 규칙으로 착각하지 않는다.
    assertThat(ProposalVerifier.verify(json, List.of(style(id, Map.of("font-size", "20px"), null))))
        .containsExactly("STYLE_MISMATCH:" + id);
  }

  @Test
  @DisplayName("given selectorsAdd 형식 규칙 when 검증 then 인식한다")
  void style_selectorsAdd() {
    ObjectNode pd = ProposalFixtures.projectData();
    ObjectNode rule = ProposalFixtures.M.createObjectNode();
    rule.putArray("selectors");
    rule.put("selectorsAdd", "#" + ProposalFixtures.PARA_ID);
    rule.putObject("style").put("color", "rgb(1, 2, 3)");
    ((com.fasterxml.jackson.databind.node.ArrayNode) pd.get("styles")).add(rule);

    assertThat(ProposalVerifier.verify(ProposalFixtures.json(pd),
        List.of(style(ProposalFixtures.PARA_ID, Map.of("color", "rgb(1, 2, 3)"), null)))).isEmpty();
  }

  @Test
  @DisplayName("given 대상 없음/중복 when 검증 then TARGET_NOT_FOUND / TARGET_AMBIGUOUS")
  void targets() {
    ObjectNode pd = ProposalFixtures.projectData();
    String json = ProposalFixtures.json(pd);
    assertThat(ProposalVerifier.verify(json, List.of(text("ghost", "x"))))
        .containsExactly("TARGET_NOT_FOUND:ghost");

    // 같은 id를 가진 컴포넌트를 하나 더 만든다.
    ObjectNode dup = ProposalFixtures.component(pd, ProposalFixtures.PARA_ID).deepCopy();
    ((com.fasterxml.jackson.databind.node.ArrayNode)
        ProposalFixtures.component(pd, "ikua").get("components")).add(dup);
    assertThat(ProposalVerifier.verify(ProposalFixtures.json(pd),
        List.of(text(ProposalFixtures.PARA_ID, "x"))))
        .containsExactly("TARGET_AMBIGUOUS:" + ProposalFixtures.PARA_ID);
  }

  @Test
  @DisplayName("given 비교 대상이 없거나 깨진 projectData when 검증 then 항상 PROJECT_DATA_MISSING (성공 기본값 없음)")
  void missingProjectData_failsClosed() {
    List<ProposalChange> c = List.of(text("a", "b"));
    for (String bad : new String[] {null, "", "   ", "{}", "[]", "not json", "{\"pages\":[]}",
        "{\"pages\":[{\"frames\":[]}]}", "{\"pages\":[{\"frames\":[{}]}]}"}) {
      assertThat(ProposalVerifier.verify(bad, c)).as("입력: %s", bad)
          .containsExactly("PROJECT_DATA_MISSING");
    }
  }

  @Test
  @DisplayName("given 변경 없음/알 수 없는 type when 검증 then 실패")
  void emptyAndUnknown() {
    String json = ProposalFixtures.json(ProposalFixtures.projectData());
    assertThat(ProposalVerifier.verify(json, List.of())).containsExactly("NO_CHANGES");
    assertThat(ProposalVerifier.verify(json, null)).containsExactly("NO_CHANGES");
    assertThat(ProposalVerifier.verify(json, List.of(new ProposalChange(
        ProposalFixtures.TITLE_ID, "script", "x", null, null))))
        .containsExactly("UNKNOWN_CHANGE_TYPE:" + ProposalFixtures.TITLE_ID);
  }

  @Test
  @DisplayName("given 일부만 반영 when 검증 then 반영 안 된 항목만 실패로 모은다")
  void partial() {
    ObjectNode pd = ProposalFixtures.projectData();
    ProposalFixtures.setTitleText(pd, "새 제목");

    assertThat(ProposalVerifier.verify(ProposalFixtures.json(pd), List.of(
        text(ProposalFixtures.TITLE_ID, "새 제목"), text(ProposalFixtures.PARA_ID, "안 바뀜"))))
        .containsExactly("TEXT_MISMATCH:" + ProposalFixtures.PARA_ID);
  }

  @Test
  @DisplayName("given 값 검증은 실패하지만 대상은 있음 when verifyTargetsOnly then 통과(STALE 판정용)")
  void targetsOnly() {
    String json = ProposalFixtures.json(ProposalFixtures.projectData());
    assertThat(ProposalVerifier.verifyTargetsOnly(json,
        List.of(text(ProposalFixtures.TITLE_ID, "아직 미반영")))).isEmpty();
    assertThat(ProposalVerifier.verifyTargetsOnly(json, List.of(text("ghost", "x"))))
        .containsExactly("TARGET_NOT_FOUND:ghost");
  }
}
