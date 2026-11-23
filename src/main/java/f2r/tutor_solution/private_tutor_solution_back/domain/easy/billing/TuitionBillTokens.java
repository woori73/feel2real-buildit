package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 청구 링크 토큰·주문번호 생성과 해시. 토큰 원문은 만든 직후 한 번만 보여 주고 DB에는 해시만 둔다. */
public final class TuitionBillTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private TuitionBillTokens() {}

  /** 추측할 수 없는 토큰(256비트 난수, URL에 그대로 쓸 수 있는 43자). */
  public static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 주문번호(영문·숫자 32자). */
  public static String newOrderId() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /** 입금자명에 붙일 숫자 4자리(1000~9999). */
  public static String newDepositorCode() {
    return String.valueOf(1000 + RANDOM.nextInt(9000));
  }

  /** 토큰 원문 → SHA-256 hex(64자). 형식이 토큰이 아니면 null(DB 조회를 하지 않게). */
  public static String hash(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
      return null;
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
