package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoOptimizationJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoOptimizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AcademyVideoOptimizationJobRepository
    extends JpaRepository<AcademyVideoOptimizationJob, Long> {

  Optional<AcademyVideoOptimizationJob> findByVideo_VideoId(Long videoId);
  List<AcademyVideoOptimizationJob> findByVideo_VideoIdIn(Collection<Long> videoIds);
  long countByStatus(VideoOptimizationStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from AcademyVideoOptimizationJob j where j.video.videoId = :videoId")
  Optional<AcademyVideoOptimizationJob> findForUpdateByVideoId(@Param("videoId") Long videoId);
  boolean existsByVideo_Academy_AcademyId(Long academyId);

  @Query(value = """
      SELECT job_id
      FROM academy_video_optimization_jobs
      WHERE status = 'PROCESSING'
        AND lease_expires_at <= CURRENT_TIMESTAMP(6)
      ORDER BY lease_expires_at, job_id
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  Optional<Long> lockNextExpiredId();

  @Query(value = """
      SELECT job_id
      FROM academy_video_optimization_jobs
      WHERE status IN ('PENDING', 'RETRY_WAIT')
        AND next_attempt_at <= CURRENT_TIMESTAMP(6)
        AND attempt_count < 2
      ORDER BY next_attempt_at, job_id
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  Optional<Long> lockNextDueClaimId();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from AcademyVideoOptimizationJob j where j.jobId = :jobId")
  Optional<AcademyVideoOptimizationJob> findForUpdateByJobId(@Param("jobId") Long jobId);

  @Query(value = "SELECT CURRENT_TIMESTAMP(6)", nativeQuery = true)
  LocalDateTime databaseNow();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE academy_video_optimization_jobs
      SET heartbeat_at = CURRENT_TIMESTAMP(6),
          lease_expires_at = TIMESTAMPADD(MICROSECOND, :leaseMicros, CURRENT_TIMESTAMP(6)),
          progress_at = CASE WHEN :mediaProgress = true THEN CURRENT_TIMESTAMP(6) ELSE progress_at END
      WHERE job_id = :jobId
        AND status = 'PROCESSING'
        AND lease_token = :leaseToken
        AND lease_expires_at > CURRENT_TIMESTAMP(6)
      """, nativeQuery = true)
  int heartbeat(
      @Param("jobId") Long jobId,
      @Param("leaseToken") String leaseToken,
      @Param("leaseMicros") long leaseMicros,
      @Param("mediaProgress") boolean mediaProgress);

  List<AcademyVideoOptimizationJob>
      findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
          Collection<VideoOptimizationStatus> statuses, LocalDateTime now);
}
