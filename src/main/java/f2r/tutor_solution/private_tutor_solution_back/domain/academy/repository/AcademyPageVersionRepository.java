package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** BuildIt 홈페이지 버전 이력 Repository (P1). */
public interface AcademyPageVersionRepository extends JpaRepository<AcademyPageVersion, Long> {

  List<AcademyPageVersion> findByAcademyIdOrderByVersionNoDesc(Long academyId);

  Optional<AcademyPageVersion> findTopByAcademyIdOrderByVersionNoDesc(Long academyId);

  @Query(
      "SELECT COUNT(v) FROM AcademyPageVersion v WHERE v.academyId = :academyId")
  long countByAcademyId(Long academyId);

  void deleteByAcademyIdAndVersionNoLessThan(Long academyId, Integer versionNo);
}
