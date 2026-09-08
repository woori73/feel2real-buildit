package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoPurpose;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 영상 1건의 공개/대시보드 표현. storage key는 노출하지 않고 serve endpoint URL만 준다.
 *
 * <p>{@code liveUsageLabels}는 강사 영상 관리 화면 전용 read-only 배지다(Phase 4e) — 이 영상이
 * 내신 LIVE 의 어느 학교/학년에 연결돼 있는지("신구중 · 2학년" 등). 공개 응답에는 비어 있다.
 */
public record AcademyVideoResponse(
    Long videoId,
    String originalFilename,
    String videoUrl,
    String thumbnailUrl,
    Integer durationSec,
    Long durationMs,
    Integer width,
    Integer height,
    VideoStatus status,
    VideoLifecycleStatus availabilityStatus,
    VideoOptimizationStatus optimizationStatus,
    VideoActiveSource activeSource,
    AcademyVideoPurpose purpose,
    boolean usedInLive,
    List<String> liveUsageLabels,
    LocalDateTime createdAt) {

  /**
   * @param slug academy slug. {@code video.getAcademy()}가 LAZY라 여기서 역참조하지 않고 호출자가 넘긴다.
   */
  public static AcademyVideoResponse of(AcademyVideo video, String slug) {
    return of(video, slug, null, List.of());
  }

  public static AcademyVideoResponse of(
      AcademyVideo video, String slug, VideoOptimizationStatus optimizationStatus) {
    return of(video, slug, optimizationStatus, List.of());
  }

  public static AcademyVideoResponse of(
      AcademyVideo video, String slug, VideoOptimizationStatus optimizationStatus,
      List<String> liveUsageLabels) {
    String base = "/api/academies/" + slug + "/videos/" + video.getVideoId();
    List<String> labels = liveUsageLabels == null ? List.of() : liveUsageLabels;
    return new AcademyVideoResponse(
        video.getVideoId(),
        video.getOriginalFilename(),
        base + "/stream",
        video.getThumbnailKey() == null ? null : base + "/thumbnail",
        video.getDurationSec(),
        video.getDurationMs(),
        video.getWidth(),
        video.getHeight(),
        video.getStatus(),
        video.getLifecycleStatus(),
        optimizationStatus,
        video.getActiveSource(),
        video.getPurpose(),
        !labels.isEmpty(),
        labels,
        video.getCreatedAt());
  }
}
