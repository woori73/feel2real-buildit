package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemGeneratorMaterialRepository extends JpaRepository<ProblemGeneratorMaterial, Long> {
  @Query("""
      select m from ProblemGeneratorMaterial m
      where m.visibility = f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility.PUBLIC
         or m.uploadedBy.userId = :userId
      order by m.createdAt desc, m.materialId desc
      """)
  Page<ProblemGeneratorMaterial> findAccessible(@Param("userId") Long userId, Pageable pageable);
  long countByVisibility(MaterialVisibility visibility);
}
