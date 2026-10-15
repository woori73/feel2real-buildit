package f2r.tutor_solution.private_tutor_solution_back.domain.landing.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LandingVideoSlotRepository
    extends JpaRepository<LandingVideoSlot, LandingVideoSlotKey> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select slot from LandingVideoSlot slot where slot.slot = :slot")
  Optional<LandingVideoSlot> findBySlotForUpdate(@Param("slot") LandingVideoSlotKey slot);
}
