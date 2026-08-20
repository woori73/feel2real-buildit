package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByLoginId(String loginId);

  Optional<User> findByLoginIdAndEmail(String loginId, String email);

  List<User> findAllByEmail(String email);

  boolean existsByLoginId(String loginId);
}
