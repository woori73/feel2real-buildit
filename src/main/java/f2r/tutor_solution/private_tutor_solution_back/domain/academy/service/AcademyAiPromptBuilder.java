package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.Composition;
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
      case ACADEMY_COMPOSE -> academyComposePrompt(context, instructions);
    };
  }

  /**
   * Phase 5A: Academy 전체 진단 + 안전한 개선안 묶음을 <b>JSON 하나</b>로만 요청한다.
   * allowlist(문구 개선 target · 섹션 순서)를 프롬프트에 명시하고, 그 밖의 변경(새 영상/자료
   * 생성, 가격/연락처/원장 이름 변경, 미구현 섹션 콘텐츠 작성)은 절대 제안하지 말라고 못박는다.
   * 실제 방어는 {@code AcademyAiProposalService}가 응답 JSON을 파싱·검증하며 한 번 더 한다.
   */
  private String academyComposePrompt(AcademyAiContext context, String instructions) {
    Composition composition = context.composition();
    String sectionLines = composition == null || composition.sections() == null
        ? "(정보 없음)"
        : composition.sections().stream()
            .map(s -> "- " + s.key() + " (" + s.label() + "): "
                + (s.visible() ? "노출" : "숨김")
                + ", " + (s.hasContent() ? "콘텐츠 있음" : "콘텐츠 비어 있음")
                + (s.editable() ? "" : ", [AI 변경 금지 섹션]"))
            .collect(Collectors.joining("\n"));
    String editableTargets = composition == null || composition.editableTextTargets() == null
        ? "(없음)"
        : String.join(", ", composition.editableTextTargets());
    String currentOrder = composition == null || composition.sections() == null
        ? "(정보 없음)"
        : composition.sections().stream()
            .map(AcademyAiContext.Composition.SectionState::key)
            .collect(Collectors.joining(", "));
    String missing = composition == null || composition.completionMissing() == null
            || composition.completionMissing().isEmpty()
        ? "(없음)"
        : String.join(", ", composition.completionMissing());

    return """
        너는 학원 홈페이지를 통째로 진단하고 안전한 개선안을 제안하는 컨설턴트다.
        아래 학원의 현재 상태를 보고 (1) 전체 진단 2~4문장과 (2) 개별 개선 proposal 목록을 만든다.

        [학원 기본 정보]
        학원명: %s
        학원 소개: %s
        대표 과목: %s
        대상: %s
        원장 이름: %s / 직함: %s
        원장 소개문: %s
        학습 철학 항목: %s

        [콘텐츠 현황]
        대표 원장 영상: %s
        수업 영상 개수: %d
        미니 강의 개수: %d
        대표 수업(Program) 카드: %d개 (이미지/영상 있는 카드 %d개)
        Featured CTA 문구: %s
        완성도 점수: %d%% / 아직 비어 있는 항목: %s

        [현재 섹션 구성/순서 — 위에서 아래로 노출됨]
        %s

        [사용자 요구사항 — 있으면 진단·우선순위에 최우선 반영]
        %s

        너가 제안할 수 있는 것은 딱 두 종류뿐이다:
        1) TEXT  — 아래 target 중 하나의 문구를 더 자연스럽게 다듬기.
           허용 target: %s
           그 밖의 target(학원명, 원장 이름/직함, 가격, 연락처, 학원 소개 본문 등)은 절대 제안하지 않는다.
        2) SECTION_ORDER — 기존 섹션의 순서만 재배열. 섹션을 새로 만들거나 지우지 않는다.
           sec-hero는 항상 맨 앞. [AI 변경 금지 섹션] 표시가 있는 섹션은 이동 대상으로 삼지 않는다.
           현재 순서: %s

        금지: 새 영상/자료/수업 생성, 데이터 삭제, 가격·연락처·원장 이름 변경,
        지금 콘텐츠가 없는 미구현 섹션에 내용 채우기, 현재 Builder가 지원하지 않는 기능 제안.

        출력은 아래 스키마의 JSON 하나만. 마크다운/설명/코드펜스 없이 순수 JSON만 출력한다.
        {
          "diagnosis": "전체 진단 2~4문장 (한국어)",
          "proposals": [
            {
              "id": "p1",
              "type": "TEXT" | "SECTION_ORDER",
              "title": "짧은 제목 (예: 원장 소개, 섹션 순서)",
              "current": "현재 상태 요약 한 문장",
              "suggestion": "무엇을 어떻게 바꾸는지 한 문장",
              "target": "TEXT면 위 허용 target 중 하나, SECTION_ORDER면 null",
              "suggestedValue": "TEXT면 새 문구 문자열, SECTION_ORDER면 sec-* 키 전체 배열",
              "safeToApply": true
            }
          ]
        }
        proposal은 최대 6개. 정말 개선이 필요한 것만. 바꿀 게 없으면 proposals는 빈 배열로 둔다.
        TEXT proposal의 suggestedValue는 원장 소개문이면 200자, 그 외 문구는 60자 이내로 짧게.
        """
        .formatted(
            blank(context.academyName()),
            blank(context.intro()),
            joinOrNone(context.subjects()),
            joinOrNone(context.targetGroups()),
            blank(context.principalName()),
            blank(context.principalRole()),
            blank(context.principalDesc()),
            joinOrNone(context.philosophyItems()),
            composition != null && composition.hasDirectorVideo() ? "있음" : "없음",
            composition == null ? 0 : composition.lessonVideoCount(),
            composition == null ? 0 : composition.miniLectureCount(),
            composition == null ? 0 : composition.programCardCount(),
            composition == null ? 0 : composition.programCardWithMediaCount(),
            composition == null || composition.ctaLabel() == null || composition.ctaLabel().isBlank()
                ? "(없음)" : composition.ctaLabel(),
            composition == null ? 0 : composition.completionScorePercent(),
            missing,
            sectionLines,
            blankInstructions(instructions),
            editableTargets,
            currentOrder);
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
