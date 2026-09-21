package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewstartKnowledgeRelationRepository extends JpaRepository<NewstartKnowledgeRelation, Long> {

    @Query("SELECT r FROM NewstartKnowledgeRelation r WHERE r.fromKnowledgeId = :id")
    List<NewstartKnowledgeRelation> findByFromKnowledgeId(@Param("id") Long id);

    @Query("SELECT r FROM NewstartKnowledgeRelation r WHERE r.toKnowledgeId = :id")
    List<NewstartKnowledgeRelation> findByToKnowledgeId(@Param("id") Long id);

    @Query("SELECT r FROM NewstartKnowledgeRelation r WHERE r.fromKnowledgeId = :fromId AND r.toKnowledgeId = :toId")
    List<NewstartKnowledgeRelation> findByFromAndTo(@Param("fromId") Long fromId, @Param("toId") Long toId);
}