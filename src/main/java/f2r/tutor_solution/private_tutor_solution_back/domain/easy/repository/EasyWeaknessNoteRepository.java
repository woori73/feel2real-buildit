package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyWeaknessNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyWeaknessNoteRepository extends JpaRepository<EasyWeaknessNote, Long> {
  List<EasyWeaknessNote> findTop10ByBusiness_BusinessIdAndStudent_StudentIdOrderByNoteIdDesc(Long businessId, Long studentId);
}
