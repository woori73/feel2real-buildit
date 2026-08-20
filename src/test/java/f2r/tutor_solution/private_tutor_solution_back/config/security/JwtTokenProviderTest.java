package f2r.tutor_solution.private_tutor_solution_back.config.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

  private static final String SECRET =
      "test-jwt-secret-must-be-at-least-32-bytes-long-for-hs256";
  private static final long EXP_SECONDS = 3600L;

  private JwtTokenProvider providerWith(long expirationSeconds) {
    return new JwtTokenProvider(
        new JwtProperties(SECRET, expirationSeconds, "ACCESS_TOKEN", false, null));
  }

  @Test
  @DisplayName("issue → parse 라운드트립이 모든 claim을 보존한다")
  void issueAndParseRoundtrip() {
    // given
    JwtTokenProvider provider = providerWith(EXP_SECONDS);

    // when
    String token = provider.issue(42L, "alice", "STUDENT");
    JwtTokenProvider.AuthenticatedPrincipal principal = provider.parse(token);

    // then
    assertThat(principal.userId()).isEqualTo(42L);
    assertThat(principal.loginId()).isEqualTo("alice");
    assertThat(principal.role()).isEqualTo("STUDENT");
  }

  @Test
  @DisplayName("secret이 32byte 미만이면 생성 자체가 실패한다")
  void rejectsShortSecret() {
    // given
    JwtProperties bad = new JwtProperties("too-short", EXP_SECONDS, "ACCESS_TOKEN", false, null);

    // when & then
    assertThatThrownBy(() -> new JwtTokenProvider(bad))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 byte");
  }

  @Test
  @DisplayName("다른 secret으로 서명된 토큰은 검증 실패")
  void rejectsTamperedSignature() {
    // given — 다른 secret로 같은 issuer 흉내내서 토큰 생성
    JwtTokenProvider provider = providerWith(EXP_SECONDS);
    SecretKey otherKey = Keys.hmacShaKeyFor(
        "another-secret-also-at-least-32-bytes-long-xx".getBytes(StandardCharsets.UTF_8));
    String forged = Jwts.builder()
        .subject("99")
        .claim("loginId", "evil")
        .claim("role", "ADMIN")
        .issuedAt(new Date())
        .expiration(Date.from(Instant.now().plusSeconds(3600)))
        .signWith(otherKey, Jwts.SIG.HS256)
        .compact();

    // when & then
    assertThatThrownBy(() -> provider.parse(forged))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("만료된 토큰은 parse 시 JwtException")
  void rejectsExpiredToken() {
    // given — 음수 만료시간으로 즉시 만료된 토큰 발급
    JwtTokenProvider provider = providerWith(-10);

    // when
    String token = provider.issue(1L, "x", "STUDENT");

    // then
    assertThatThrownBy(() -> provider.parse(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("아예 JWT 형식이 아닌 문자열은 IllegalArgumentException 또는 JwtException")
  void rejectsGarbage() {
    // given
    JwtTokenProvider provider = providerWith(EXP_SECONDS);

    // when & then
    assertThatThrownBy(() -> provider.parse("not-a-jwt"))
        .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
  }
}
