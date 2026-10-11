package f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {

  /** BuildIt AI 생성용: 한글 과목명으로 조회 (A1). */
  java.util.Optional<Subject> findByNameKo(String nameKo);
}
