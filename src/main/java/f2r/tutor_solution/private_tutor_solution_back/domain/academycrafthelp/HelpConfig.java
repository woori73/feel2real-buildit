package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HelpConfig {

  @Bean
  HelpAssistant academyCraftHelpAssistant(HelpCatalog catalog) {
    return new HelpAssistant(catalog);
  }

  @Bean
  HelpRateLimiter academyCraftHelpRateLimiter() {
    return new HelpRateLimiter(20, 60_000);
  }
}
