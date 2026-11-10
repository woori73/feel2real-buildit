package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Explicit provider verification allow-list. A source becomes READY only after an official
 * contract, credentials and a live verification are completed and an operator lists its code in
 * {@code easy.collection.verified-sources} (comma separated). The default is empty, so no provider
 * is ever shown as connected or collected automatically before that decision.
 */
@Component
public class EasyCollectionProviderReadinessPolicy {

  private final Set<String> verifiedSources;

  public EasyCollectionProviderReadinessPolicy() {
    this("");
  }

  @org.springframework.beans.factory.annotation.Autowired
  public EasyCollectionProviderReadinessPolicy(
      @Value("${easy.collection.verified-sources:}") String verifiedSources) {
    this.verifiedSources = verifiedSources == null ? Set.of()
        : Arrays.stream(verifiedSources.split(","))
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .map(code -> code.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
  }

  public boolean isVerified(EasyCollectionSource source) {
    return source != null && source.getCode() != null
        && verifiedSources.contains(source.getCode().toUpperCase(Locale.ROOT));
  }
}
