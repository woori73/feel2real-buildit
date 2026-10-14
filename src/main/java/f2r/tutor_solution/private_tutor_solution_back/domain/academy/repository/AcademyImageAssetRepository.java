package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademyImageAssetRepository extends JpaRepository<AcademyImageAsset, Long> {

  /** 학원의 특정 슬롯 이미지 목록 (버전 내림차순). */
  List<AcademyImageAsset> findByAcademyIdAndSlotKeyOrderByVersionDesc(Long academyId, String slotKey);

  /** 학원의 특정 슬롯 최신 버전. */
  Optional<AcademyImageAsset> findFirstByAcademyIdAndSlotKeyOrderByVersionDesc(Long academyId, String slotKey);

  /** 학원의 전체 이미지 목록. */
  List<AcademyImageAsset> findByAcademyIdOrderBySlotKeyAscVersionDesc(Long academyId);
}
