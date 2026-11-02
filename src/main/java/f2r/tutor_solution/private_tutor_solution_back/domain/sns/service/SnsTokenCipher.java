package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 인스타그램 토큰 암호화(AES-256-GCM, 12바이트 nonce). 저장 형식: "v1:" + base64(nonce || 암호문).
 * 키(SNS_TOKEN_KEY_BASE64, 32바이트)가 없으면 isConfigured()=false — 계정 연결을 막는다.
 */
@Component
public class SnsTokenCipher {

  private static final String PREFIX = "v1:";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SnsTokenCipher(SnsProperties properties) {
    this.key = parseKey(properties.getTokenKeyBase64());
  }

  static SecretKeySpec parseKey(String base64) {
    if (base64 == null || base64.isBlank()) return null;
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
    return raw.length == 32 ? new SecretKeySpec(raw, "AES") : null;
  }

  public boolean isConfigured() {
    return key != null;
  }

  public String encrypt(String plain) {
    requireKey();
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buf = ByteBuffer.allocate(nonce.length + enc.length).put(nonce).put(enc);
      return PREFIX + Base64.getEncoder().encodeToString(buf.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("토큰 암호화 실패");
    }
  }

  public String decrypt(String stored) {
    requireKey();
    if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalStateException("저장된 토큰 형식이 올바르지 않습니다.");
    try {
      byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
      byte[] plain = c.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      // 키가 바뀌었거나 값이 손상됨 — 원문·키를 메시지에 싣지 않는다.
      throw new IllegalStateException("저장된 토큰을 풀 수 없습니다. 계정을 다시 연결해 주세요.");
    }
  }

  private void requireKey() {
    if (key == null) throw new IllegalStateException("토큰 암호화 키(SNS_TOKEN_KEY_BASE64)가 설정되지 않았습니다.");
  }
}
