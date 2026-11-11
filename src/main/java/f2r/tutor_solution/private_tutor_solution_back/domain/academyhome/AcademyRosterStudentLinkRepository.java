package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyRosterStudentLinkRepository extends JpaRepository<AcademyRosterStudentLink, Long> {
  List<AcademyRosterStudentLink> findByAcademyIdAndRosterYear(Long academyId, Integer rosterYear);
  Optional<AcademyRosterStudentLink> findByAcademyIdAndRosterYearAndRosterName(Long academyId, Integer rosterYear, String rosterName);
}
