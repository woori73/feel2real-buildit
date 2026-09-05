package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Task + Context + 현재 값으로 provider-agnostic 프롬프트 문자열을 만든다. provider를 몰라도
 * 되게 순수 문자열 조립만 한다({@code AiProvider}를 참조하지 않는다).
 *
 * <p>모든 프롬프트는 "다른 설명 없이 최종 문구만 출력하라"는 제약을 명시한다 — provider가
 * 마크다운/따옴표/부연설명을 덧붙이는 경우가 있어, 실제 정제(코드펜스 제거 등)는
 * {@link AcademyAiSuggestionService}가 한 번 더 한다(방어적 이중화).
 */
@Component
public class AcademyAiPromptBuilder {

  public String build(
      AcademyAiTaskType task, AcademyAiContext context, String target, String currentValue,
      String instructions) {
    return switch (task) {
      case PRINCIPAL_INTRO -> principalIntroPrompt(context, currentValue, instructions);
      case PHILOSOPHY_ITEM -> philosophyItemPrompt(context, currentValue, instructions);
    };
  }

  private String principalIntroPrompt(AcademyAiContext context, String currentValue, String instructions) {
    return """
        너는 학원 홈페이지의 원장 소개문을 다듬는 카피라이터다.
        이 문구는 원장 소개 섹션에서 여러 문장이 이어지는 문단 자리에 그대로 노출된다(카드가
        아니라 짧은 자기소개 문단이다).
        아래 정보를 참고해 원장 소개문을 자연스럽고 신뢰감 있게 다듬어라.

        학원명: %s
        대표 과목: %s
        대상: %s
        원장 이름: %s
        원장 직함: %s
        현재 원장 소개문: %s
        사용자 요구사항(있으면 최우선 반영하되, 아래 길이 규칙은 절대 넘지 않는다): %s

        규칙:
        - 결과는 2~4문장, 200자 이내의 한국어 소개문 하나만 출력한다.
        - 사용자 요구사항이 이 길이 규칙과 충돌하면(예: "더 길게") 길이 규칙을 우선한다.
        - 과장되거나 검증할 수 없는 수치(연간 합격률 등)를 새로 지어내지 않는다.
        - 다른 설명, 따옴표, 마크다운 없이 최종 문구만 출력한다.
        - 현재 소개문이 비어 있으면 위 정보만으로 새로 작성한다.
        """
        .formatted(
            blank(context.academyName()),
            joinOrNone(context.subjects()),
            joinOrNone(context.targetGroups()),
            blank(context.principalName()),
            blank(context.principalRole()),
            blank(currentValue),
            blankInstructions(instructions));
  }

  private String philosophyItemPrompt(AcademyAiContext context, String currentValue, String instructions) {
    String otherItems = context.philosophyItems().stream()
        .filter(item -> item != null && !item.isBlank())
        .collect(Collectors.joining(" / "));
    return """
        너는 학원 홈페이지의 "학습 철학" 카드 문구를 다듬는 카피라이터다.
        이 문구는 4개가 나란히 놓이는 짧은 카드 하나의 제목처럼 쓰인다 — 문단이 아니라 한 줄
        슬로건이다. 길게 쓰면 카드 4개의 높이가 서로 어긋나 레이아웃이 깨진다.
        아래 정보를 참고해 학습 철학 항목 하나를 자연스럽게 다듬어라.

        학원명: %s
        대표 과목: %s
        기존 학습 철학 항목들(참고용, 중복 금지): %s
        다듬을 항목의 현재 문구: %s
        사용자 요구사항(있으면 최우선 반영하되, 아래 길이 규칙은 절대 넘지 않는다): %s

        규칙:
        - 결과는 1문장, 60자 이내의 한국어 문구 하나만 출력한다(카드 제목 길이 — 문단 금지).
        - 사용자 요구사항이 이 길이 규칙과 충돌하면(예: "더 자세히") 길이 규칙을 우선한다.
        - 기존 다른 항목들과 의미가 겹치지 않게 한다.
        - 다른 설명, 따옴표, 마크다운 없이 최종 문구만 출력한다.
        - 현재 문구가 비어 있으면 위 정보만으로 새로 작성한다.
        """
        .formatted(
            blank(context.academyName()),
            joinOrNone(context.subjects()),
            otherItems.isBlank() ? "없음" : otherItems,
            blank(currentValue),
            blankInstructions(instructions));
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? "(없음)" : value;
  }

  private static String blankInstructions(String instructions) {
    return instructions == null || instructions.isBlank() ? "(없음)" : instructions.trim();
  }

  private static String joinOrNone(List<String> values) {
    if (values == null || values.isEmpty()) return "(없음)";
    String joined = values.stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(", "));
    return joined.isBlank() ? "(없음)" : joined;
  }
}
