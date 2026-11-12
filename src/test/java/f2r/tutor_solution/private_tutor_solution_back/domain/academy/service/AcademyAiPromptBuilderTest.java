package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.EducatorReference;
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

  private static EducatorReference reference(long id, String philosophy) {
    return new EducatorReference(
        id, "권소정", "원장", "권소정 영어학원", "강원특별자치도 원주시", "2026-09-14",
        philosophy, "진단 후 단계별 배치", "첨삭과 개별 관리", List.of("anti_roterism"),
        "https://feel2real.co.kr/chart/educators/" + id);
  }

  @Test
  @DisplayName("given 참고 사례 없음 when 6-arg build then 5-arg 프롬프트와 바이트 단위로 동일(회귀 방지)")
  void references_empty_promptIdenticalToLegacy() {
    for (AcademyAiTaskType task : List.of(AcademyAiTaskType.GUIDE_TEXT, AcademyAiTaskType.ACADEMY_COMPOSE)) {
      String legacy = builder.build(task, withComposition(), "principalDesc", "현재값", "요구");
      String withEmpty = builder.build(task, withComposition(), "principalDesc", "현재값", "요구", List.of());
      String withNull = builder.build(task, withComposition(), "principalDesc", "현재값", "요구", null);
      assertThat(withEmpty).isEqualTo(legacy);
      assertThat(withNull).isEqualTo(legacy);
    }
  }

  @Test
  @DisplayName("given GUIDE_TEXT + 참고 사례 when 프롬프트 생성 then [참고 사례] 블록과 인용 금지 규칙이 붙는다")
  void references_guideText_appendsBlockWithProhibitions() {
    String prompt = builder.build(
        AcademyAiTaskType.GUIDE_TEXT, context, "philosophyItem1", "", "강조: 암기 반대",
        List.of(reference(75L, "정답 암기보다 논리적 문제 해결")));

    assertThat(prompt).contains("[참고 사례");
    assertThat(prompt).contains("id 75");
    assertThat(prompt).contains("2026-09-14");
    assertThat(prompt).contains("정답 암기보다 논리적 문제 해결");
    // 다른 교육자의 이름·수치·실적·프로그램명을 출력에 쓰지 말라는 이중 방어.
    assertThat(prompt).contains("포함하지 않는다").contains("의뢰 학원장이 제공");
    // 사례 라인에는 원장/학원 이름을 넣지 않는다(유출 유인 최소화).
    assertThat(prompt).doesNotContain("권소정");
  }

  @Test
  @DisplayName("given ACADEMY_COMPOSE + 참고 사례 when 프롬프트 생성 then 진단 프롬프트에도 사례가 주입된다")
  void references_academyCompose_appendsBlock() {
    String prompt = builder.build(
        AcademyAiTaskType.ACADEMY_COMPOSE, withComposition(), null, null, "요구",
        List.of(reference(75L, "철학 원문")));
    assertThat(prompt).contains("[참고 사례").contains("id 75").contains("철학 원문");
  }

  @Test
  @DisplayName("given PRINCIPAL_INTRO/PHILOSOPHY_ITEM when 참고 사례와 함께 build then 단일 target Task는 사례를 받지 않는다")
  void references_singleTargetTasks_ignoreBlock() {
    List<EducatorReference> refs = List.of(reference(75L, "철학 원문"));
    for (AcademyAiTaskType task : List.of(AcademyAiTaskType.PRINCIPAL_INTRO, AcademyAiTaskType.PHILOSOPHY_ITEM)) {
      assertThat(builder.build(task, context, "x", "현재값", "요구", refs))
          .isEqualTo(builder.build(task, context, "x", "현재값", "요구"));
    }
  }

  @Test
  @DisplayName("given 60자 초과 사례 원문 when 블록 렌더 then 60자로 잘라 인용 분량을 제한한다")
  void references_excerptTruncated() {
    String longText = "가".repeat(100);
    String prompt = builder.build(
        AcademyAiTaskType.GUIDE_TEXT, context, "philosophyItem1", "", "x",
        List.of(reference(75L, longText)));
    assertThat(prompt).doesNotContain("가".repeat(61));
    assertThat(prompt).contains("가".repeat(60) + "…");
  }

  @Test
  @DisplayName("given 카드 원문이 인물명·학원명을 포함 when 블록 렌더 then 마스킹되어 이름이 프롬프트에 남지 않는다")
  void references_namesMasked() {
    EducatorReference named = new EducatorReference(
        66L, "문나경", "원장", "기지시GNB영어수학학원", "충청남도 당진시", "2026-09-14",
        "문나경 원장은 당진 학생도 대도시 학생과 경쟁할 영어 실력을 갖춰야 한다고 본다.",
        "기지시GNB영어수학학원은 입학 진단 후 누적 반복 과정을 운영한다.",
        "원장 직강", List.of("habit_structure"), "https://feel2real.co.kr/chart/educators/66");

    String prompt = builder.build(
        AcademyAiTaskType.GUIDE_TEXT, context, "principalDesc", "", "강조", List.of(named));

    assertThat(prompt).doesNotContain("문나경").doesNotContain("기지시GNB영어수학학원");
    assertThat(prompt).contains("해당 교육자").contains("다른 학원");
  }

  private AcademyAiContext withComposition() {
    return new AcademyAiContext(
        "우리학원", "소개", List.of("영어"), List.of("MIDDLE"),
        "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of(), composition());
  }
}
