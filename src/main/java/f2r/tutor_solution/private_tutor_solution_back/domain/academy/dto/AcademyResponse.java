package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;

import java.util.List;
import java.util.Set;

public record AcademyResponse(
    Long academyId,
    String slug,
    String handle,
    String name,
    String intro,
    AcademyStatus status,
    boolean isVisible,
    String logoUrl,
    String coverUrl,
    String publicUrl,
    List<SubjectSummary> subjects,
    Set<TargetGroup> targetGroups,
    List<AcademyRegionResponse> regions,
    List<LessonResponse> lessons,
    /**
     * 이미지가 채워진 히어로 슬롯 key 목록(V940). storage key는 싣지 않는다 —
     * 바이트는 {@code /api/academies/{slug}/hero-media/{slot}}로만 나간다.
     * 프론트는 이 목록으로 실제 이미지와 시안 placeholder를 가른다.
     */
    List<String> heroMediaSlots,
    AcademyPageSettings pageSettings
) {
  public static AcademyResponse of(
      Academy academy,
      List<SubjectSummary> subjects,
      List<AcademyRegionResponse> regions) {
    return of(academy, subjects, regions, List.of(), null);
  }

  public static AcademyResponse of(
      Academy academy,
      List<SubjectSummary> subjects,
      List<AcademyRegionResponse> regions,
      List<LessonResponse> lessons) {
    return of(academy, subjects, regions, lessons, null);
  }

  /**
   * @param baseDomain 서브도메인 base(예: prd={@code feel2real.co.kr}). 있고 handle도 있으면
   *                   publicUrl은 서브도메인({@code https://{handle}.{baseDomain}/}), 아니면 경로형
   *                   ({@code /academies/{slug}}). 로컬/dev/stg는 base-domain 미설정이라 경로형.
   */
  public static AcademyResponse of(
      Academy academy,
      List<SubjectSummary> subjects,
      List<AcademyRegionResponse> regions,
      List<LessonResponse> lessons,
      String baseDomain) {
    String base = "/api/academies/" + academy.getSlug();
    return new AcademyResponse(
        academy.getAcademyId(),
        academy.getSlug(),
        academy.getHandle(),
        academy.getName(),
        academy.getIntro(),
        academy.getStatus(),
        academy.isVisible(),
        base + "/logo",
        academy.getCoverStorageKey() == null ? null : base + "/cover",
        publicUrl(academy, baseDomain),
        subjects,
        TargetGroup.canonicalOrder(academy.getTargetGroups()),
        regions,
        lessons,
        filledHeroMediaSlots(academy),
        academy.getPageSettings());
  }

  /**
   * 이미지가 실제로 들어 있는 슬롯만 추린다. 빈 슬롯은 시안 placeholder로 렌더된다.
   *
   * <p>슬롯→컬럼 매핑은 {@link Academy#imageSlotKey}에만 둔다. 여기서 분기를 한 벌 더
   * 들고 있으면 슬롯이 늘 때(V948 schedule) 한쪽만 고쳐져 응답에서 조용히 빠진다.
   */
  private static List<String> filledHeroMediaSlots(Academy academy) {
    List<String> filled = new java.util.ArrayList<>();
    for (HeroMediaSlot slot : HeroMediaSlot.values()) {
      String key = academy.imageSlotKey(slot);
      if (key != null && !key.isBlank()) filled.add(slot.key());
    }
    return List.copyOf(filled);
  }

  /** 공개 페이지 URL. base-domain+handle 있으면 서브도메인, 없으면 경로형(전환기·로컬). */
  private static String publicUrl(Academy academy, String baseDomain) {
    String handle = academy.getHandle();
    if (baseDomain != null && !baseDomain.isBlank() && handle != null && !handle.isBlank()) {
      return "https://" + handle + "." + baseDomain + "/";
    }
    return "/academies/" + academy.getSlug();
  }

  public record SubjectSummary(Integer subjectId, String nameKo, String nameEn) {
  }
}
