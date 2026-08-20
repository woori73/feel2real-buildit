package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorQuestionSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemGeneratorQuestionSetRepository extends JpaRepository<ProblemGeneratorQuestionSet, Long> {
  @Query("""
      select s from ProblemGeneratorQuestionSet s
      where s.visibility = f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility.PUBLIC
         or s.owner.userId = :userId
      order by s.createdAt desc, s.setId desc
      """)
  Page<ProblemGeneratorQuestionSet> findAccessible(@Param("userId") Long userId, Pageable pageable);
}
