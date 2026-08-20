package f2r.tutor_solution.private_tutor_solution_back.domain.score.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.score.entity.StudentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {

  /**
   * 대시보드 성적 변화 추이용 — 학생의 모든 시험 점수.
   * exam을 함께 fetch해서 displayName / examDate / examType 즉시 사용 가능.
   *
   * <p>정렬은 ScoreTimelineService에서 수행한다
   * (examYear → examSemester → examType ordinal → confirmedAt).
   * examType이 EnumType.STRING이라 DB ORDER BY로는 알파벳 정렬이 되어 의도와 어긋남.
   */
  @Query("""
      SELECT ss FROM StudentScore ss
      JOIN FETCH ss.exam
      WHERE ss.student.studentId = :studentId
      """)
  List<StudentScore> findTimelineByStudent(@Param("studentId") Long studentId);

  /**
   * conversation 대시보드용 — 해당 conversation에 직접 첨부된 source에서 확정된 점수만 조회한다.
   * 같은 학생/같은 exam이라도 다른 conversation에 첨부된 source의 점수는 섞지 않는다.
   */
  @Query("""
      SELECT ss FROM StudentScore ss
      JOIN FETCH ss.exam
      WHERE ss.student.studentId = :studentId
        AND ss.source.sourceId IN :sourceIds
      """)
  List<StudentScore> findTimelineByStudentAndSources(
      @Param("studentId") Long studentId,
      @Param("sourceIds") List<Long> sourceIds);

  /** 자료 삭제 시 cascade — 같은 source로 확정된 점수 row 제거. */
  void deleteBySource_SourceId(Long sourceId);
}
