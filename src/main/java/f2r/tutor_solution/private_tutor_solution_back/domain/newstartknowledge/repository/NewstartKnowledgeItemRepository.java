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

    // ── 회원 갈무리 feed(설계 §6) 전용 조회 ────────────────────────────────────────────
    // ACTIVE이면서 "지금 효력이 있는" 항목만 회원 화면에 내린다. REVIEW/DRAFT는 운영자 검토함이고,
    // effective_to가 지난 항목은 [공식·과거]로 정리되기 전까지 feed에서 빠진다(RAG와 동일한 안전선).
    // 2026-09-29 source 교정: 갈무리는 Wiki/OFFICIAL·EBOOK 목록이 아니다 — 호출 측이 넘기는
    // community source type 집합(:sourceTypes)에 속한 항목만 feed에 오른다.
    // 분류 필터는 nullable 파라미터 하나로 합치지 않고 메서드를 나눈다 — JPQL의 ":p IS NULL OR"
    // 패턴은 MySQL 방언에서 파라미터 타입 추론 실패 위험이 있어, 이 경로와 같이 DB 검증 없이
    // merge되는 작업에서는 두 개의 명확한 쿼리로 둔다.
    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.status = :status"
            + " AND k.sourceType IN :sourceTypes"
            + " AND (k.effectiveFrom IS NULL OR k.effectiveFrom <= :today)"
            + " AND (k.effectiveTo IS NULL OR k.effectiveTo >= :today)"
            + " ORDER BY COALESCE(k.sourceDate, k.createdAt) DESC, k.knowledgeId DESC")
    Page<NewstartKnowledgeItem> findFeed(@Param("status") KnowledgeStatus status,
                                         @Param("sourceTypes") java.util.Collection<SourceType> sourceTypes,
                                         @Param("today") java.time.LocalDate today,
                                         Pageable pageable);

    @Query("SELECT k FROM NewstartKnowledgeItem k WHERE k.status = :status AND k.category = :category"
            + " AND k.sourceType IN :sourceTypes"
            + " AND (k.effectiveFrom IS NULL OR k.effectiveFrom <= :today)"
            + " AND (k.effectiveTo IS NULL OR k.effectiveTo >= :today)"
            + " ORDER BY COALESCE(k.sourceDate, k.createdAt) DESC, k.knowledgeId DESC")
    Page<NewstartKnowledgeItem> findFeedByCategory(@Param("status") KnowledgeStatus status,
                                                   @Param("category") String category,
                                                   @Param("sourceTypes") java.util.Collection<SourceType> sourceTypes,
                                                   @Param("today") java.time.LocalDate today,
                                                   Pageable pageable);
}