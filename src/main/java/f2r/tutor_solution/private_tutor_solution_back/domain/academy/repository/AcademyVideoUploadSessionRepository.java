package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideoUploadSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoUploadSessionState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AcademyVideoUploadSessionRepository
    extends JpaRepository<AcademyVideoUploadSession, String> {

  Optional<AcademyVideoUploadSession> findBySessionIdAndAcademy_AcademyId(
      String sessionId, Long academyId);

  List<AcademyVideoUploadSession> findByAcceptedVideo_VideoId(Long videoId);
  boolean existsByAcademy_AcademyId(Long academyId);
  boolean existsByStagingKey(String stagingKey);
  long countByStateIn(Collection<VideoUploadSessionState> states);

  List<AcademyVideoUploadSession> findByAcceptedVideoIsNullAndStateInOrderBySessionId(
      Collection<VideoUploadSessionState> states, Pageable pageable);

  @Query(value = "select current_timestamp(6)", nativeQuery = true)
  LocalDateTime databaseNow();

  @Modifying
  @Query(value = """
      insert into academy_video_upload_sessions (
        session_id, academy_id, requested_filename, declared_size_bytes, state,
        staging_key, multipart_upload_id, part_size_bytes, expected_part_count,
        deadline_at, created_at, updated_at
      ) values (
        :sessionId, :academyId, :filename, :declaredSize, 'INITIATED',
        :stagingKey, :uploadId, :partSize, :partCount,
        timestampadd(microsecond, :deadlineMicros, current_timestamp(6)),
        current_timestamp(6), current_timestamp(6)
      )
      """, nativeQuery = true)
  int insertInitiatedUsingDatabaseClock(
      @Param("sessionId") String sessionId,
      @Param("academyId") Long academyId,
      @Param("filename") String filename,
      @Param("declaredSize") long declaredSize,
      @Param("stagingKey") String stagingKey,
      @Param("uploadId") String uploadId,
      @Param("partSize") long partSize,
      @Param("partCount") int partCount,
      @Param("deadlineMicros") long deadlineMicros);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select session
      from AcademyVideoUploadSession session
      where session.sessionId = :sessionId
        and session.academy.academyId = :academyId
      """)
  Optional<AcademyVideoUploadSession> findWithLockBySessionIdAndAcademyId(
      @Param("sessionId") String sessionId, @Param("academyId") Long academyId);

  long countByAcademy_AcademyIdAndStateInAndDeadlineAtGreaterThanEqual(
      Long academyId,
      Collection<VideoUploadSessionState> states,
      LocalDateTime now);

  @EntityGraph(attributePaths = "academy")
  List<AcademyVideoUploadSession>
      findTop100ByStateAndNextFinalizeAttemptAtLessThanEqualOrderByNextFinalizeAttemptAt(
          VideoUploadSessionState state, LocalDateTime now);

  @EntityGraph(attributePaths = "academy")
  List<AcademyVideoUploadSession>
      findTop100ByStateInAndDeadlineAtLessThanOrderByDeadlineAt(
          Collection<VideoUploadSessionState> states, LocalDateTime now);
}
