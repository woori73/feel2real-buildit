package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WikiIdentitySigner — BuildItWikiProxyController에서 뽑아낸 X-BuildIt-* HMAC 서명 로직.
 * 서명 값이 Python 쪽 workspace.verify_identity()가 재계산하는 것과 같은 payload
 * (userId\nloginId\nrole\ntimestamp)로 만들어지는지 확인한다.
 */
@DisplayName("WikiIdentitySigner — X-BuildIt-* 신원 서명")
class WikiIdentitySignerTest {
  private static final String SECRET = "wiki-identity-signer-test-secret-32-bytes-min";

  @Test
  @DisplayName("given principal when 서명 then 5개 헤더 + Python과 동일한 payload로 계산된 HMAC-SHA256을 싣는다")
  void signsRequestWithExpectedHeadersAndValidHmac() throws Exception {
    WikiIdentitySigner signer = new WikiIdentitySigner(SECRET);
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(7L, "tutor-login", "TUTOR");

    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8000/x"));
    signer.sign(builder, principal);
    HttpRequest request = builder.build();

    assertThat(request.headers().firstValue("X-BuildIt-User-Id")).contains("7");
    assertThat(request.headers().firstValue("X-BuildIt-Login-Id")).contains("tutor-login");
    assertThat(request.headers().firstValue("X-BuildIt-Role")).contains("TUTOR");
    String timestamp = request.headers().firstValue("X-BuildIt-Timestamp").orElseThrow();
    assertThat(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp))).isLessThan(5);

    String payload = "7\ntutor-login\nTUTOR\n" + timestamp;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String expected = java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    assertThat(request.headers().firstValue("X-BuildIt-Signature")).contains(expected);
  }

  @Test
  @DisplayName("given 32바이트 미만 시크릿 when 생성 then 즉시 거부한다")
  void rejectsSecretShorterThan32Bytes() {
    assertThatThrownBy(() -> new WikiIdentitySigner("short-secret"))
        .isInstanceOf(IllegalStateException.class);
  }
}
