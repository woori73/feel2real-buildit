package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuleBasedEditParser — AI 없이 해석하는 자연어 수정(실제 요소 목록 기준)")
class RuleBasedEditParserTest {

  private static final List<EditorOutline.Item> OUTLINE = List.of(
      new EditorOutline.Item("hero-title", "h1", "text", "우리 학원", null),
      new EditorOutline.Item("hero-sub", "p", "text", "맞춤 수업", null),
      new EditorOutline.Item("hero-cta", "a", "link", "상담 신청하기", null),
      new EditorOutline.Item("about-title", "h2", "text", "학원 소개", null),
      new EditorOutline.Item("about-text", "p", "text", "내신 중심의 체계적인 관리", null));

  private static ProposalChange only(String cmd) {
    var parsed = RuleBasedEditParser.parse(cmd, OUTLINE);
    assertThat(parsed).as(cmd).isPresent();
    assertThat(parsed.get().changes()).hasSize(1);
    return parsed.get().changes().get(0);
  }

  @Test
  @DisplayName("given 제목을 '새 제목'으로 when 해석 then 실제 h1 요소의 text 변경")
  void titleText() {
    ProposalChange c = only("제목을 '수능 영어 1등급'으로 바꿔줘");
    assertThat(c.componentId()).isEqualTo("hero-title");
    assertThat(c.type()).isEqualTo("text");
    assertThat(c.after()).isEqualTo("수능 영어 1등급");
  }

  @Test
  @DisplayName("given 소제목·소개·버튼 키워드 when 해석 then 각각 h2·p·a 첫 요소")
  void keywordsMapToTags() {
    assertThat(only("소제목을 \"우리 강점\"으로 변경").componentId()).isEqualTo("about-title");
    assertThat(only("소개를 '친절한 학원'으로 수정").componentId()).isEqualTo("hero-sub");
    assertThat(only("버튼을 '지금 상담'으로 바꿔줘").componentId()).isEqualTo("hero-cta");
  }

  @Test
  @DisplayName("given '기존'을 '새'로 when 해석 then 기존 문구를 가진 요소")
  void oldToNew() {
    ProposalChange c = only("'체계적인 관리'를 '1:1 맞춤 관리'로 바꿔줘");
    assertThat(c.componentId()).isEqualTo("about-text");
    assertThat(c.after()).isEqualTo("1:1 맞춤 관리");
  }

  @Test
  @DisplayName("given 기존 문구가 여러 요소와 일치하거나 없음 when 해석 then 추측하지 않고 empty")
  void ambiguousOrMissingOld() {
    List<EditorOutline.Item> dup = List.of(
        new EditorOutline.Item("a", "p", "text", "같은 문구", null),
        new EditorOutline.Item("b", "p", "text", "같은 문구", null));
    assertThat(RuleBasedEditParser.parse("'같은 문구'를 '새'로 바꿔줘", dup)).isEmpty();
    assertThat(RuleBasedEditParser.parse("'없는 문구'를 '새'로 바꿔줘", OUTLINE)).isEmpty();
  }

  @Test
  @DisplayName("given 색·크기 when 해석 then style 변경")
  void styles() {
    ProposalChange color = only("제목 색을 빨간색으로 바꿔줘");
    assertThat(color.type()).isEqualTo("style");
    assertThat(color.componentId()).isEqualTo("hero-title");
    assertThat(color.styles()).containsEntry("color", "#dc2626");
    ProposalChange size = only("소개 글자 크기를 20px로 변경");
    assertThat(size.styles()).containsEntry("font-size", "20px");
  }

  @Test
  @DisplayName("given 이해할 수 없는 요청 when 해석 then empty (변경 동사 없음·대상 없음·스타일 값 없음)")
  void unknown() {
    for (String bad : new String[] {null, "", "안녕", "제목", "제목을 예쁘게", "푸터를 '새'로 바꿔줘", "제목 색을 바꿔줘"}) {
      assertThat(RuleBasedEditParser.parse(bad, OUTLINE)).as(String.valueOf(bad)).isEmpty();
    }
    assertThat(RuleBasedEditParser.parse("제목을 '새'로 바꿔줘", List.of())).isEmpty();
  }

  @Test
  @DisplayName("given 결과 when 검증기 then 항상 ProposalChangeValidator를 통과하는 형식")
  void resultsPassValidator() {
    for (String cmd : new String[] {"제목을 '새 제목'으로 바꿔줘", "제목 색을 파란색으로 바꿔줘", "소개 크기를 18px로 수정"}) {
      var changes = RuleBasedEditParser.parse(cmd, OUTLINE).orElseThrow().changes();
      assertThat(ProposalChangeValidator.validate(changes)).hasSize(1);
    }
  }
}
