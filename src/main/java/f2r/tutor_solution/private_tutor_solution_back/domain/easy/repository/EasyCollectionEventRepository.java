package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionEvent;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectionEventRepository extends JpaRepository<EasyCollectionEvent, Long> {
  Optional<EasyCollectionEvent> findByConnection_ConnectionIdAndEventKey(
      Long connectionId, String eventKey);
  Optional<EasyCollectionEvent> findFirstByConnection_ConnectionIdAndExternalEventIdOrderByEventIdAsc(
      Long connectionId, String externalEventId);
  List<EasyCollectionEvent> findAllByEconomicSale_SaleIdOrderByEventIdAsc(Long saleId);
}
