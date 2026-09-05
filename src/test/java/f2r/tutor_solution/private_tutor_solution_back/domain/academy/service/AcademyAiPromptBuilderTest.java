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
      "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of(), null);

  private static AcademyAiContext.Composition composition() {
    return new AcademyAiContext.Composition(
        List.of(
            new AcademyAiContext.Composition.SectionState("sec-hero", "히어로", true, true, true),
            new AcademyAiContext.Composition.SectionState("sec-principal", "원장 소개", true, true, false),
            new AcademyAiContext.Composition.SectionState("sec-reviews", "수강 후기", true, false, false)),
        false, 0, 2, 3, 1, "상담 신청하기",
        List.of("principalDesc", "philosophyItem1"), 40, List.of("원장 소개", "수업 영상"));
  }

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

  @Test
  @DisplayName("given ACADEMY_COMPOSE when 프롬프트 생성 then JSON 스키마·allowlist·섹션 목록·금지 규칙이 포함된다")
  void academyCompose_prompt_contains_schema_and_guardrails() {
    AcademyAiContext composeContext = new AcademyAiContext(
        "우리학원", "소개", List.of("영어"), List.of("MIDDLE"),
        "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of(), composition());

    String prompt = builder.build(
        AcademyAiTaskType.ACADEMY_COMPOSE, composeContext, null, null, "수업 영상을 중심으로 보여줘");

    // 사용자 요구사항이 그대로 실린다.
    assertThat(prompt).contains("수업 영상을 중심으로 보여줘");
    // 구조화 출력(JSON 스키마)을 요구한다.
    assertThat(prompt).contains("\"diagnosis\"").contains("\"proposals\"");
    assertThat(prompt).contains("TEXT").contains("SECTION_ORDER");
    // allowlist target이 프롬프트에 노출된다.
    assertThat(prompt).contains("principalDesc");
    // 현재 섹션 구성/순서가 들어간다 + 미구현 섹션은 변경 금지로 표시된다.
    assertThat(prompt).contains("sec-hero").contains("sec-reviews");
    assertThat(prompt).contains("[AI 변경 금지 섹션]");
    // 금지 규칙(새 영상 생성/가격·연락처 변경)을 못박는다.
    assertThat(prompt).contains("금지");
  }

  @Test
  @DisplayName("given ACADEMY_COMPOSE + composition이 null when 프롬프트 생성 then NPE 없이 안전하게 만든다")
  void academyCompose_handles_null_composition() {
    String prompt = builder.build(AcademyAiTaskType.ACADEMY_COMPOSE, context, null, null, null);
    assertThat(prompt).contains("\"proposals\"");
  }

  @Test
  @DisplayName("given GUIDE_TEXT 카드형 target when 프롬프트 생성 then 후보 배열·짧은 길이 규칙·hallucination 금지가 포함된다")
  void guideText_shortTarget_prompt() {
    String prompt = builder.build(
        AcademyAiTaskType.GUIDE_TEXT, context, "philosophyItem1", "", "이 영역에서 강조하려는 것: 학교별 내신 분석");

    assertThat(prompt).contains("JSON 배열");
    assertThat(prompt).contains("45자 이내");
    assertThat(prompt).contains("학교별 내신 분석");
    // 사용자가 말하지 않은 실적/수치/경력을 지어내지 말라는 제약을 못박는다.
    assertThat(prompt).contains("지어내지").contains("합격 실적");
  }

  @Test
  @DisplayName("given GUIDE_TEXT 문단형 target(principalDesc/intro) when 프롬프트 생성 then 카드형보다 긴 길이 규칙을 쓴다")
  void guideText_longTarget_prompt() {
    String shortPrompt = builder.build(AcademyAiTaskType.GUIDE_TEXT, context, "philosophyItem1", "", "x");
    String longPrompt = builder.build(AcademyAiTaskType.GUIDE_TEXT, context, "principalDesc", "", "x");
    String heroPrompt = builder.build(AcademyAiTaskType.GUIDE_TEXT, context, "intro", "", "x");

    assertThat(shortPrompt).contains("45자 이내");
    assertThat(longPrompt).contains("180자 이내").doesNotContain("45자 이내");
    assertThat(heroPrompt).contains("180자 이내");
  }
}
