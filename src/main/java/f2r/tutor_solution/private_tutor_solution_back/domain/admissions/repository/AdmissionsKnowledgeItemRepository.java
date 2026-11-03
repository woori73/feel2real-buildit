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

    /**
     * 본문을 읽지 않고 상태별 id만 조회(오름차순) — 재색인 대상 계산·진행률용.
     * 지난해 입시결과 항목(dedupKey "prior:")은 상담 검색에 쓰이지 않으므로(대학 단위 DB 조회로 답함) 임베딩 대상에서 뺀다.
     */
    @Query("select k.knowledgeId from AdmissionsKnowledgeItem k where k.status = :status"
            + " and (k.dedupKey is null or k.dedupKey not like 'prior:%') order by k.knowledgeId")
    List<Long> findIdsByStatus(@Param("status") AdmissionsKnowledgeItem.KnowledgeStatus status);

    @Query("select k.knowledgeId from AdmissionsKnowledgeItem k"
            + " where k.dedupKey is null or k.dedupKey not like 'prior:%' order by k.knowledgeId")
    List<Long> findAllIds();
}
