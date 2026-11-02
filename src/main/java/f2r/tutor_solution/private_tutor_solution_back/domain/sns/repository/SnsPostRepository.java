package f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnsPostRepository extends JpaRepository<SnsPost, Long> {

  List<SnsPost> findByStatusInOrderByCreatedAtDesc(Collection<SnsPost.Status> statuses, Pageable pageable);

  List<SnsPost> findByRuleIdAndTrialTrueOrderByCreatedAtDesc(Long ruleId, Pageable pageable);

  long countByStatus(SnsPost.Status status);

  long countByRuleId(Long ruleId);

  Optional<SnsPost> findByMediaToken(String mediaToken);

  /** 같은 규칙의 최근 주제(시험 생성·버린 글 제외) — 주제 중복 피하기용. */
  @Query("select p.topic from SnsPost p where p.ruleId = :ruleId and p.trial = false and p.topic is not null "
      + "and p.status <> f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.Status.DISCARDED order by p.createdAt desc")
  List<String> recentTopics(@Param("ruleId") Long ruleId, Pageable pageable);

  /** GPU 작업자가 가져갈 이미지 작업 후보: 대기 중이거나, 가져간 지 오래돼 멈춘 것. */
  @Query("select p from SnsPost p where p.status = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.Status.GENERATING "
      + "and p.captionStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.DONE "
      + "and (p.imageStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.PENDING "
      + "or (p.imageStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.RUNNING and p.imageClaimedAt < :staleBefore)) "
      + "order by p.createdAt asc")
  List<SnsPost> imageJobCandidates(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);

  /** 후보 하나를 원자적으로 가져간다. 1이면 이번 작업자가 가져감. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update SnsPost p set p.imageStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.RUNNING, "
      + "p.imageClaimedAt = :now, p.imageAttempts = p.imageAttempts + 1 "
      + "where p.id = :id and p.imageAttempts = :attempts and p.captionStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.DONE and (p.imageStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.PENDING "
      + "or (p.imageStatus = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.PartStatus.RUNNING and p.imageClaimedAt < :staleBefore))")
  int claimImageJob(@Param("id") Long id, @Param("attempts") int attempts, @Param("now") LocalDateTime now,
      @Param("staleBefore") LocalDateTime staleBefore);

  @Query("select p from SnsPost p where p.status = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.Status.SCHEDULED "
      + "and p.scheduledAt <= :now order by p.scheduledAt asc")
  List<SnsPost> dueScheduled(@Param("now") LocalDateTime now);

  @Query("select count(p) from SnsPost p where p.status = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.Status.SCHEDULED "
      + "and p.scheduledAt >= :from and p.scheduledAt < :to")
  long countScheduledBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  @Query("select count(p) from SnsPost p where p.status = f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost.Status.PUBLISHED "
      + "and p.publishedAt >= :from")
  long countPublishedSince(@Param("from") LocalDateTime from);
}
