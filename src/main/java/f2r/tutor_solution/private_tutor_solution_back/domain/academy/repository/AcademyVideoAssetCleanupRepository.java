package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoAssetCleanup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetCleanupState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AcademyVideoAssetCleanupRepository
    extends JpaRepository<AcademyVideoAssetCleanup, Long> {

  Optional<AcademyVideoAssetCleanup> findByDedupeKey(String dedupeKey);
  long countByStateNot(VideoAssetCleanupState state);

  @Modifying(flushAutomatically = true)
  @Query(value = """
      INSERT IGNORE INTO academy_video_asset_cleanups (
        dedupe_key, session_id, video_id, job_id, asset_class, asset_key, reason,
        state, attempt_count, next_attempt_at, created_at, updated_at
      ) VALUES (
        :dedupeKey, :sessionId, :videoId, :jobId, :assetClass, :assetKey, :reason,
        'PENDING', 0, :now, :now, :now
      )
      """, nativeQuery = true)
  int insertPendingIgnoreDuplicate(
      @Param("dedupeKey") String dedupeKey,
      @Param("sessionId") String sessionId,
      @Param("videoId") Long videoId,
      @Param("jobId") Long jobId,
      @Param("assetClass") String assetClass,
      @Param("assetKey") String assetKey,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now);

  boolean existsByVideo_VideoIdAndStateNot(Long videoId, VideoAssetCleanupState state);
  boolean existsBySession_SessionIdAndStateNot(String sessionId, VideoAssetCleanupState state);
  boolean existsByVideo_Academy_AcademyId(Long academyId);

  List<AcademyVideoAssetCleanup> findByVideo_VideoId(Long videoId);
  List<AcademyVideoAssetCleanup> findBySession_SessionId(String sessionId);

  @Query(value = """
      SELECT cleanup_id
      FROM academy_video_asset_cleanups
      WHERE (state = 'PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6)))
         OR (state = 'PROCESSING' AND lease_expires_at <= CURRENT_TIMESTAMP(6))
      ORDER BY
        CASE WHEN state = 'PROCESSING' THEN lease_expires_at ELSE next_attempt_at END,
        cleanup_id
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  Optional<Long> lockNextDueId();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from AcademyVideoAssetCleanup c where c.cleanupId = :cleanupId")
  Optional<AcademyVideoAssetCleanup> findForUpdateByCleanupId(
      @Param("cleanupId") Long cleanupId);

  @Query(value = "SELECT CURRENT_TIMESTAMP(6)", nativeQuery = true)
  LocalDateTime databaseNow();

  List<AcademyVideoAssetCleanup>
      findTop100ByStateAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
          VideoAssetCleanupState state, LocalDateTime now);
}
