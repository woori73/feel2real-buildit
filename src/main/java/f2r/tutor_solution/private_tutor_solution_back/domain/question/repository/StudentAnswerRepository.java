package f2r.tutor_solution.private_tutor_solution_back.domain.question.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.StudentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

  @Query("""
      SELECT sa FROM StudentAnswer sa
      JOIN sa.question q
      WHERE sa.student.studentId = :studentId
      AND q.category IS NOT NULL
      """)
  List<StudentAnswer> findByStudentIdWithCategory(Long studentId);

  /**
   * 대시보드 radar용 — 특정 학생이 특정 시험(들)에서 푼 답안 중 카테고리가 매핑된 것만.
   * questions JOIN FETCH로 N+1 회피 (집계 시 question.getCategory() 호출 직후 LAZY 해제).
   */
  @Query("""
      SELECT sa FROM StudentAnswer sa
      JOIN FETCH sa.question q
      JOIN FETCH q.category
      WHERE sa.student.studentId = :studentId
      AND sa.examId IN :examIds
      AND q.category IS NOT NULL
      """)
  List<StudentAnswer> findForRadar(
      @Param("studentId") Long studentId,
      @Param("examIds") List<Long> examIds);

  /** 자료 삭제 시 cascade — 해당 학생의 해당 시험에 대한 답안 전부 제거. */
  void deleteByStudent_StudentIdAndExamId(Long studentId, Long examId);

  /**
   * 오답노트용 — 특정 학생이 특정 시험(들)에서 틀린 답안만. question + category까지 FETCH.
   * order_num 기준 정렬로 시험지 순서대로 노출 가능.
   */
  @Query("""
      SELECT sa FROM StudentAnswer sa
      JOIN FETCH sa.question q
      LEFT JOIN FETCH q.category
      WHERE sa.student.studentId = :studentId
      AND sa.examId IN :examIds
      AND sa.isCorrect = false
      ORDER BY sa.examId, q.orderNum ASC
      """)
  List<StudentAnswer> findWrongAnswersForExams(
      @Param("studentId") Long studentId,
      @Param("examIds") List<Long> examIds);

  /** 오답노트 헤더용 — 시험별 학생 답안 총 개수 (오답 + 정답 합산). 반환: [examId, count]. */
  @Query("""
      SELECT sa.examId, COUNT(sa) FROM StudentAnswer sa
      WHERE sa.student.studentId = :studentId
      AND sa.examId IN :examIds
      GROUP BY sa.examId
      """)
  List<Object[]> countByStudentAndExams(
      @Param("studentId") Long studentId,
      @Param("examIds") List<Long> examIds);
}
