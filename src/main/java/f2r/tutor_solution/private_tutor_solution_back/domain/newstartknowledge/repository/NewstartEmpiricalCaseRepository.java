package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewstartEmpiricalCaseRepository extends JpaRepository<NewstartEmpiricalCase, Long>,
        JpaSpecificationExecutor<NewstartEmpiricalCase> {

    Optional<NewstartEmpiricalCase> findByArticleId(String articleId);

    List<NewstartEmpiricalCase> findByFinalReductionRateIsNotNull();

    long countByFinalReductionRateIsNotNull();

    long countByPredictedReductionRateIsNotNull();

    long countByAuthorFollowupResultIsNotNull();

    @Query("SELECT c FROM NewstartEmpiricalCase c WHERE c.debtTotal IS NOT NULL ORDER BY c.debtTotal ASC")
    List<NewstartEmpiricalCase> findByDebtTotalOrderByDebtTotalAsc();

    @Query("SELECT c FROM NewstartEmpiricalCase c WHERE c.income2025 IS NOT NULL ORDER BY c.income2025 ASC")
    List<NewstartEmpiricalCase> findByIncome2025OrderByIncome2025Asc();

    @Query("SELECT c FROM NewstartEmpiricalCase c WHERE c.age IS NOT NULL ORDER BY c.age ASC")
    List<NewstartEmpiricalCase> findByAgeOrderByAgeAsc();

    @Query("SELECT c FROM NewstartEmpiricalCase c WHERE c.householdType = :type ORDER BY c.createdAt DESC")
    List<NewstartEmpiricalCase> findByHouseholdType(@Param("type") String type);

    @Query("SELECT c FROM NewstartEmpiricalCase c WHERE c.finalReductionRate IS NOT NULL ORDER BY c.writtenAt DESC")
    List<NewstartEmpiricalCase> findWithActualReductionRate();

    Page<NewstartEmpiricalCase> findAllByOrderByCreatedAtDesc(Pageable pageable);
}