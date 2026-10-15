package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectionCheckpointRepository
    extends JpaRepository<EasyCollectionCheckpoint, Long> {
  Optional<EasyCollectionCheckpoint> findByConnection_ConnectionIdAndDatasetType(
      Long connectionId, EasyCollectionDatasetType datasetType);

  List<EasyCollectionCheckpoint> findAllByConnection_ConnectionIdOrderByDatasetTypeAsc(
      Long connectionId);

  boolean existsByConnection_ConnectionId(Long connectionId);
}
