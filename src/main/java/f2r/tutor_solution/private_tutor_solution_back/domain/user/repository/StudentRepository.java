package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

  /** users.user_id 로 학생 본인 조회 (학생 직접 로그인 후 본인 프로필 fetch 용) */
  Optional<Student> findByUser_UserId(Long userId);
}
