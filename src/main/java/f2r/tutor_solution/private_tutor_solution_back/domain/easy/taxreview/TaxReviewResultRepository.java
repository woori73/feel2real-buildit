package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxReviewResultRepository extends JpaRepository<TaxReviewRecord, Long> {

    List<TaxReviewRecord> findByTxnId(Long txnId);

    List<TaxReviewRecord> findByBusinessType(String businessType);

    List<TaxReviewRecord> findByBusinessTypeAndUserConfirmed(String businessType, Boolean confirmed);
}
