package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AcademyAiPromptBuilder — 필드별 목적/길이 규칙 + 사용자 요구사항이 실제 프롬프트 문자열에
 * 반영되는지 확인한다. AcademyAiSuggestionServiceTest는 promptBuilder를 mock으로 대체하기
 * 때문에 실제 프롬프트 "내용"은 여기서만 검증된다.
 */
@DisplayName("AcademyAiPromptBuilder — 필드별 길이/목적 규칙 + 사용자 요구사항 반영")
class AcademyAiPromptBuilderTest {

  private final AcademyAiPromptBuilder builder = new AcademyAiPromptBuilder();
  private final AcademyAiContext context = new AcademyAiContext(
      "우리학원", "소개", List.of("영어"), List.of("MIDDLE"),
      "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of());

  @Test
  @DisplayName("given PRINCIPAL_INTRO when 요구사항 없음 then '(없음)'으로 표시하고 문단형 길이 규칙(200자)을 명시한다")
  void principalIntro_withoutInstructions() {
    String prompt = builder.build(AcademyAiTaskType.PRINCIPAL_INTRO, context, "principalDesc", "현재값", null);
    assertThat(prompt).contains("사용자 요구사항").contains("(없음)");
    assertThat(prompt).contains("200자 이내");
    assertThat(prompt).contains("문단");
  }

  @Test
  @DisplayName("given PRINCIPAL_INTRO when 요구사항 있음 then 프롬프트에 그대로 포함된다")
  void principalIntro_withInstructions() {
    String prompt = builder.build(
        AcademyAiTaskType.PRINCIPAL_INTRO, context, "principalDesc", "현재값", "학부모에게 신뢰감 있게");
    assertThat(prompt).contains("학부모에게 신뢰감 있게");
  }

  @Test
  @DisplayName("given PHILOSOPHY_ITEM when 요구사항 없음 then '(없음)'으로 표시하고 카드형 짧은 길이 규칙(60자)을 명시한다")
  void philosophyItem_withoutInstructions() {
    String prompt = builder.build(AcademyAiTaskType.PHILOSOPHY_ITEM, context, "philosophyItem2", "현재값", "   ");
    assertThat(prompt).contains("사용자 요구사항").contains("(없음)");
    assertThat(prompt).contains("60자 이내");
    assertThat(prompt).contains("카드");
    // 문단이 아니라 카드 제목/슬로건이라는 점을 명시해 장문 생성을 막는다.
    assertThat(prompt).contains("문단 금지");
  }

  @Test
  @DisplayName("given PHILOSOPHY_ITEM when 요구사항 있음 then 프롬프트에 그대로 포함된다")
  void philosophyItem_withInstructions() {
    String prompt = builder.build(
        AcademyAiTaskType.PHILOSOPHY_ITEM, context, "philosophyItem1", "현재값", "1:1 맞춤수업 강조");
    assertThat(prompt).contains("1:1 맞춤수업 강조");
  }

  @Test
  @DisplayName("given 두 Task when 프롬프트를 비교하면 then 길이 목표가 서로 다르다(같은 방식으로 생성하지 않는다)")
  void twoTasks_haveDifferentLengthTargets() {
    String principalPrompt = builder.build(AcademyAiTaskType.PRINCIPAL_INTRO, context, "principalDesc", "x", null);
    String philosophyPrompt = builder.build(AcademyAiTaskType.PHILOSOPHY_ITEM, context, "philosophyItem1", "x", null);

    assertThat(principalPrompt).doesNotContain("60자 이내");
    assertThat(philosophyPrompt).doesNotContain("200자 이내");
  }
}
