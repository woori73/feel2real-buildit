package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;

import java.time.LocalDateTime;

/**
 * 영상 1건의 공개/대시보드 표현. storage key는 노출하지 않고 serve endpoint URL만 준다.
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
    LocalDateTime createdAt) {

  /**
   * @param slug academy slug. {@code video.getAcademy()}가 LAZY라 여기서 역참조하지 않고 호출자가 넘긴다.
   */
  public static AcademyVideoResponse of(AcademyVideo video, String slug) {
    return of(video, slug, null);
  }

  public static AcademyVideoResponse of(
      AcademyVideo video, String slug, VideoOptimizationStatus optimizationStatus) {
    String base = "/api/academies/" + slug + "/videos/" + video.getVideoId();
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
        video.getCreatedAt());
  }
}
