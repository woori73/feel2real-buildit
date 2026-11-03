package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdmissionsKnowledgeEmbeddingRepository extends JpaRepository<AdmissionsKnowledgeEmbedding, Long> {
    List<AdmissionsKnowledgeEmbedding> findByModelVersion(String modelVersion);

    /** 검색 인덱스용: 지난해 입시결과 항목("prior:")의 임베딩은 상담 검색에 쓰이지 않으므로 메모리에 올리지 않는다. */
    @Query("select e from AdmissionsKnowledgeEmbedding e join e.knowledge k where e.modelVersion = :modelVersion"
            + " and (k.dedupKey is null or k.dedupKey not like 'prior:%')")
    List<AdmissionsKnowledgeEmbedding> findSearchableByModelVersion(@Param("modelVersion") String modelVersion);
    Optional<AdmissionsKnowledgeEmbedding> findByKnowledge_KnowledgeIdAndModelVersion(Long knowledgeId, String modelVersion);

    /** 벡터(LONGBLOB)를 읽지 않고 임베딩이 있는 knowledge_id만 조회 — 진행률 폴링용. */
    @Query("select e.knowledge.knowledgeId from AdmissionsKnowledgeEmbedding e where e.modelVersion = :modelVersion")
    List<Long> findKnowledgeIdsByModelVersion(@Param("modelVersion") String modelVersion);
}
