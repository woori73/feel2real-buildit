package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewstartKnowledgeStatusLogRepository extends JpaRepository<NewstartKnowledgeStatusLog, Long> {

    @Query("SELECT l FROM NewstartKnowledgeStatusLog l WHERE l.knowledgeId = :id ORDER BY l.createdAt DESC")
    List<NewstartKnowledgeStatusLog> findByKnowledgeIdOrderByCreatedAtDesc(@Param("id") Long id);
}