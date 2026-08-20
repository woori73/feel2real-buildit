package f2r.tutor_solution.private_tutor_solution_back.domain.exam.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

  /**
   * MVP §5.0 4단계 dedup — UNIQUE 키 매칭.
   * <p>{@code exam_year} / {@code exam_semester}는 nullable이지만 dedup 후보가 모호해지지 않도록
   * 호출자(ExamExtractionService)에서 모든 메타가 채워진 경우에만 호출한다.
   */
  @Query("""
      SELECT e FROM Exam e
       WHERE e.schoolName  = :schoolName
         AND e.grade       = :grade
         AND e.examType    = :examType
         AND e.examYear    = :examYear
         AND e.examSemester = :examSemester
         AND e.subject.subjectId = :subjectId
      """)
  Optional<Exam> findByUniqueKey(
      String schoolName,
      Byte grade,
      ExamType examType,
      Short examYear,
      Byte examSemester,
      Integer subjectId);
}
