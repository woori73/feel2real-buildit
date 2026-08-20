package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.TutorStudentMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TutorStudentMapRepository extends JpaRepository<TutorStudentMap, Long> {

  boolean existsByTutor_UserIdAndStudent_StudentIdAndActiveTrue(Long tutorUserId, Long studentId);

  Optional<TutorStudentMap> findByTutor_UserIdAndStudent_StudentId(Long tutorUserId, Long studentId);
}
