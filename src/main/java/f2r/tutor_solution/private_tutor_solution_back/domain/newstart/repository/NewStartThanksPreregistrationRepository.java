package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksPreregistration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartThanksPreregistrationRepository extends JpaRepository<NewStartThanksPreregistration, Long> {

  Optional<NewStartThanksPreregistration> findByEmail(String email);

  Optional<NewStartThanksPreregistration> findByLinkedUserId(Long linkedUserId);

  boolean existsByCafeMemberTag(String cafeMemberTag);

  boolean existsByCafeNickname(String cafeNickname);

  List<NewStartThanksPreregistration> findAllByOrderByPreregIdAsc();
}
