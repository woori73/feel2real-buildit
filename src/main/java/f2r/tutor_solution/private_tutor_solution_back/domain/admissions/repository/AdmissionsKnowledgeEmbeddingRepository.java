package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdmissionsKnowledgeEmbeddingRepository extends JpaRepository<AdmissionsKnowledgeEmbedding, Long> {
    List<AdmissionsKnowledgeEmbedding> findByModelVersion(String modelVersion);
    Optional<AdmissionsKnowledgeEmbedding> findByKnowledge_KnowledgeIdAndModelVersion(Long knowledgeId, String modelVersion);
}
