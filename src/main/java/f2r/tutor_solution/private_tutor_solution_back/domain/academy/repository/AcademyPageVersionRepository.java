package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** BuildIt 홈페이지 버전 이력 Repository (P1). */
public interface AcademyPageVersionRepository extends JpaRepository<AcademyPageVersion, Long> {

  List<AcademyPageVersion> findByAcademyIdOrderByVersionNoDesc(Long academyId);

  Optional<AcademyPageVersion> findTopByAcademyIdOrderByVersionNoDesc(Long academyId);

  /**
   * D3: 스냅샷 생성 시 최신 version_no를 정확히 읽기 위한 잠금 조회.
   *
   * <p>REPEATABLE READ에서 일반 SELECT는 트랜잭션 시작 시점의 스냅샷을 사용하므로
   * 동시 스냅샷 요청이 같은 version_no를 계산하는 원인이 된다.
   * 잠금 읽기(SELECT ... FOR UPDATE)는 항상 최신 커밋 데이터를 보므로
   * 이 문제를 해결한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from AcademyPageVersion v where v.academyId = :academyId order by v.versionNo desc limit 1")
  Optional<AcademyPageVersion> findTopForUpdateByAcademyId(Long academyId);

  @Query(
      "SELECT COUNT(v) FROM AcademyPageVersion v WHERE v.academyId = :academyId")
  long countByAcademyId(Long academyId);

  void deleteByAcademyIdAndVersionNoLessThan(Long academyId, Integer versionNo);
}
