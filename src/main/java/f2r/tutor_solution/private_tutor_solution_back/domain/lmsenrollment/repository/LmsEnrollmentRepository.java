package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsEnrollment;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsEnrollmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsEnrollmentRepository extends JpaRepository<LmsEnrollment, Long> {

  Optional<LmsEnrollment> findByStudent_StudentIdAndLessonProduct_LessonId(
      Long studentId, Long lessonId);

  List<LmsEnrollment> findByLessonProduct_LessonIdOrderByGrantedAtDesc(Long lessonId);

  List<LmsEnrollment> findByStudent_StudentIdAndAcademy_AcademyIdAndStatus(
      Long studentId, Long academyId, LmsEnrollmentStatus status);
}
