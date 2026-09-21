package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
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
public interface NewstartKnowledgeItemRepository extends JpaRepository<NewstartKnowledgeItem, Long>,
        JpaSpecificationExecutor<NewstartKnowledgeItem> {

    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.status = :status AND k.sourceType = :type ORDER BY k.createdAt DESC")
    List<NewstartKnowledgeItem> findByStatusAndSourceType(@Param("status") KnowledgeStatus status, @Param("type") SourceType type);

    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.category = :category ORDER BY k.createdAt DESC")
    List<NewstartKnowledgeItem> findByCategory(@Param("category") String category);

    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.status = 'ACTIVE' ORDER BY k.createdAt DESC")
    List<NewstartKnowledgeItem> findAllActive();

    // Production retrieval은 ACTIVE ONLY. REVIEW가 RAG로 유입되지 않도록 ACTIVE로 한정한다.
    // (ADMIN 검토함은 findByStatus(REVIEW) 별도 경로 사용.)
    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.status = 'ACTIVE' ORDER BY k.effectiveFrom ASC")
    List<NewstartKnowledgeItem> findEffective();

    Page<NewstartKnowledgeItem> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.sourceType = :type ORDER BY k.createdAt DESC")
    List<NewstartKnowledgeItem> findBySourceType(@Param("type") SourceType type);

    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.status = :status ORDER BY k.createdAt DESC")
    Page<NewstartKnowledgeItem> findByStatus(@Param("status") KnowledgeStatus status, org.springframework.data.domain.Pageable pageable);

    Optional<NewstartKnowledgeItem> findByTitle(String title);

    long countByStatus(KnowledgeStatus status);

    long countBySourceType(SourceType type);
}