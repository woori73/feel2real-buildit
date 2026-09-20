package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectionSourceRepository extends JpaRepository<EasyCollectionSource, Long> {
  Optional<EasyCollectionSource> findByCode(String code);
}
