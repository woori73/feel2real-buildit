package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssistantRuleInterpreter — 규칙 기반 수정 의도 해석(폴백·기준선)")
class AssistantRuleInterpreterTest {

  private static List<Op> ops(String text) {
    return AssistantRuleInterpreter.interpret(text, Map.of()).ops();
  }

  private static Predicate<List<Op>> has(String op, String keyOrId, String value) {
    return list -> list.stream().anyMatch(o -> o.op().equals(op)
        && (keyOrId == null || keyOrId.equals(o.key()) || keyOrId.equals(o.id()))
        && (value == null || value.equals(o.value())));
  }

  /** 발화 → 기대 연산. 정확도를 기준선으로 센다. */
  private record Case(String utterance, Predicate<List<Op>> expect) {}

  private static final List<Case> CORPUS = List.of(
      new Case("더 고급스럽게 바꿔 줘", has("setPreset", null, "DARK").and(has("setTypography", null, "PREMIUM"))),
      new Case("밝고 친근한 분위기로 바꿔주세요", has("setPreset", null, "ENGLISH").and(has("setTypography", null, "FRIENDLY"))),
      new Case("깔끔하게 정리해줘", has("setPreset", null, "MINIMAL")),
      new Case("좀 더 따뜻한 느낌으로", has("setPreset", null, "WARM")),
      new Case("활기차고 젊은 느낌이면 좋겠어", has("setPreset", null, "DYNAMIC")),
      new Case("신뢰감 있는 입시 학원 스타일로", has("setPreset", null, "EXAM")),
      new Case("다크 모드로 바꿔줘", has("setPreset", null, "DARK")),
      new Case("주색을 파란색으로", has("setColor", "colorPrimary", "#2563eb")),
      new Case("버튼 색을 초록색으로 바꿔줘", has("setColor", "colorPrimary", "#16a34a")),
      new Case("상단 배경색을 남색으로", has("setColor", "colorHeroBg", "#1e3a8a")),
      new Case("수업 영상 섹션 숨겨 줘", has("hideSection", "sec-videos", null)),
      new Case("원장 소개 빼줘", has("hideSection", "sec-principal", null)),
      new Case("상담 신청을 맨 위로 올려줘", has("moveSection", "sec-cta", "first")),
      new Case("커리큘럼 섹션 보여줘", has("showSection", "sec-lessons", null)),
      new Case("블로그 섹션은 아래로 내려줘", has("moveSection", "sec-social", "last")),
      new Case("제목 글꼴을 명조로 해줘", has("setFont", "headingFont", "NOTO_SERIF_KR")),
      new Case("가운데 정렬로 바꿔줘", has("setHeroLayout", null, "center")),
      new Case("미니 강의 섹션 추가해줘", has("showSection", "sec-minilectures", null)),
      new Case("더 고급스럽게 하고 수업 영상은 숨겨 줘", has("setPreset", null, "DARK").and(has("hideSection", "sec-videos", null))),
      new Case("미니멀한 느낌으로 하고 상담 신청 섹션을 맨 위로", has("setPreset", null, "MINIMAL").and(has("moveSection", "sec-cta", "first"))));

  @Test
  @DisplayName("given 대표 발화 코퍼스 when 해석하면 then 기대한 연산이 나온다(기준선 정확도 100%)")
  void corpusAccuracy() {
    int ok = 0;
    StringBuilder misses = new StringBuilder();
    for (Case c : CORPUS) {
      List<Op> o = ops(c.utterance());
      if (c.expect().test(o)) ok++;
      else misses.append("\n - ").append(c.utterance()).append(" → ").append(o);
    }
    assertThat(ok).as("코퍼스 %d건 중 실패:%s", CORPUS.size(), misses).isEqualTo(CORPUS.size());
  }

  @Test
  @DisplayName("given 색을 직접 지정한 요청 when 해석하면 then 프리셋은 바꾸지 않는다(요청 범위 준수)")
  void explicitColorDoesNotChangePreset() {
    assertThat(ops("주색을 보라색으로")).noneMatch(o -> o.op().equals("setPreset"));
  }

  @Test
  @DisplayName("given 이해할 수 없는 요청·빈 요청 when 해석하면 then 연산 없이 되묻는다")
  void asksWhenUnclear() {
    for (String u : new String[] {"", "   ", "음 잘 모르겠어", "맛있는 피자 레시피 알려줘"}) {
      AssistantRuleInterpreter.Result r = AssistantRuleInterpreter.interpret(u, Map.of());
      assertThat(r.ops()).as(u).isEmpty();
      assertThat(r.question()).as(u).isNotBlank();
    }
  }

  @Test
  @DisplayName("given 문구를 새로 써 달라는 요청 when 규칙이 해석하면 then 문구를 지어내지 않는다(AI 전용)")
  void rulesNeverWriteCopy() {
    assertThat(ops("소개 문구를 새로 써줘")).noneMatch(o -> o.op().equals("setText") || o.op().equals("setIntro"));
  }
}
