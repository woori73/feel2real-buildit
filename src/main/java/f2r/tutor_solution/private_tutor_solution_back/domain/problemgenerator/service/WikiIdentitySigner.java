package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * BuildIt Wiki 백엔드(Python, 127.0.0.1:8000)가 신뢰하는 X-BuildIt-* 신원 서명을 만든다.
 * 브라우저 프록시(BuildItWikiProxyController)와 서버-서버 호출(Academy AI 등)이 같은 서명
 * 로직을 공유하도록 여기 하나로 뽑아둔다 — 시크릿은 이 컴포넌트에만 있다.
 */
@Component
public class WikiIdentitySigner {
  private final byte[] identitySecret;

  public WikiIdentitySigner(@Value("${app.wiki.identity-secret:${app.jwt.secret}}") String identitySecret) {
    this.identitySecret = identitySecret.getBytes(StandardCharsets.UTF_8);
    if (this.identitySecret.length < 32) {
      throw new IllegalStateException("Wiki identity secret must be at least 32 bytes");
    }
  }

  public void sign(HttpRequest.Builder builder, JwtTokenProvider.AuthenticatedPrincipal principal) {
    try {
      String timestamp = Long.toString(Instant.now().getEpochSecond());
      String payload = principal.userId() + "\n" + principal.loginId() + "\n"
          + principal.role() + "\n" + timestamp;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(identitySecret, "HmacSHA256"));
      String signature = java.util.HexFormat.of().formatHex(
          mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
      builder.header("X-BuildIt-User-Id", principal.userId().toString());
      builder.header("X-BuildIt-Login-Id", principal.loginId());
      builder.header("X-BuildIt-Role", principal.role());
      builder.header("X-BuildIt-Timestamp", timestamp);
      builder.header("X-BuildIt-Signature", signature);
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("Wiki identity 서명 생성 실패", exception);
    }
  }
  /** An additional signature, only attached to validated path-bound MCP GET delegations. */
  public void signReadOnly(HttpRequest.Builder builder, JwtTokenProvider.AuthenticatedPrincipal principal, String target) {
    try {
      String timestamp = Long.toString(Instant.now().getEpochSecond());
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(identitySecret, "HmacSHA256"));
      String payload = "mcp-read-only\n" + principal.userId() + "\nGET\n" + target + "\n" + timestamp;
      builder.header("X-BuildIt-ReadOnly-Time", timestamp);
      builder.header("X-BuildIt-ReadOnly-Signature", java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
    } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
  }

}
