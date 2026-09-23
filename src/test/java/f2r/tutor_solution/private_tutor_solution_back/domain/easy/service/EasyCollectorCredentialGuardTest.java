package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EasyCollectorCredentialGuardTest {

  @Test
  void allowsOrdinaryMetadataFields() {
    assertThatCode(() -> EasyCollectorCredentialGuard.reject(
        Map.of("filename", "x.csv", "size", 10, "sha256", "a".repeat(64))))
        .doesNotThrowAnyException();
  }

  @Test
  void allowsNullBody() {
    assertThatCode(() -> EasyCollectorCredentialGuard.reject(null)).doesNotThrowAnyException();
  }

  @Test
  void rejectsPasswordField() {
    assertThatThrownBy(() -> EasyCollectorCredentialGuard.reject(Map.of("password", "hunter2")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsCommonCredentialShapedKeysCaseAndUnderscoreInsensitively() {
    for (String key : new String[] {
        "otp", "OTP", "cvc", "CVV", "pin", "certPassword", "certificate_password",
        "privateKey", "cardPassword", "fullCardNumber", "cardNumber", "pan", "secret"}) {
      assertThatThrownBy(() -> EasyCollectorCredentialGuard.reject(Map.of(key, "x")))
          .as("key=%s", key)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void doesNotFlagAFilenameThatMerelyMentionsACredentialWord() {
    // the guard checks field NAMES, not values or substrings of the filename/reason content.
    assertThatCode(() -> EasyCollectorCredentialGuard.reject(
        Map.of("filename", "password_policy_notice.csv", "size", 1, "sha256", "a".repeat(64))))
        .doesNotThrowAnyException();
  }
}
