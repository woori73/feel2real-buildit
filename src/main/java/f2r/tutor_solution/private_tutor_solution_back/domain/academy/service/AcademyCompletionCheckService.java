package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyCompletionResult.CompletionItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoPurpose;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI에게 판단을 맡기지 않는 deterministic Academy 완성도 검사기.
 *
 * <p>규칙은 전부 실제 schema에서 신뢰성 있게 확인 가능한 항목으로만 정의한다(파일
 * 존재/텍스트 비어있음/영상 purpose 등) — "그럴듯해 보이는" AI 판단이 아니다. 항목을
 * 추가하려면 이 클래스에 명시적 규칙을 추가해야 하고, 점수 산식은 항상
 * {@code done 개수 / 전체 개수 * 100}이다(임의 가중치 없음).
 */
@Service
public class AcademyCompletionCheckService {

  private final AcademyService academyService;
  private final LessonProductRepository lessonProductRepository;
  private final AcademyVideoRepository academyVideoRepository;

  public AcademyCompletionCheckService(
      AcademyService academyService,
      LessonProductRepository lessonProductRepository,
      AcademyVideoRepository academyVideoRepository) {
    this.academyService = academyService;
    this.lessonProductRepository = lessonProductRepository;
    this.academyVideoRepository = academyVideoRepository;
  }

  public AcademyCompletionResult evaluate(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    List<LessonProduct> lessons =
        lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(academy.getAcademyId());
    List<AcademyVideo> videos = academyVideoRepository
        .findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
            academy.getAcademyId(), VideoLifecycleStatus.READY);
    return evaluate(academy, lessons, videos);
  }

  /** Academy/lessons/videos를 이미 들고 있는 호출자를 위한 순수 평가 함수. */
  public AcademyCompletionResult evaluate(
      Academy academy, List<LessonProduct> lessons, List<AcademyVideo> videos) {
    Map<String, String> draft =
        academy.getPageSettings() != null && academy.getPageSettings().builderDraft() != null
            ? academy.getPageSettings().builderDraft()
            : Map.of();

    boolean hasDirectorVideo = videos.stream().anyMatch(v -> v.getPurpose() == AcademyVideoPurpose.DIRECTOR)
        || !isBlank(draft.get("principalVideoId"))
        || !isBlank(draft.get("principalYoutubeUrl"));
    boolean hasLessonVideo = videos.stream().anyMatch(v -> v.getPurpose() == AcademyVideoPurpose.LESSON);
    boolean hasMiniLectures = hasNonEmptyJsonArray(draft.get("miniLectures"));
    boolean hasProgramMedia = lessons.stream()
        .anyMatch(l -> !isBlank(l.getMediaImageKey()) || l.getMediaVideoId() != null);
    boolean hasCta = academy.getPageSettings() != null
        && academy.getPageSettings().businessInfo() != null
        && (!isBlank(academy.getPageSettings().businessInfo().phone())
            || !isBlank(academy.getPageSettings().businessInfo().email()));

    List<CompletionItem> items = List.of(
        new CompletionItem("intro", "학원 소개", !isBlank(academy.getIntro())),
        new CompletionItem("principalDesc", "원장 소개", !isBlank(draft.get("principalDesc"))),
        new CompletionItem("directorVideo", "대표 원장 영상", hasDirectorVideo),
        new CompletionItem("lessons", "대표 수업", !lessons.isEmpty()),
        new CompletionItem("lessonVideo", "수업 영상", hasLessonVideo),
        new CompletionItem("miniLectures", "미니 강의", hasMiniLectures),
        new CompletionItem("programMedia", "프로그램 이미지/영상", hasProgramMedia),
        new CompletionItem("cta", "상담 연락처", hasCta));

    long done = items.stream().filter(CompletionItem::done).count();
    int score = items.isEmpty() ? 0 : Math.round(100f * done / items.size());
    return new AcademyCompletionResult(score, items);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** builderDraft.miniLectures는 "[]"(빈 배열)로도 저장될 수 있어 길이가 아니라 원소 유무를 본다. */
  private static boolean hasNonEmptyJsonArray(String rawJson) {
    if (isBlank(rawJson)) return false;
    String trimmed = rawJson.trim();
    return trimmed.length() > 2; // "[]"보다 길면 최소 하나는 있다고 본다(관대한 휴리스틱).
  }
}
