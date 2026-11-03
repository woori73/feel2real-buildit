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

    /**
     * 임베딩 대상 id(오름차순): 지난해 입시결과({@code prior:}) 항목은 뺀다 — 상담은 입시결과를 원본 행에서 직접 근거로 만들고
     * 이 항목들을 검색에 쓰지 않으므로, 임베딩하면 시간·메모리만 든다.
     */
    @Query("select k.knowledgeId from AdmissionsKnowledgeItem k where k.status = :status"
            + " and (k.dedupKey is null or k.dedupKey not like 'prior:%') order by k.knowledgeId")
    List<Long> findEmbeddableIdsByStatus(@Param("status") AdmissionsKnowledgeItem.KnowledgeStatus status);

    @Query("select k.knowledgeId from AdmissionsKnowledgeItem k"
            + " where k.dedupKey is null or k.dedupKey not like 'prior:%' order by k.knowledgeId")
    List<Long> findAllEmbeddableIds();

    /** 승격 후보(본문을 읽지 않음): [id, dedupKey, sourceClass, createdBy]. */
    @Query("select k.knowledgeId, k.dedupKey, k.sourceClass, k.createdBy from AdmissionsKnowledgeItem k"
            + " where k.academicYear = :year and k.status in :statuses")
    List<Object[]> findPromotionCandidates(@Param("year") Integer year,
                                           @Param("statuses") java.util.Collection<AdmissionsKnowledgeItem.KnowledgeStatus> statuses);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AdmissionsKnowledgeItem k set k.status = :status where k.knowledgeId in :ids")
    int updateStatusByIds(@Param("ids") java.util.Collection<Long> ids,
                          @Param("status") AdmissionsKnowledgeItem.KnowledgeStatus status);
}
