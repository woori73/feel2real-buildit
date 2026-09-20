package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectionConnectionRepository
    extends JpaRepository<EasyCollectionConnection, Long> {

  Optional<EasyCollectionConnection> findByBusiness_BusinessIdAndSource_SourceIdAndLabel(
      Long businessId, Long sourceId, String label);

  List<EasyCollectionConnection> findAllByBusiness_BusinessIdOrderByConnectionIdAsc(Long businessId);

  List<EasyCollectionConnection> findBySource_SourceIdAndStatus(Long sourceId, EasyCollectionConnectionStatus status);

  List<EasyCollectionConnection> findByBusiness_BusinessId(Long businessId);
}
