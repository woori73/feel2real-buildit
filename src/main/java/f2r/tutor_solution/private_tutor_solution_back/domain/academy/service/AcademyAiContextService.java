package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.Composition;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.Composition.SectionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.LessonSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoPurpose;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Academy의 현재 상태를 AI가 안정적으로 이해할 수 있는 하나의 normalized context로 조립한다.
 *
 * <p>여기서 만드는 {@link AcademyAiContext}가 유일한 AI 입력 표현이다 — JPA Entity를 AI 호출
 * 경로 어디에도 직접 넘기지 않는다. 조립은 순수 함수(같은 Academy 상태 → 같은 결과)이고,
 * DB 전체를 덤프하지 않고 A급 필드만 명시적으로 골라 담는다(§4 클래스 주석 참고).
 *
 * <p>인증/권한: {@link AcademyService#requireMyAcademy}를 그대로 재사용한다 — 이 서비스는 절대
 * academyId를 파라미터로 받지 않는다. 호출자가 다른 학원의 Academy를 조회할 방법이 구조적으로
 * 없다(다른 Academy write path와 동일한 보장).
 *
 * <p>Phase 5A: 전체 페이지 진단(ACADEMY_COMPOSE)을 위해 {@link Composition}까지 조립한다 —
 * 섹션 카탈로그(순서·라벨·editable)는 {@link AcademyService#DEFAULT_SECTION_ORDER}/
 * {@link AcademyService#OPT_IN_SECTION_IDS}를 재사용하고, 완성도 요약은
 * {@link AcademyCompletionCheckService}의 deterministic 결과를 그대로 쓴다(중복 판정 금지).
 */
@Service
public class AcademyAiContextService {

  /** 사람이 읽는 섹션 이름. {@code STYLE_PANEL_SECTION_NAMES}(academy_builder.js)와 맞춘다. */
  private static final Map<String, String> SECTION_LABELS = Map.ofEntries(
      Map.entry("sec-hero", "히어로 배너"),
      Map.entry("sec-featured", "Featured · 시즌 프로그램"),
      Map.entry("sec-intro", "학원 소개"),
      Map.entry("sec-principal", "원장 소개"),
      Map.entry("sec-philosophy", "학습 철학"),
      Map.entry("sec-videos", "수업 영상"),
      Map.entry("sec-minilectures", "미니 강의"),
      Map.entry("sec-reviews", "수강 후기"),
      Map.entry("sec-lessons", "대표 수업 · 강의 목록"),
      Map.entry("sec-curriculum", "커리큘럼"),
      Map.entry("sec-effect", "학습 효과"),
      Map.entry("sec-digitaltwin", "AI Native 교육 과정"),
      Map.entry("sec-market", "마켓플레이스"),
      Map.entry("sec-payment", "수강료 결제"),
      Map.entry("sec-cta", "상담 신청 CTA"),
      Map.entry("sec-social", "외부 연동"));

  /**
   * 현재 Builder가 실제 콘텐츠 입력을 지원하지 않는 섹션. AI는 이 섹션의 순서·문구 변경을
   * 제안하면 안 된다(제안하더라도 {@code AcademyAiProposalService}가 거부).
   */
  private static final java.util.Set<String> NON_EDITABLE_SECTION_IDS =
      java.util.Set.of("sec-reviews", "sec-curriculum", "sec-effect", "sec-market", "sec-payment");

  /**
   * AI가 문구 개선(TEXT proposal)을 제안할 수 있는 builderDraft key. 전부 서버
   * {@code AcademyService.BUILDER_DRAFT_KEYS}의 부분집합이며, 가격/연락처/원장 이름·직함/학원명
   * 처럼 "잘못 바뀌면 위험한" 값은 제외했다. {@code AcademyAiProposalService}의 allowlist와
   * 반드시 동일하게 유지한다.
   */
  static final List<String> EDITABLE_TEXT_TARGETS = List.of(
      "principalDesc",
      "principalQuote",
      "philosophyItem1",
      "philosophyItem2",
      "philosophyItem3",
      "philosophyItem4",
      "philosophyKeyword",
      "philosophyTitle",
      "introHeading",
      "introMotto",
      "introBodySecondary",
      "featuredEyebrow",
      "featuredTitle",
      "featuredDescription",
      "featuredCtaLabel",
      "videosSectionTitle",
      "socialSectionTitle",
      "miniLecturesSectionTitle",
      "ctaTitle",
      "ctaSubtitle");

  private final AcademyService academyService;
  private final LessonProductRepository lessonProductRepository;
  private final AcademyVideoRepository academyVideoRepository;
  private final AcademyCompletionCheckService completionCheckService;

  public AcademyAiContextService(
      AcademyService academyService,
      LessonProductRepository lessonProductRepository,
      AcademyVideoRepository academyVideoRepository,
      AcademyCompletionCheckService completionCheckService) {
    this.academyService = academyService;
    this.lessonProductRepository = lessonProductRepository;
    this.academyVideoRepository = academyVideoRepository;
    this.completionCheckService = completionCheckService;
  }

  public AcademyAiContext buildContext(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademyForBuilder(principal);
    List<LessonProduct> lessons =
        lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(academy.getAcademyId());
    List<AcademyVideo> videos =
        academyVideoRepository.findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
            academy.getAcademyId(), VideoLifecycleStatus.READY);
    return buildContext(academy, lessons, videos);
  }

  /** Academy/lessons/videos를 이미 들고 있는 호출자를 위한 순수 조립 함수. */
  public AcademyAiContext buildContext(
      Academy academy, List<LessonProduct> lessons, List<AcademyVideo> videos) {
    Map<String, String> draft =
        academy.getPageSettings() != null && academy.getPageSettings().builderDraft() != null
            ? academy.getPageSettings().builderDraft()
            : Map.of();

    return new AcademyAiContext(
        academy.getName(),
        academy.getIntro(),
        academy.getSubjects().stream().map(Subject::getNameKo).sorted().toList(),
        academy.getTargetGroups().stream()
            .sorted(Comparator.comparingInt(Enum::ordinal))
            .map(TargetGroup::name)
            .toList(),
        draft.getOrDefault("principalName", ""),
        draft.getOrDefault("principalRole", ""),
        draft.getOrDefault("principalDesc", ""),
        List.of(
            draft.getOrDefault("philosophyItem1", ""),
            draft.getOrDefault("philosophyItem2", ""),
            draft.getOrDefault("philosophyItem3", ""),
            draft.getOrDefault("philosophyItem4", "")),
        lessons.stream()
            .map(lesson -> new LessonSummary(
                lesson.getName(),
                nullToEmpty(lesson.getKicker()),
                nullToEmpty(lesson.getLearnerNeed()),
                nullToEmpty(lesson.getHighlight())))
            .toList(),
        buildComposition(academy, lessons, videos, draft));
  }

  private Composition buildComposition(
      Academy academy,
      List<LessonProduct> lessons,
      List<AcademyVideo> videos,
      Map<String, String> draft) {
    Map<String, Boolean> visibility =
        academy.getPageSettings() != null && academy.getPageSettings().sectionVisibility() != null
            ? academy.getPageSettings().sectionVisibility()
            : Map.of();

    int lessonVideoCount =
        (int) videos.stream().filter(v -> v.getPurpose() == AcademyVideoPurpose.LESSON).count();
    boolean hasDirectorVideo =
        videos.stream().anyMatch(v -> v.getPurpose() == AcademyVideoPurpose.DIRECTOR)
            || !isBlank(draft.get("principalVideoId"))
            || !isBlank(draft.get("principalYoutubeUrl"));
    int miniLectureCount = countJsonArrayElements(draft.get("miniLectures"));
    int programCardCount = lessons.size();
    int programCardWithMediaCount = (int) lessons.stream()
        .filter(l -> !isBlank(l.getMediaImageKey()) || l.getMediaVideoId() != null)
        .count();

    List<SectionState> sections = orderedSectionIds(academy).stream()
        .map(id -> new SectionState(
            id,
            SECTION_LABELS.getOrDefault(id, id),
            isSectionVisible(id, visibility),
            !NON_EDITABLE_SECTION_IDS.contains(id),
            sectionHasContent(id, academy, draft, lessons, videos, hasDirectorVideo,
                miniLectureCount, lessonVideoCount)))
        .toList();

    AcademyCompletionResult completion = completionCheckService.evaluate(academy, lessons, videos);
    List<String> missing = completion.items().stream()
        .filter(item -> !item.done())
        .map(AcademyCompletionResult.CompletionItem::label)
        .toList();

    return new Composition(
        sections,
        hasDirectorVideo,
        lessonVideoCount,
        miniLectureCount,
        programCardCount,
        programCardWithMediaCount,
        draft.getOrDefault("featuredCtaLabel", ""),
        EDITABLE_TEXT_TARGETS,
        completion.scorePercent(),
        missing);
  }

  /** {@code AcademyService.normalizeSectionOrderForWrite}와 같은 규칙: 저장값 → 정규화, 없으면 기본. */
  private static List<String> orderedSectionIds(Academy academy) {
    List<String> stored =
        academy.getPageSettings() != null ? academy.getPageSettings().sectionOrder() : null;
    if (stored == null || stored.isEmpty()) {
      return AcademyService.DEFAULT_SECTION_ORDER;
    }
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (String id : stored) {
      if (AcademyService.DEFAULT_SECTION_ORDER.contains(id)) {
        seen.add(id);
      }
    }
    for (String id : AcademyService.DEFAULT_SECTION_ORDER) {
      seen.add(id);
    }
    List<String> ordered = new ArrayList<>(seen);
    // sec-hero는 항상 0번(서버 validateSectionOrder와 동일 불변).
    if (!"sec-hero".equals(ordered.get(0))) {
      return AcademyService.DEFAULT_SECTION_ORDER;
    }
    return ordered;
  }

  private static boolean isSectionVisible(String id, Map<String, Boolean> visibility) {
    Boolean explicit = visibility.get(id);
    if (AcademyService.OPT_IN_SECTION_IDS.contains(id)) {
      return Boolean.TRUE.equals(explicit); // opt-in: 명시적 true일 때만
    }
    return !Boolean.FALSE.equals(explicit); // 기존 섹션: 명시적 false일 때만 숨김
  }

  private static boolean sectionHasContent(
      String id,
      Academy academy,
      Map<String, String> draft,
      List<LessonProduct> lessons,
      List<AcademyVideo> videos,
      boolean hasDirectorVideo,
      int miniLectureCount,
      int lessonVideoCount) {
    return switch (id) {
      case "sec-hero" -> true;
      case "sec-intro" -> !isBlank(academy.getIntro());
      case "sec-principal" -> !isBlank(draft.get("principalDesc")) || hasDirectorVideo;
      case "sec-philosophy" -> java.util.stream.Stream.of(
              "philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4")
          .anyMatch(k -> !isBlank(draft.get(k)));
      case "sec-videos" -> lessonVideoCount > 0;
      case "sec-minilectures" -> miniLectureCount > 0;
      case "sec-lessons" -> !lessons.isEmpty();
      case "sec-featured" -> !isBlank(draft.get("featuredTitle"))
          || !isBlank(draft.get("featuredDescription"));
      case "sec-digitaltwin" -> !isBlank(draft.get("aiProcessTitle"))
          || !isBlank(draft.get("aiProcessDescription"));
      case "sec-social" -> academy.getPageSettings() != null
          && (!isBlank(academy.getPageSettings().blogUrl())
              || !isBlank(academy.getPageSettings().storeUrl())
              || !isBlank(academy.getPageSettings().naverMapUrl()));
      default -> false; // 미구현 섹션은 콘텐츠 없음
    };
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** {@code miniLectures}는 JSON 배열 문자열. 원소 개수를 대략 센다(콤마 기반, 관대). */
  private static int countJsonArrayElements(String rawJson) {
    if (isBlank(rawJson)) return 0;
    String trimmed = rawJson.trim();
    if (trimmed.length() <= 2 || !trimmed.startsWith("[")) return 0;
    if (trimmed.endsWith("]") && trimmed.substring(1, trimmed.length() - 1).isBlank()) return 0;
    int depth = 0;
    int count = 1;
    boolean inString = false;
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (inString) {
        if (c == '\\') {
          i++;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      switch (c) {
        case '"' -> inString = true;
        case '{', '[' -> depth++;
        case '}', ']' -> depth--;
        case ',' -> {
          if (depth == 1) count++;
        }
        default -> {
          // no-op
        }
      }
    }
    return count;
  }
}
