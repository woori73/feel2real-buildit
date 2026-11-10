package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyClassMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyClassMemberRepository extends JpaRepository<EasyClassMember, Long> {
  List<EasyClassMember> findAllByEasyClass_ClassIdOrderByMemberIdAsc(Long classId);

  List<EasyClassMember> findAllByEasyClass_ClassIdAndReleasedOnIsNull(Long classId);

  List<EasyClassMember> findAllByStudent_StudentIdAndReleasedOnIsNull(Long studentId);
}
