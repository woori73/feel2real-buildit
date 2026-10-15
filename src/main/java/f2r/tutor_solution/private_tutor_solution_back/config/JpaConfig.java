package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaConfig implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // TODO: JWT 인증 추가 시 SecurityContext에서 loginId 추출로 교체
        return Optional.ofNullable(AuditorContextHolder.get());
    }
}
