package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GrammarQuestionProfileRepository
    extends JpaRepository<GrammarQuestionProfile, Long>,
    JpaSpecificationExecutor<GrammarQuestionProfile> {

  Optional<GrammarQuestionProfile> findByQuestion_QuestionId(Long questionId);

  Optional<GrammarQuestionProfile> findByContentHash(String contentHash);

  Optional<GrammarQuestionProfile> findByMaterial_MaterialIdAndExternalRef(Long materialId, String externalRef);

  Optional<GrammarQuestionProfile> findByMaterial_MaterialIdAndSourcePosition(Long materialId, String sourcePosition);

  long countByReviewStatus(f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarReviewStatus reviewStatus);

  long countByMaterial_MaterialId(Long materialId);

  @Query("SELECT p.schoolLevel, COUNT(p) FROM GrammarQuestionProfile p GROUP BY p.schoolLevel")
  List<Object[]> countGroupBySchoolLevel();

  @Query("""
      SELECT t.tagId, t.nameKo, COUNT(gt.id)
      FROM GrammarTag t
      LEFT JOIN QuestionGrammarTag gt ON gt.tag = t
      GROUP BY t.tagId, t.nameKo
      ORDER BY t.sortOrder ASC
      """)
  List<Object[]> countGroupByTag();
}
