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
      AcademyAiTaskType task, AcademyAiContext context, String target, String currentValue) {
    return switch (task) {
      case PRINCIPAL_INTRO -> principalIntroPrompt(context, currentValue);
      case PHILOSOPHY_ITEM -> philosophyItemPrompt(context, currentValue);
    };
  }

  private String principalIntroPrompt(AcademyAiContext context, String currentValue) {
    return """
        너는 학원 홈페이지의 원장 소개문을 다듬는 카피라이터다.
        아래 정보를 참고해 원장 소개문을 자연스럽고 신뢰감 있게 다듬어라.

        학원명: %s
        대표 과목: %s
        대상: %s
        원장 이름: %s
        원장 직함: %s
        현재 원장 소개문: %s

        규칙:
        - 결과는 2~4문장, 200자 이내의 한국어 소개문 하나만 출력한다.
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
            blank(currentValue));
  }

  private String philosophyItemPrompt(AcademyAiContext context, String currentValue) {
    String otherItems = context.philosophyItems().stream()
        .filter(item -> item != null && !item.isBlank())
        .collect(Collectors.joining(" / "));
    return """
        너는 학원 홈페이지의 "학습 철학" 항목 문구를 다듬는 카피라이터다.
        아래 정보를 참고해 학습 철학 항목 하나를 자연스럽게 다듬어라.

        학원명: %s
        대표 과목: %s
        기존 학습 철학 항목들(참고용, 중복 금지): %s
        다듬을 항목의 현재 문구: %s

        규칙:
        - 결과는 1문장, 60자 이내의 한국어 문구 하나만 출력한다.
        - 기존 다른 항목들과 의미가 겹치지 않게 한다.
        - 다른 설명, 따옴표, 마크다운 없이 최종 문구만 출력한다.
        - 현재 문구가 비어 있으면 위 정보만으로 새로 작성한다.
        """
        .formatted(
            blank(context.academyName()),
            joinOrNone(context.subjects()),
            otherItems.isBlank() ? "없음" : otherItems,
            blank(currentValue));
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? "(없음)" : value;
  }

  private static String joinOrNone(List<String> values) {
    if (values == null || values.isEmpty()) return "(없음)";
    String joined = values.stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(", "));
    return joined.isBlank() ? "(없음)" : joined;
  }
}
