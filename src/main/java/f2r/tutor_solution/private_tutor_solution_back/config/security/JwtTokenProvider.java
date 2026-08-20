package f2r.tutor_solution.private_tutor_solution_back.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 발급/검증 컴포넌트. HS256 단일 access token만 다룬다 (refresh token 미사용).
 *
 * <p>토큰 구조:
 * <ul>
 *   <li>subject — userId (문자열)
 *   <li>claim {@code loginId} — 사용자 로그인 ID (감사 로그용)
 *   <li>claim {@code role} — STUDENT/ADMIN/TUTOR
 *   <li>iat/exp — 발급/만료 시각
 * </ul>
 */
@Component
public class JwtTokenProvider {

  private static final String CLAIM_LOGIN_ID = "loginId";
  private static final String CLAIM_ROLE = "role";

  private final SecretKey signingKey;
  private final long expirationSeconds;

  public JwtTokenProvider(JwtProperties props) {
    if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "app.jwt.secret은 최소 32 byte 이상이어야 합니다 (HS256 요구). 현재 길이="
              + (props.secret() == null ? 0 : props.secret().getBytes(StandardCharsets.UTF_8).length));
    }
    this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    this.expirationSeconds = props.expirationSeconds();
  }

  /** 사용자 정보를 담아 새 토큰을 발급한다. */
  public String issue(Long userId, String loginId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim(CLAIM_LOGIN_ID, loginId)
        .claim(CLAIM_ROLE, role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirationSeconds)))
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * 토큰을 검증하고 claim을 파싱한다. 서명 위변조/만료/형식 오류는 모두 {@link JwtException}으로 전파된다.
   * 호출 측(필터)이 catch해서 401로 떨어뜨린다.
   */
  public AuthenticatedPrincipal parse(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();

    Long userId = Long.parseLong(claims.getSubject());
    String loginId = claims.get(CLAIM_LOGIN_ID, String.class);
    String role = claims.get(CLAIM_ROLE, String.class);
    return new AuthenticatedPrincipal(userId, loginId, role);
  }

  /** 만료 시각 — 쿠키 Max-Age 동기화용. */
  public long expirationSeconds() {
    return expirationSeconds;
  }

  /** 필터/컨트롤러가 공유하는 인증 주체. SecurityContext의 principal로 들어간다. */
  public record AuthenticatedPrincipal(Long userId, String loginId, String role) {
  }
}
