package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiExamOriginalRepository extends JpaRepository<WikiExamOriginal, Long> {

  @Query("""
      select w from WikiExamOriginal w
      where w.deleted = false and w.visible = true
        and (:grade is null or w.grade = :grade)
      order by w.createdAt desc, w.wikiExamOriginalId desc
      """)
  List<WikiExamOriginal> findPublicByGrade(@Param("grade") Integer grade);

  @Query("""
      select w from WikiExamOriginal w
      where w.deleted = false
      order by w.createdAt desc, w.wikiExamOriginalId desc
      """)
  List<WikiExamOriginal> findAdminList();

  @Query("""
      select w from WikiExamOriginal w
      where w.wikiExamOriginalId = :id and w.deleted = false and w.visible = true
      """)
  Optional<WikiExamOriginal> findPublicById(@Param("id") Long id);

  @Query("""
      select w from WikiExamOriginal w
      where w.wikiExamOriginalId = :id and w.deleted = false
      """)
  Optional<WikiExamOriginal> findAdminById(@Param("id") Long id);
}
