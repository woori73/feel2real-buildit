package f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnsAccountRepository extends JpaRepository<SnsAccount, Long> {}
