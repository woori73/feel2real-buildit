package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 별도 32바이트 키·무작위 nonce·계정별 AAD. 키/토큰은 응답이나 로그로 내보내지 않는다. */
@Component
public class CafeTokenCipher {
  private final SecretKeySpec key;

  public CafeTokenCipher(CafeProperties properties) {
    SecretKeySpec candidate = null;
    try {
      byte[] bytes = Base64.getDecoder().decode(properties.getTokenKeyBase64().trim());
      if (bytes.length == 32) candidate = new SecretKeySpec(bytes, "AES");
    } catch (IllegalArgumentException ignored) {
      // 미설정은 서버 전체 부팅 실패 대신 연결 화면에서 안내한다.
    }
    key = candidate;
  }

  public boolean configured() { return key != null; }

  public String encrypt(long owner, String value) { return transform(owner, value, true); }
  public String decrypt(long owner, String value) { return transform(owner, value, false); }

  private String transform(long owner, String value, boolean encrypt) {
    if (key == null) throw CafeService.error(503, "cafe_token_key_missing");
    try {
      byte[] nonce = new byte[12];
      byte[] bytes;
      if (encrypt) {
        new SecureRandom().nextBytes(nonce);
        bytes = value.getBytes(StandardCharsets.UTF_8);
      } else {
        if (value == null || !value.startsWith("v1:")) throw new IllegalArgumentException();
        byte[] envelope = Base64.getDecoder().decode(value.substring(3));
        if (envelope.length < 28) throw new IllegalArgumentException();
        var buffer = ByteBuffer.wrap(envelope);
        buffer.get(nonce);
        bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
      }
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(("newstart-cafe:" + owner).getBytes(StandardCharsets.UTF_8));
      byte[] result = cipher.doFinal(bytes);
      return encrypt
          ? "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + result.length).put(nonce).put(result).array())
          : new String(result, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw CafeService.error(503, "cafe_token_unavailable_reconnect");
    }
  }
}
