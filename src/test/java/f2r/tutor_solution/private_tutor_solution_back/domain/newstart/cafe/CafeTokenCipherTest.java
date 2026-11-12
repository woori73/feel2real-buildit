package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLDecoder;
import java.nio.charset.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CafeTokenCipherTest {
  @Test
  void givenEncryptedToken_whenTamperedOrReadByOtherOwner_thenRejected() {
    var properties = new CafeProperties(); properties.setTokenKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
    var cipher = new CafeTokenCipher(properties);
    String encrypted = cipher.encrypt(1L, "synthetic-secret");
    assertEquals("synthetic-secret", cipher.decrypt(1L, encrypted));
    assertNotEquals(encrypted, cipher.encrypt(1L, "synthetic-secret"));
    assertThrows(RuntimeException.class, () -> cipher.decrypt(2L, encrypted));
    assertThrows(RuntimeException.class, () -> cipher.decrypt(1L, encrypted.substring(0, 8) + "AAAA"));
    assertFalse(new CafeTokenCipher(new CafeProperties()).configured());
  }

  @Test
  void givenKoreanAndPercent_whenCafeEncoding_thenMatchesOfficialDoubleEncodingContract() {
    String content = "한글·개인워크아웃 70% + & ?\n두 번째 줄";
    String encoded = NaverCafeClient.cafeEncode(content);
    String decoded = URLDecoder.decode(URLDecoder.decode(encoded, Charset.forName("MS949")), StandardCharsets.UTF_8);
    assertEquals(content, decoded);
    assertTrue(encoded.contains("%25"));
  }
}
