package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyEconomicSale;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyEconomicSaleRepository extends JpaRepository<EasyEconomicSale, Long> {
  Optional<EasyEconomicSale> findByConnection_ConnectionIdAndEconomicKey(
      Long connectionId, String economicKey);
}
