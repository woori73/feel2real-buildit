package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaComment;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity.BetaCommentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetaCommentRepository extends JpaRepository<BetaComment, Long> {

  /**
   * 랜딩 캐러셀에 노출할 승인 의견. 최신순 상한 10건.
   *
   * <p>{@code idx_beta_comments_status_created (status, created_at)}를 그대로 탄다.
   */
  List<BetaComment> findTop10ByStatusOrderByCreatedAtDesc(BetaCommentStatus status);

  /** 어드민 모더레이션 목록. 상한 없이 상태별 전체. */
  List<BetaComment> findAllByStatusOrderByCreatedAtDesc(BetaCommentStatus status);

  long countByStatus(BetaCommentStatus status);
}
