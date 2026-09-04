package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiLearningMaterialRepository extends JpaRepository<WikiLearningMaterial, Long> {

  @Query("""
      select m from WikiLearningMaterial m
      where m.deleted = false
        and (:grade is null or m.grade = :grade)
      order by m.createdAt desc, m.wikiLearningMaterialId desc
      """)
  List<WikiLearningMaterial> findPublicByGrade(@Param("grade") Integer grade);

  @Query("""
      select m from WikiLearningMaterial m
      where m.deleted = false
      order by m.createdAt desc, m.wikiLearningMaterialId desc
      """)
  List<WikiLearningMaterial> findAdminList();

  @Query("""
      select m from WikiLearningMaterial m
      where m.wikiLearningMaterialId = :id and m.deleted = false
      """)
  Optional<WikiLearningMaterial> findActiveById(@Param("id") Long id);
}
