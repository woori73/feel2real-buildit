package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

  Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

  Optional<SocialAccount> findFirstByUserIdAndProvider(Long userId, SocialProvider provider);
}
