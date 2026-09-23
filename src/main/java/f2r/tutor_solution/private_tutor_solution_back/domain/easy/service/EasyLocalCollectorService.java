package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyLocalCollectorRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visible Local Browser Collector — agent registration/pairing. EASY stores only a SHA-256 of
 * the pairing token; the raw token is returned exactly once, at registration.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyLocalCollectorService {

  private final EasyBusinessRepository businessRepository;
  private final EasyLocalCollectorRepository collectorRepository;

  public record RegisterResult(EasyLocalCollector collector, String rawToken) {}

  @Transactional
  public RegisterResult register(Long businessId, String name) {
    EasyBusiness business = businessRepository.findById(requireBusinessId(businessId))
        .orElseThrow(() -> new IllegalArgumentException("Business not found: " + businessId));
    String rawToken = generateToken();
    EasyLocalCollector collector = collectorRepository.save(EasyLocalCollector.builder()
        .business(business).name(name).tokenHash(sha256(rawToken)).build());
    return new RegisterResult(collector, rawToken);
  }

  public List<EasyLocalCollector> list(Long businessId) {
    return collectorRepository.findAllByBusiness_BusinessIdOrderByCollectorIdDesc(
        requireBusinessId(businessId));
  }

  /** Used by {@code EasyCollectorAgentTokenFilter}. Empty for an unknown or revoked token. */
  public Optional<EasyLocalCollector> authenticate(String presentedToken) {
    if (presentedToken == null || presentedToken.isBlank()) {
      return Optional.empty();
    }
    return collectorRepository.findByTokenHash(sha256(presentedToken))
        .filter(EasyLocalCollector::isActive);
  }

  @Transactional
  public void heartbeat(Long collectorId) {
    EasyLocalCollector collector = collectorRepository.findById(collectorId)
        .orElseThrow(() -> new IllegalArgumentException("Collector not found: " + collectorId));
    collector.heartbeat();
  }

  private Long requireBusinessId(Long businessId) {
    if (businessId == null) throw new IllegalArgumentException("businessId is required.");
    return businessId;
  }

  private static String generateToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 is unavailable.", e);
    }
  }
}
