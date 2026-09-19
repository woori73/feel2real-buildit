package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 네이버 OAuth state(CSRF) 발급/검증 — STATELESS 서버이므로 state를 서명된 쿠키에 담는다.
 *
 * <p>쿠키 payload = {@code nonce|returnUrl|issuedEpochSec} + "." + HMAC-SHA256(payload).
 * 서명키로 {@code app.jwt.secret}을 재사용한다(별도 env 없이 동일 신뢰경계; 쿠키가
 * HttpOnly이므로 브라우저 스크립트는 state를 읽을 수 없고, 공격자는 서명된 값을 위조할 수 없다).
 * 콜백에서는 (1) 서명 검증, (2) query state == cookie nonce 일치, (3) TTL 이내일 때만 통과한다.
 * returnUrl는 서명된 값만 사용하므로 콜백 시점 open-redirect 주입이 원천 차단된다.
 */
@Service
public class NaverOAuthStateService {

  static final String COOKIE_NAME = "NAVER_OAUTH_STATE";
  static final Duration TTL = Duration.ofMinutes(10);
  static final String DEFAULT_RETURN_URL = "/newstart";

  private final byte[] signingKey;
  private final boolean secureCookie;
  private final SecureRandom random = new SecureRandom();

  public NaverOAuthStateService(JwtProperties jwtProperties) {
    this.signingKey = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    this.secureCookie = jwtProperties.secureCookie();
  }

  /** URL-safe 32hex nonce를 발급하고, nonce+서명된 returnUrl을 담은 state 쿠키를 쓴다. nonce를 반환. */
  public String issue(String returnUrl, HttpServletResponse response) {
    byte[] buf = new byte[16];
    random.nextBytes(buf);
    StringBuilder hex = new StringBuilder();
    for (byte b : buf) hex.append(String.format("%02x", b));
    String nonce = hex.toString();
    long issuedAt = java.time.Instant.now().getEpochSecond();
    String payload = nonce + "|" + returnUrl + "|" + issuedAt;
    String cookieValue = b64(payload.getBytes(StandardCharsets.UTF_8)) + "." + b64(sign(payload));
    writeCookie(response, cookieValue, TTL);
    return nonce;
  }

  /** 서명+TTL+nonce 일치를 통과할 때만 발급 시점의 returnUrl을 돌려준다. 실패 시 empty. */
  public Optional<String> validate(HttpServletRequest request, String state) {
    String cookie = readCookie(request);
    if (cookie == null || state == null || state.isBlank()) return Optional.empty();
    int dot = cookie.lastIndexOf('.');
    if (dot <= 0 || dot == cookie.length() - 1) return Optional.empty();
    String payload;
    byte[] sig;
    try {
      payload = new String(Base64.getUrlDecoder().decode(cookie.substring(0, dot)), StandardCharsets.UTF_8);
      sig = Base64.getUrlDecoder().decode(cookie.substring(dot + 1));
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
    if (!MessageDigest.isEqual(sig, sign(payload))) return Optional.empty();
    String[] parts = payload.split("\\|");
    if (parts.length != 3) return Optional.empty();
    long issuedAt;
    try {
      issuedAt = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
    long age = java.time.Instant.now().getEpochSecond() - issuedAt;
    if (age < 0 || age > TTL.getSeconds()) return Optional.empty();
    if (!MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.UTF_8),
        state.getBytes(StandardCharsets.UTF_8))) return Optional.empty();
    return Optional.of(parts[1]);
  }

  public void clear(HttpServletResponse response) {
    writeCookie(response, "", Duration.ZERO);
  }

  /**
   * 서버측 open-redirect 방어 — 프론트 static/js/return_url.js의 safeInternalReturnUrl과
   * 동일 규칙: 내부 경로("/")만 허용, 프로토콜/상대 프로토콜/역슬래시/제어문자 우회 차단.
   */
  public String sanitizeReturnUrl(String raw) {
    if (raw == null || raw.isBlank()) return DEFAULT_RETURN_URL;
    String value = raw.trim();
    for (int i = 0; i < 2; i++) {
      try {
        String decoded = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        if (decoded.equals(value)) break;
        value = decoded;
      } catch (IllegalArgumentException e) {
        return DEFAULT_RETURN_URL;
      }
    }
    value = value.replace('\\', '/');
    if (!value.startsWith("/") || value.startsWith("//")) return DEFAULT_RETURN_URL;
    if (value.contains("|") || value.contains("\n") || value.contains("\r")) return DEFAULT_RETURN_URL;
    if (value.matches("^/[\\x00-\\x1f\\s]*/.*")) return DEFAULT_RETURN_URL;
    if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return DEFAULT_RETURN_URL;
    if (value.regionMatches(true, 1, "%2f", 0, 3) || value.regionMatches(true, 1, "%5c", 0, 3)) {
      return DEFAULT_RETURN_URL;
    }
    return value;
  }

  /** 네이버 인가 URL. 공식 문서의 authorization endpoint를 그대로 쓴다. */
  public String authorizationUrl(NaverOAuthProperties props, String redirectUri, String nonce) {
    return "https://nid.naver.com/oauth2.0/authorize"
        + "?response_type=code"
        + "&client_id=" + URLEncoder.encode(props.clientId(), StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode(nonce, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
  }

  private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
    ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private String readCookie(HttpServletRequest request) {
    var cookies = request.getCookies();
    if (cookies == null) return null;
    for (var c : cookies) {
      if (COOKIE_NAME.equals(c.getName()) && !c.getValue().isEmpty()) return c.getValue();
    }
    return null;
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 초기화 실패", e);
    }
  }

  private String b64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
