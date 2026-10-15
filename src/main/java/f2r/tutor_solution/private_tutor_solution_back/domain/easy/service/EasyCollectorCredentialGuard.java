package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The collector agent must never report a credential to EASY — only status transitions and,
 * on completion, file metadata (name/size/hash). This rejects any agent-reported payload that
 * carries a field shaped like a credential, instead of silently ignoring it.
 */
public final class EasyCollectorCredentialGuard {

  private static final Set<String> FORBIDDEN_KEYS = Set.of(
      "password", "pwd", "passwd", "otp", "cvc", "cvv", "pin",
      "privatekey", "certpassword", "certificatepassword", "secret",
      "cardpassword", "fullcardnumber", "cardnumber", "pan");

  private EasyCollectorCredentialGuard() {
  }

  public static void reject(Map<String, ?> body) {
    if (body == null) {
      return;
    }
    for (String key : body.keySet()) {
      String normalized = key.toLowerCase(Locale.ROOT).replace("_", "");
      if (FORBIDDEN_KEYS.contains(normalized)) {
        throw new IllegalArgumentException("Field not allowed on this endpoint: " + key);
      }
    }
  }
}
