package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdmissionsKnowledgeItemRepository extends JpaRepository<AdmissionsKnowledgeItem, Long>,
        JpaSpecificationExecutor<AdmissionsKnowledgeItem> {
    Optional<AdmissionsKnowledgeItem> findByDedupKey(String dedupKey);

    /** 본문을 읽지 않고 상태별 id만 조회(오름차순) — 재색인 대상 계산·진행률용. */
    @Query("select k.knowledgeId from AdmissionsKnowledgeItem k where k.status = :status order by k.knowledgeId")
    List<Long> findIdsByStatus(@Param("status") AdmissionsKnowledgeItem.KnowledgeStatus status);

    @Query("select k.knowledgeId from AdmissionsKnowledgeItem k order by k.knowledgeId")
    List<Long> findAllIds();
}
