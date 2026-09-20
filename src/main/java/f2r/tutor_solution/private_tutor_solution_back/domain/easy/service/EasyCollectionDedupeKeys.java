package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

/**
   * 수집 dedupe 키 구성 — 3단계 중 1·2단계를 결정한다.
   * 1차: provider 고유 ID + connection → {source}:{businessId}:{connectionId}:EXT:{normalizedId}
   *     (connectionId 포함으로 서로 다른 연결 간 false merge 방지)
   * 2차: 내용 튜플 + 파일 내 발생 순번 → {source}:{businessId}:T:{tupleHash16}:{seq}
   *     (순번은 '같은 파일 안에서 이 튜플이 몇 번째 등장인지' — 행 순서·파일명 변경에 무관하고,
   *      동일 내용 재수집 시 동일 키가 되어 중복 제외, 서로 다른 실제 거래의 우연한 충돌은
   *      순번이 달라 개별 수용된다.)
   * 3차(장부 fingerprint)는 EasyCollectionIngestService가 담당.
   */
  public final class EasyCollectionDedupeKeys {

    private EasyCollectionDedupeKeys() {
    }

    public static String external(String sourceCode, Long businessId, Long connectionId, String externalId) {
      return sourceCode + ":" + businessId + ":" + connectionId + ":EXT:" + normalizeText(externalId);
    }

  public static String tupleHash(String sourceCode, Long businessId, LocalDate occurredOn,
      String direction, Long amount, String counterparty, String description, String payMethod) {
    String tuple = String.join("|",
        sourceCode,
        String.valueOf(businessId),
        occurredOn == null ? "" : occurredOn.toString(),
        direction == null ? "" : direction.trim().toUpperCase(),
        amount == null ? "" : String.valueOf(amount),
        normalizeText(counterparty),
        normalizeText(description),
        normalizeText(payMethod));
    return sha256Hex(tuple);
  }

  public static String tuple(String sourceCode, Long businessId, String tupleHash, int seq) {
    return sourceCode + ":" + businessId + ":T:" + tupleHash.substring(0, 16) + ":" + seq;
  }

  public static String rawHash(String payload) {
    return sha256Hex(payload);
  }

  /** NFKC 정규화(전각/반각 통일) + trim + 연속 공백 하나로 + 소문자. */
  public static String normalizeText(String value) {
    if (value == null) {
      return "";
    }
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .trim()
        .replaceAll("\\s+", " ")
        .toLowerCase();
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
    }
  }
}
