package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyReviewStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyReviewItemRepository extends JpaRepository<EasyReviewItem, Long> {
  List<EasyReviewItem> findByBusiness_BusinessIdAndStatusOrderByReviewIdDesc(
      Long businessId, EasyReviewStatus status);

  List<EasyReviewItem> findByStatusOrderByReviewIdDesc(EasyReviewStatus status);

  List<EasyReviewItem> findByBusiness_BusinessIdAndKindAndStatusOrderByReviewIdDesc(
      Long businessId, EasyReviewKind kind, EasyReviewStatus status);

  List<EasyReviewItem> findByKindAndStatusOrderByReviewIdDesc(
      EasyReviewKind kind, EasyReviewStatus status);

  long countByBusiness_BusinessIdAndKindAndStatus(
      Long businessId, EasyReviewKind kind, EasyReviewStatus status);

  long countByKindAndStatus(EasyReviewKind kind, EasyReviewStatus status);

  long countByBusiness_BusinessIdAndStatus(Long businessId, EasyReviewStatus status);

  long countByStatus(EasyReviewStatus status);

  boolean existsByBusiness_BusinessIdAndKindAndStatusAndTxn_TxnId(
      Long businessId, EasyReviewKind kind, EasyReviewStatus status, Long txnId);

  boolean existsByBusiness_BusinessIdAndKindAndStatusAndBatch_BatchId(
      Long businessId, EasyReviewKind kind, EasyReviewStatus status, Long batchId);
}
