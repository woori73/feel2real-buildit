package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Easy 중복 의심 탐지용 fingerprint — SHA-256 해시만 만들고 판단하지 않는다.
 * 같은 값이 나와도 자동합치기는 하지 않는다 (의심 목록에만 올린다).
 * 발상 참고: kimlawtech/korean-jangbu-for (Apache-2.0)의 source/source_ref —
 * 코드 복사 없이 재구현.
 */
public final class EasyFingerprint {

  private EasyFingerprint() {
  }

  public static String of(Long businessId, java.time.LocalDate occurredOn, String direction,
      long amount, String counterparty, String description, String payMethod) {
    String normalized = String.join("|",
        String.valueOf(businessId),
        occurredOn == null ? "" : occurredOn.toString(),
        direction == null ? "" : direction.trim().toUpperCase(),
        String.valueOf(amount),
        normalize(counterparty),
        normalize(description),
        normalize(payMethod));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
    }
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().replaceAll("\\s+", " ");
  }
}
