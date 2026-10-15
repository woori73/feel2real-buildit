package f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.examanalysis.entity.ExamAnalysis;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamAnalysisRepository extends JpaRepository<ExamAnalysis, Long> {

  @Query("""
      select e from ExamAnalysis e
      join fetch e.subject s
      where e.deleted = false
      order by e.createdAt desc, e.examAnalysisId desc
      """)
  List<ExamAnalysis> findAdminList();

  @Query("""
      select e from ExamAnalysis e
      join fetch e.subject s
      where e.deleted = false and e.visible = true
        and (:keyword is null or lower(e.title) like lower(concat('%', :keyword, '%'))
          or lower(e.schoolName) like lower(concat('%', :keyword, '%')))
        and (:schoolName is null or lower(e.schoolName) like lower(concat('%', :schoolName, '%')))
        and (:grade is null or e.grade = :grade)
        and (:semester is null or e.semester = :semester)
        and (:examType is null or e.examType = :examType)
        and (:subjectId is null or s.subjectId = :subjectId)
      order by e.createdAt desc, e.examAnalysisId desc
      """)
  List<ExamAnalysis> searchPublic(
      @Param("keyword") String keyword,
      @Param("schoolName") String schoolName,
      @Param("grade") Integer grade,
      @Param("semester") String semester,
      @Param("examType") String examType,
      @Param("subjectId") Integer subjectId);

  @Query("""
      select e from ExamAnalysis e
      join fetch e.subject s
      where e.deleted = false and e.visible = true
      order by e.createdAt desc, e.examAnalysisId desc
      """)
  List<ExamAnalysis> findLatestPublic(Pageable pageable);

  @Query("""
      select e from ExamAnalysis e
      join fetch e.subject s
      where e.examAnalysisId = :id and e.deleted = false and e.visible = true
      """)
  Optional<ExamAnalysis> findPublicById(@Param("id") Long id);

  @Query("""
      select e from ExamAnalysis e
      join fetch e.subject s
      where e.examAnalysisId = :id and e.deleted = false
      """)
  Optional<ExamAnalysis> findAdminById(@Param("id") Long id);
}
