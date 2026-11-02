package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnsTokenCipherTest {

  static SnsTokenCipher cipher(String key) {
    SnsProperties p = new SnsProperties();
    p.setTokenKeyBase64(key);
    return new SnsTokenCipher(p);
  }

  static String key32() {
    return Base64.getEncoder().encodeToString(new byte[32]);
  }

  @Test
  @DisplayName("given 32바이트 키 when 암호화·복호화 then 원문 복원, 저장값에 원문 없음")
  void roundTrip() {
    // given
    SnsTokenCipher c = cipher(key32());
    // when
    String enc = c.encrypt("IGAAtoken-secret");
    // then
    assertThat(enc).startsWith("v1:").doesNotContain("IGAAtoken-secret");
    assertThat(c.decrypt(enc)).isEqualTo("IGAAtoken-secret");
    assertThat(c.encrypt("IGAAtoken-secret")).isNotEqualTo(enc); // nonce마다 다름
  }

  @Test
  @DisplayName("given 키 없음·짧은 키 when isConfigured then false, 암호화 시 설정 안내")
  void missingKey() {
    // given / when / then
    assertThat(cipher(null).isConfigured()).isFalse();
    assertThat(cipher(Base64.getEncoder().encodeToString(new byte[16])).isConfigured()).isFalse();
    assertThatThrownBy(() -> cipher("").encrypt("x")).hasMessageContaining("SNS_TOKEN_KEY_BASE64");
  }

  @Test
  @DisplayName("given 다른 키로 만든 값 when decrypt then 원문·키 없이 재연결 안내")
  void wrongKey() {
    // given
    String enc = cipher(key32()).encrypt("tok");
    byte[] other = new byte[32];
    other[0] = 1;
    // when / then
    assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(other)).decrypt(enc))
        .hasMessageContaining("다시 연결").hasMessageNotContaining("tok");
  }
}
