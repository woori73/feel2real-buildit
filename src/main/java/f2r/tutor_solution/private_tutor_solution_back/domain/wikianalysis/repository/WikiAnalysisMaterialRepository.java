package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiAnalysisMaterialRepository extends JpaRepository<WikiAnalysisMaterial, Long> {

  @Query("""
      select w from WikiAnalysisMaterial w
      where w.deleted = false and w.visible = true
        and (:grade is null or w.grade = :grade)
      order by w.createdAt desc, w.wikiAnalysisMaterialId desc
      """)
  List<WikiAnalysisMaterial> findPublicByGrade(@Param("grade") Integer grade);

  @Query("""
      select w from WikiAnalysisMaterial w
      where w.deleted = false
      order by w.createdAt desc, w.wikiAnalysisMaterialId desc
      """)
  List<WikiAnalysisMaterial> findAdminList();

  @Query("""
      select w from WikiAnalysisMaterial w
      where w.wikiAnalysisMaterialId = :id and w.deleted = false and w.visible = true
      """)
  Optional<WikiAnalysisMaterial> findPublicById(@Param("id") Long id);

  @Query("""
      select w from WikiAnalysisMaterial w
      where w.wikiAnalysisMaterialId = :id and w.deleted = false
      """)
  Optional<WikiAnalysisMaterial> findAdminById(@Param("id") Long id);

  /** 공개 Wiki 메인 "NEW 최신 업데이트" — 학년 무관 최신 공개 분석 자료. */
  List<WikiAnalysisMaterial> findTop8ByDeletedFalseAndVisibleTrueOrderByCreatedAtDesc();
}
