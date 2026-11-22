package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TpQuestionRepository extends JpaRepository<TpQuestion, Long> {

  List<TpQuestion> findByExamIdOrderByOrderNoAsc(Long examId);

  List<TpQuestion> findByExamIdAndQuestionIdInOrderByOrderNoAsc(Long examId, Collection<Long> questionIds);

  List<TpQuestion> findByExamIdInOrderByExamIdAscOrderNoAsc(Collection<Long> examIds);
  long countByExamId(Long examId);

  /** 시험마다 문항 수: [examId, count]. 목록 화면이 시험 수만큼 count 질의를 하지 않게 한 번에 센다. */
  @Query("select q.examId, count(q) from TpQuestion q where q.examId in :examIds group by q.examId")
  List<Object[]> countByExamIds(@Param("examIds") Collection<Long> examIds);

  /** 파트·유형 묶음용 가벼운 조회: [examId, taskType, 해설 앞 80자, orderNo]. 지문·선지는 읽지 않는다. */
  @Query("select q.examId, q.taskType, substring(q.explanation, 1, 80), q.orderNo from TpQuestion q where q.examId in :examIds")
  List<Object[]> findFocusRows(@Param("examIds") Collection<Long> examIds);
}
