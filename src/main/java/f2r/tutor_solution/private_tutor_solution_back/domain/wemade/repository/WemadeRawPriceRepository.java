package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity.WemadeRawPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WemadeRawPriceRepository extends JpaRepository<WemadeRawPrice, java.time.LocalDate> {
    List<WemadeRawPrice> findAllByOrderByTradeDateAsc();
        boolean existsByContentHash(String hash);
}
