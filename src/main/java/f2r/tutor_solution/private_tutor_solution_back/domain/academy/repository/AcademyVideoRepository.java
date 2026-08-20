package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AcademyVideoRepository extends JpaRepository<AcademyVideo, Long> {

  /** 공개 페이지 노출용. READY 영상만 최신순 최대 10개. */
  List<AcademyVideo> findTop10ByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
      Long academyId, VideoLifecycleStatus lifecycleStatus);

  /** Transitional adapter for the V930 synchronous service. */
  @Deprecated(forRemoval = true)
  default List<AcademyVideo> findTop10ByAcademy_AcademyIdAndStatusOrderByCreatedAtDesc(
      Long academyId, VideoStatus status) {
    if (status != VideoStatus.READY) {
      return List.of();
    }
    return findTop10ByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
        academyId, VideoLifecycleStatus.READY);
  }

  /** 강사 대시보드 목록. 내부 DELETING/UNAVAILABLE tombstone은 query 단계에서 제외한다. */
  List<AcademyVideo> findByAcademy_AcademyIdAndLifecycleStatusOrderByCreatedAtDesc(
      Long academyId, VideoLifecycleStatus lifecycleStatus);

  /** 업로드 개수 상한(app.video.max-count) 서버 재검증용. */
  long countByAcademy_AcademyId(Long academyId);
  boolean existsByAcademy_AcademyId(Long academyId);
  boolean existsByRawStorageKey(String rawStorageKey);
  boolean existsByNormalizedStorageKey(String normalizedStorageKey);
  boolean existsByThumbnailKey(String thumbnailKey);

  List<AcademyVideo> findByLifecycleStatusOrderByVideoId(
      VideoLifecycleStatus lifecycleStatus, Pageable pageable);

  List<AcademyVideo>
      findByLifecycleStatusAndActiveSourceAndRawCleanupStatusOrderByVideoId(
          VideoLifecycleStatus lifecycleStatus,
          f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource activeSource,
          f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoRawCleanupStatus rawCleanupStatus,
          Pageable pageable);

  long countByAcademy_AcademyIdAndLifecycleStatusNot(
      Long academyId, VideoLifecycleStatus lifecycleStatus);

  /** academy 범위를 조건에 포함해 타 학원 영상 접근을 쿼리 단계에서 차단한다. */
  Optional<AcademyVideo> findByVideoIdAndAcademy_AcademyId(Long videoId, Long academyId);

  /** 삭제와 대표 영상 연결을 직렬화하는 부모 행 잠금. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select v from AcademyVideo v
      where v.videoId = :videoId and v.academy.academyId = :academyId
      """)
  Optional<AcademyVideo> findForUpdateByVideoIdAndAcademyId(
      @Param("videoId") Long videoId,
      @Param("academyId") Long academyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from AcademyVideo v where v.videoId = :videoId")
  Optional<AcademyVideo> findForUpdateByVideoId(@Param("videoId") Long videoId);

  /**
   * 주어진 영상들이 이 학원 소유인지 한 번에 판정한다(대표 영상 IDOR 차단).
   *
   * <p>결과 개수가 요청 id 개수와 다르면 남의 영상이거나 없는 영상이 섞여 있다는 뜻이다.
   * 항목마다 조회하지 않도록 집합으로 묶고, videoId 순서로 부모 행을 잠가 삭제와의 경쟁을 막는다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select v from AcademyVideo v
      where v.videoId in :videoIds and v.academy.academyId = :academyId
      order by v.videoId asc
      """)
  List<AcademyVideo> findAllForUpdateByVideoIdsAndAcademyId(
      @Param("videoIds") Collection<Long> videoIds,
      @Param("academyId") Long academyId);
}
