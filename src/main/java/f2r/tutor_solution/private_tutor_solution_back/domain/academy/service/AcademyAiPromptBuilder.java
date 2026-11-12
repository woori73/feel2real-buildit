package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.Composition;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.EducatorReference;
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
    return build(task, context, target, currentValue, instructions, List.of());
  }

  /**
   * Chart 교육자 참고 사례({@link EducatorReference})가 있을 때만 쓰는 오버로드.
   *
   * <p>참고 사례는 GUIDE_TEXT·ACADEMY_COMPOSE 두 Task에만 주입되고, 목록이 비면 5-arg
   * {@link #build}와 <b>바이트 단위로 동일한 프롬프트</b>를 만든다(기존 기능 회귀 방지).
   */
  public String build(
      AcademyAiTaskType task, AcademyAiContext context, String target, String currentValue,
      String instructions, List<EducatorReference> references) {
    List<EducatorReference> refs = references == null ? List.of() : references;
    return switch (task) {
      case PRINCIPAL_INTRO -> principalIntroPrompt(context, currentValue, instructions);
      case PHILOSOPHY_ITEM -> philosophyItemPrompt(context, currentValue, instructions);
      case ACADEMY_COMPOSE -> academyComposePrompt(context, instructions, refs);
      case GUIDE_TEXT -> guideTextPrompt(context, target, currentValue, instructions, refs);
    };
  }

  /** 문단형(길게 허용) target — 나머지는 카드/슬로건이라 짧게 강제한다. */
  private static final List<String> GUIDE_LONG_TARGETS = List.of(
      "intro", "principalDesc", "principalQuote", "introMotto", "introBodySecondary",
      "featuredDescription", "philosophyTitle");

  /**
   * Phase 5B — AI Guided Builder 한 단계의 문구 후보 2~3개를 <b>JSON 배열 하나</b>로 요청한다.
   * 단일 target을 다듬는 다른 Task와 달리 여러 후보를 돌려주지만, "지어내지 말 것"(합격 실적·
   * 수치·경력·학력·수상·가격·후기) 제약은 동일하게 못박는다. 실제 검증(배열 파싱·target
   * allowlist·길이)은 {@code AcademyAiGuideService}가 한 번 더 한다.
   */
  private String guideTextPrompt(
      AcademyAiContext context, String target, String currentValue, String instructions,
      List<EducatorReference> references) {
    boolean isLong = GUIDE_LONG_TARGETS.contains(target);
    String lengthRule = isLong
        ? "각 후보는 2~3문장, 180자 이내의 한국어 문단."
        : "각 후보는 1문장, 45자 이내의 한국어 한 줄 문구(문단 금지 — 카드/제목 자리다).";
    return """
        너는 학원 홈페이지 제작을 돕는 AI Guide다. 지금 사용자는 홈페이지의 한 영역을 작성하는
        단계에 있고, 방금 이 영역에서 강조하고 싶은 것을 알려줬다. 그 내용만 활용해 자연스러운
        문구 후보 3개를 만들어라.

        학원명: %s
        대표 과목: %s
        대상: %s
        원장 이름: %s
        지금 작성 중인 영역(내부 키): %s
        이 영역의 현재 문구(있으면 참고, 없으면 새로 작성): %s
        사용자가 이 영역에서 강조하려는 것 / 원하는 톤: %s

        규칙:
        - %s
        - 후보 3개는 서로 표현이 뚜렷이 달라야 한다(같은 문장 조금 바꾼 수준 금지).
        - 사용자가 말하지 않은 사실(합격 실적, 학생 수, 성적 상승 수치, 경력, 학력, 출신 학교,
          수상, 가격, 후기, 연락처)을 절대 새로 지어내지 않는다. 근거가 없으면 쓰지 않는다.
        - 과장·단정 대신 학원이 실제로 할 수 있는 것을 담담하게 쓴다.
        - 출력은 JSON 배열 하나만. 마크다운/설명/코드펜스 없이 ["후보1","후보2","후보3"] 형식만.
        """
        .formatted(
            blank(context.academyName()),
            joinOrNone(context.subjects()),
            joinOrNone(context.targetGroups()),
            blank(context.principalName()),
            blank(target),
            blank(currentValue),
            blankInstructions(instructions),
            lengthRule)
        + referenceBlock(references);
  }

  /**
   * Phase 5A: Academy 전체 진단 + 안전한 개선안 묶음을 <b>JSON 하나</b>로만 요청한다.
   * allowlist(문구 개선 target · 섹션 순서)를 프롬프트에 명시하고, 그 밖의 변경(새 영상/자료
   * 생성, 가격/연락처/원장 이름 변경, 미구현 섹션 콘텐츠 작성)은 절대 제안하지 말라고 못박는다.
   * 실제 방어는 {@code AcademyAiProposalService}가 응답 JSON을 파싱·검증하며 한 번 더 한다.
   */
  private String academyComposePrompt(
      AcademyAiContext context, String instructions, List<EducatorReference> references) {
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
            currentOrder)
        + referenceBlock(references);
  }

  /**
   * Chart 교육자 참고 사례 블록. 사례가 없으면 빈 문자열을 돌려 프롬프트가 이전과 동일하게
   * 유지된다. 이름·학원명은 넣지 않고 id·지역·기준일만 식별로 싣고, 인용 금지 규칙으로
   * 다른 교육자의 실적·수치·프로그램명이 의뢰 학원의 사실처럼 출력되는 것을 막는다.
   */
  private static String referenceBlock(List<EducatorReference> references) {
    if (references == null || references.isEmpty()) return "";
    StringBuilder sb = new StringBuilder()
        .append("\n\n[참고 사례 — BuildIt Chart에 공개된 다른 교육자의 실제 카드 원문. ")
        .append("수업 구조·설명 방식만 참고하고 그대로 옮기지 말 것. 순위 기준일은 ")
        .append(references.get(0).rankAsOf() == null ? "(알 수 없음)" : references.get(0).rankAsOf())
        .append("이며 현재 수치가 아님.]");
    for (EducatorReference r : references) {
      sb.append("\n- (id ").append(r.id())
          .append(" · ").append(blank(r.region())).append(") ")
          .append("철학: ").append(excerpt(mask(r.philosophy(), r)))
          .append(" | 커리큘럼: ").append(excerpt(mask(r.curriculum(), r)))
          .append(" | 수업 방식: ").append(excerpt(mask(r.teaching(), r)));
    }
    sb.append("\n규칙: 위 사례의 원장·학원 이름, 수치, 실적, 프로그램명, 문구를 출력에 포함하지 않는다. ")
        .append("의뢰 학원장이 제공하거나 확인한 값만 슬롯에 채운다.");
    return sb.toString();
  }

  /** 카드 원문 요약이 사례 인물·학원 이름을 포함하는 경우(예: "~원장은 …") 프롬프트에서 마스킹한다. */
  private static String mask(String value, EducatorReference r) {
    if (value == null || value.isBlank()) return value;
    String out = value;
    if (r.name() != null && !r.name().isBlank()) out = out.replace(r.name(), "해당 교육자");
    if (r.academyName() != null && !r.academyName().isBlank()) out = out.replace(r.academyName(), "다른 학원");
    return out;
  }

  private static String excerpt(String value) {
    if (value == null || value.isBlank()) return "(원문 없음)";
    String trimmed = value.trim();
    return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
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
